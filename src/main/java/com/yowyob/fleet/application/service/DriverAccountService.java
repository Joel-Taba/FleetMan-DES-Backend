package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.DriverException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Blocage du compte d'un chauffeur par le gestionnaire de sa flotte, sans suppression :
 * la connexion est refusée (y compris les sessions ouvertes, contrôlées à chaque requête),
 * le chauffeur n'est plus engageable et tout son historique est conservé. Réversible.
 * Refusé tant qu'il a un trajet ou un poste en cours ou à venir (à terminer, annuler ou réaffecter).
 */
@Service
@RequiredArgsConstructor
public class DriverAccountService {

    public record AccountState(UUID driverId, boolean blocked, String reason, LocalDateTime blockedAt, String blockedByName) {}

    private final DatabaseClient db;
    private final AccessScopeService accessScope;
    private final ResourceAccessService resourceAccess;
    private final ResourceAvailabilityService availability;
    private final AppNotifier notifier;

    public Mono<AccountState> state(UUID actorId, UUID driverId) {
        return resourceAccess.assertDriverAccess(actorId, driverId).then(load(driverId));
    }

    @Transactional
    public Mono<AccountState> block(UUID actorId, UUID driverId, String reason) {
        String why = reason == null ? "" : reason.trim();
        if (why.isEmpty()) {
            return Mono.error(new DriverException("Indiquez le motif du blocage.", HttpStatus.BAD_REQUEST, "DRV_020"));
        }
        if (why.length() > 500) why = why.substring(0, 500);
        String motive = why;
        return resourceAccess.assertDriverAccess(actorId, driverId)
                .then(accessScope.resolveLocalUserId(actorId))
                .flatMap(me -> {
                    if (me.equals(driverId)) {
                        return Mono.<AccountState>error(new DriverException("Vous ne pouvez pas bloquer votre propre compte.",
                                HttpStatus.FORBIDDEN, "DRV_021"));
                    }
                    return load(driverId).flatMap(current -> current.blocked()
                            ? Mono.<AccountState>error(new DriverException("Ce compte est déjà bloqué.", HttpStatus.CONFLICT, "DRV_022"))
                            : availability.driverHasOpenCommitments(driverId).flatMap(open -> Boolean.TRUE.equals(open)
                                    ? Mono.<AccountState>error(new DriverException(
                                            "Ce chauffeur a un trajet ou un poste en cours ou à venir : terminez-le, annulez-le ou réaffectez-le avant de bloquer son compte.",
                                            HttpStatus.CONFLICT, "DRV_023"))
                                    : db.sql("""
                                                    UPDATE fleet.drivers SET account_blocked_at = :now, account_blocked_reason = :reason,
                                                           account_blocked_by = :me, updated_at = :now
                                                    WHERE user_id = :id
                                                    """)
                                            .bind("now", LocalDateTime.now()).bind("reason", motive).bind("me", me).bind("id", driverId)
                                            .fetch().rowsUpdated()
                                            .then(db.sql("UPDATE fleet.users SET is_active = FALSE WHERE id = :id")
                                                    .bind("id", driverId).fetch().rowsUpdated())
                                            .then(load(driverId))));
                });
    }

    @Transactional
    public Mono<AccountState> unblock(UUID actorId, UUID driverId) {
        return resourceAccess.assertDriverAccess(actorId, driverId)
                .then(accessScope.resolveLocalUserId(actorId))
                .flatMap(me -> load(driverId).flatMap(current -> !current.blocked()
                        ? Mono.<AccountState>error(new DriverException("Ce compte n'est pas bloqué.", HttpStatus.CONFLICT, "DRV_024"))
                        : db.sql("""
                                        UPDATE fleet.drivers SET account_blocked_at = NULL, account_blocked_reason = NULL,
                                               account_blocked_by = NULL, updated_at = :now
                                        WHERE user_id = :id
                                        """)
                                .bind("now", LocalDateTime.now()).bind("id", driverId)
                                .fetch().rowsUpdated()
                                .then(db.sql("UPDATE fleet.users SET is_active = TRUE WHERE id = :id")
                                        .bind("id", driverId).fetch().rowsUpdated())
                                .then(notifier.toUser(driverId, me, "Compte rétabli",
                                        "Votre gestionnaire a rétabli l'accès à votre compte.",
                                        AppNotifier.Level.SUCCESS, "DRIVER_ACCOUNT_UNBLOCKED",
                                        AppNotifier.refs("driverId", driverId)))
                                .then(load(driverId))));
    }

    private Mono<AccountState> load(UUID driverId) {
        return db.sql("""
                        SELECT d.account_blocked_at, d.account_blocked_reason,
                               TRIM(COALESCE(b.first_name, '') || ' ' || COALESCE(b.last_name, '')) AS blocked_by
                        FROM fleet.drivers d LEFT JOIN fleet.users b ON b.id = d.account_blocked_by
                        WHERE d.user_id = :id
                        """)
                .bind("id", driverId)
                .map((row, meta) -> {
                    LocalDateTime at = row.get("account_blocked_at", LocalDateTime.class);
                    return new AccountState(driverId, at != null, row.get("account_blocked_reason", String.class), at,
                            at != null ? row.get("blocked_by", String.class) : null);
                })
                .one()
                .switchIfEmpty(Mono.error(DriverException.notFound()));
    }
}
