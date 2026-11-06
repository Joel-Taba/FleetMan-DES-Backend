package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.DriverException;
import com.yowyob.fleet.domain.model.Driver;
import com.yowyob.fleet.domain.model.DriverAbsence;
import com.yowyob.fleet.domain.ports.out.DriverPersistencePort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.DriverAbsenceR2dbcRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Demandes d'absence du chauffeur (congé, arrêt maladie) : le chauffeur demande, le gestionnaire de
 * sa flotte accepte (l'absence est alors enregistrée avec les contrôles habituels : aucun poste ni
 * trajet prévu sur ces jours) ou refuse avec un motif. Chaque étape est notifiée à l'autre partie.
 * Une suspension reste une décision du gestionnaire, jamais une demande.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DriverAbsenceRequestService {

    public static final Set<String> TYPES = Set.of(DriverAbsence.LEAVE, DriverAbsence.SICK_LEAVE);
    static final int MAX_DAYS = 90;
    /** Un arrêt maladie peut être signalé après coup, dans la limite de quelques jours. */
    static final int SICK_BACKDATE_DAYS = 7;

    private final DatabaseClient db;
    private final DriverPersistencePort drivers;
    private final AccessScopeService accessScope;
    private final DriverAbsenceService absences;
    private final DriverAbsenceR2dbcRepository absenceRepository;
    private final AppNotifier notifier;

    public record CreateCommand(String type, LocalDate startDate, LocalDate endDate, String reason) {}

    /** Demande telle que vue par le chauffeur et le gestionnaire ; {@code conflicts} = postes et trajets prévus sur ces jours. */
    public record AbsenceRequest(UUID id, UUID driverId, String type, LocalDate startDate, LocalDate endDate, String reason,
                                 String status, UUID decidedBy, LocalDateTime decidedAt, String decisionReason,
                                 UUID absenceId, LocalDateTime createdAt, long conflicts) {}

    /** Règles de saisie (null si la demande est recevable). */
    static String validate(CreateCommand cmd, LocalDate today) {
        String type = normalizedType(cmd.type());
        if (!TYPES.contains(type)) return "choisissez un congé ou un arrêt maladie";
        if (cmd.startDate() == null || cmd.endDate() == null) return "indiquez les dates de début et de fin";
        if (cmd.endDate().isBefore(cmd.startDate())) return "la date de fin précède la date de début";
        if (ChronoUnit.DAYS.between(cmd.startDate(), cmd.endDate()) + 1 > MAX_DAYS) {
            return "une demande couvre au plus " + MAX_DAYS + " jours";
        }
        LocalDate earliest = DriverAbsence.SICK_LEAVE.equals(type) ? today.minusDays(SICK_BACKDATE_DAYS) : today;
        if (cmd.startDate().isBefore(earliest)) {
            return DriverAbsence.SICK_LEAVE.equals(type)
                    ? "un arrêt maladie se signale au plus tard " + SICK_BACKDATE_DAYS + " jours après son début"
                    : "un congé se demande pour aujourd'hui ou une date à venir";
        }
        return null;
    }

    static String normalizedType(String type) {
        return type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
    }

    private static final String SELECT = """
            SELECT r.*,
                   CASE WHEN r.status <> 'PENDING' THEN 0 ELSE
                     (SELECT COUNT(*) FROM fleet.assignments a
                      WHERE a.driver_id = r.driver_id AND a.status IN ('PENDING', 'IN_PROGRESS')
                        AND a.start_datetime < (r.end_date + 1) AND a.end_datetime > r.start_date)
                   + (SELECT COUNT(*) FROM fleet.trips t
                      WHERE t.driver_id = r.driver_id AND t.deleted_at IS NULL
                        AND t.status IN ('SCHEDULED', 'DEPARTED', 'ARRIVED')
                        AND t.start_date BETWEEN r.start_date AND r.end_date)
                   END AS conflicts
            FROM fleet.driver_absence_requests r
            """;

    private Flux<AbsenceRequest> query(String where, UUID id) {
        return db.sql(SELECT + where).bind("id", id)
                .map((row, meta) -> new AbsenceRequest(row.get("id", UUID.class), row.get("driver_id", UUID.class),
                        row.get("type", String.class), row.get("start_date", LocalDate.class), row.get("end_date", LocalDate.class),
                        row.get("reason", String.class), row.get("status", String.class), row.get("decided_by", UUID.class),
                        row.get("decided_at", LocalDateTime.class), row.get("decision_reason", String.class),
                        row.get("absence_id", UUID.class), row.get("created_at", LocalDateTime.class),
                        row.get("conflicts", Long.class) == null ? 0 : row.get("conflicts", Long.class)))
                .all();
    }

    // ── Lecture ──────────────────────────────────────────────────────────────

    public Flux<AbsenceRequest> list(UUID driverId, UUID actorId) {
        return selfOrManager(driverId, actorId)
                .thenMany(query("WHERE r.driver_id = :id ORDER BY r.created_at DESC", driverId));
    }

    private Mono<AbsenceRequest> find(UUID driverId, UUID requestId) {
        return query("WHERE r.id = :id", requestId).next()
                .filter(r -> r.driverId().equals(driverId))
                .switchIfEmpty(Mono.error(DriverException.invalidAbsence("demande introuvable")));
    }

    // ── Chauffeur ────────────────────────────────────────────────────────────

    @Transactional
    public Mono<AbsenceRequest> create(UUID driverId, UUID actorId, CreateCommand cmd) {
        String invalid = validate(cmd, LocalDate.now());
        if (invalid != null) return Mono.error(DriverException.invalidAbsence(invalid));
        String type = normalizedType(cmd.type());
        String reason = cmd.reason() == null || cmd.reason().isBlank() ? null : cmd.reason().trim();
        UUID id = UUID.randomUUID();
        return requireSelf(driverId, actorId)
                .then(drivers.findById(driverId))
                .switchIfEmpty(Mono.error(DriverException.notFound()))
                .flatMap((Driver d) -> "LEFT".equals(d.status()) ? Mono.<Driver>error(DriverException.left())
                        : d.fleetId() == null ? Mono.<Driver>error(DriverException.notInFleet()) : Mono.just(d))
                .then(absenceRepository.findOverlapping(driverId, cmd.startDate(), cmd.endDate()).hasElements())
                .flatMap(busy -> Boolean.TRUE.equals(busy) ? Mono.<Void>error(DriverException.absenceOverlap()) : Mono.<Void>empty())
                .then(db.sql("""
                                SELECT COUNT(*) AS n FROM fleet.driver_absence_requests
                                WHERE driver_id = :d AND status = 'PENDING' AND start_date <= :e AND end_date >= :s
                                """)
                        .bind("d", driverId).bind("s", cmd.startDate()).bind("e", cmd.endDate())
                        .map((row, meta) -> row.get("n", Long.class)).one())
                .flatMap(n -> n > 0
                        ? Mono.<Void>error(DriverException.invalidAbsence("une demande en attente couvre déjà ces dates"))
                        : Mono.<Void>empty())
                .then(Mono.defer(() -> {
                    var spec = db.sql("""
                                    INSERT INTO fleet.driver_absence_requests (id, driver_id, type, start_date, end_date, reason, status, created_at, tenant_id)
                                    SELECT :id, :d, :t, :s, :e, :r, 'PENDING', :now, u.tenant_id FROM fleet.users u WHERE u.id = :d
                                    """)
                            .bind("id", id).bind("d", driverId).bind("t", type).bind("s", cmd.startDate())
                            .bind("e", cmd.endDate()).bind("now", LocalDateTime.now());
                    spec = reason != null ? spec.bind("r", reason) : spec.bindNull("r", String.class);
                    return spec.fetch().rowsUpdated();
                }))
                .then(Mono.defer(() -> find(driverId, id)))
                .flatMap(r -> driverName(driverId).flatMap(name -> notifier.toDriverManager(driverId, driverId,
                        "Demande d'absence",
                        name + " demande un " + DriverAbsenceService.typeLabel(r.type()) + " "
                                + DriverAbsenceService.period(r.startDate(), r.endDate())
                                + (r.reason() != null ? " : " + r.reason() : "") + "."
                                + (r.conflicts() > 0 ? " " + r.conflicts() + " poste(s) ou trajet(s) sont prévus sur ces jours." : ""),
                        r.conflicts() > 0 ? AppNotifier.Level.WARNING : AppNotifier.Level.INFO, "ABSENCE_REQUESTED",
                        AppNotifier.refs("driverId", driverId, "absenceRequestId", r.id()))).thenReturn(r))
                .doOnSuccess(r -> log.info("📨 Demande d'absence {} du chauffeur {}", r.id(), driverId));
    }

    public Mono<AbsenceRequest> withdraw(UUID driverId, UUID requestId, UUID actorId) {
        return requireSelf(driverId, actorId)
                .then(find(driverId, requestId))
                .flatMap(r -> !"PENDING".equals(r.status())
                        ? Mono.<AbsenceRequest>error(DriverException.invalidAbsence("cette demande a déjà été traitée"))
                        : decide(requestId, "WITHDRAWN", null, null, null).then(find(driverId, requestId)))
                .flatMap(r -> driverName(driverId).flatMap(name -> notifier.toDriverManager(driverId, driverId,
                        "Demande d'absence retirée",
                        name + " a retiré sa demande de " + DriverAbsenceService.typeLabel(r.type()) + " "
                                + DriverAbsenceService.period(r.startDate(), r.endDate()) + ".",
                        AppNotifier.Level.INFO, "ABSENCE_REQUEST_WITHDRAWN",
                        AppNotifier.refs("driverId", driverId, "absenceRequestId", r.id()))).thenReturn(r));
    }

    // ── Gestionnaire ─────────────────────────────────────────────────────────

    /** Accepter : l'absence est enregistrée (refusée si des postes ou trajets restent prévus sur ces jours). */
    @Transactional
    public Mono<AbsenceRequest> approve(UUID driverId, UUID requestId, UUID actorId) {
        return requireManager(driverId, actorId)
                .then(find(driverId, requestId))
                .flatMap(r -> !"PENDING".equals(r.status())
                        ? Mono.<AbsenceRequest>error(DriverException.invalidAbsence("cette demande a déjà été traitée"))
                        : absences.declare(driverId, new DriverAbsenceService.DeclareAbsenceCommand(
                                        r.type(), r.startDate(), r.endDate(), r.reason()), actorId, false)
                                .flatMap(absence -> local(actorId)
                                        .flatMap(manager -> decide(requestId, "APPROVED", manager, null, absence.id()))
                                        .then(find(driverId, requestId))))
                .flatMap(r -> local(actorId).flatMap(manager -> notifier.toUser(driverId, manager, "Demande d'absence acceptée",
                        "Votre demande de " + DriverAbsenceService.typeLabel(r.type()) + " "
                                + DriverAbsenceService.period(r.startDate(), r.endDate()) + " est acceptée.",
                        AppNotifier.Level.SUCCESS, "ABSENCE_REQUEST_APPROVED",
                        AppNotifier.refs("absenceId", r.absenceId(), "driverId", driverId, "absenceRequestId", r.id()))).thenReturn(r));
    }

    public Mono<AbsenceRequest> reject(UUID driverId, UUID requestId, UUID actorId, String reason) {
        String why = reason == null || reason.isBlank() ? null : reason.trim();
        if (why == null) return Mono.error(DriverException.invalidAbsence("indiquez le motif du refus"));
        return requireManager(driverId, actorId)
                .then(find(driverId, requestId))
                .flatMap(r -> !"PENDING".equals(r.status())
                        ? Mono.<AbsenceRequest>error(DriverException.invalidAbsence("cette demande a déjà été traitée"))
                        : local(actorId).flatMap(manager -> decide(requestId, "REJECTED", manager, why, null))
                                .then(find(driverId, requestId)))
                .flatMap(r -> local(actorId).flatMap(manager -> notifier.toUser(driverId, manager, "Demande d'absence refusée",
                        "Votre demande de " + DriverAbsenceService.typeLabel(r.type()) + " "
                                + DriverAbsenceService.period(r.startDate(), r.endDate()) + " est refusée : " + why,
                        AppNotifier.Level.WARNING, "ABSENCE_REQUEST_REJECTED",
                        AppNotifier.refs("driverId", driverId, "absenceRequestId", r.id()))).thenReturn(r));
    }

    // ── Outils ───────────────────────────────────────────────────────────────

    private Mono<Void> decide(UUID requestId, String status, UUID decidedBy, String reason, UUID absenceId) {
        var spec = db.sql("""
                        UPDATE fleet.driver_absence_requests
                        SET status = :st, decided_at = :now, decided_by = :by, decision_reason = :why, absence_id = :abs
                        WHERE id = :id AND status = 'PENDING'
                        """)
                .bind("st", status).bind("now", LocalDateTime.now()).bind("id", requestId);
        spec = decidedBy != null ? spec.bind("by", decidedBy) : spec.bindNull("by", UUID.class);
        spec = reason != null ? spec.bind("why", reason) : spec.bindNull("why", String.class);
        spec = absenceId != null ? spec.bind("abs", absenceId) : spec.bindNull("abs", UUID.class);
        return spec.fetch().rowsUpdated()
                .flatMap(n -> n == 0
                        ? Mono.<Void>error(DriverException.invalidAbsence("cette demande a déjà été traitée"))
                        : Mono.<Void>empty());
    }

    private Mono<UUID> local(UUID actorId) {
        return accessScope.resolveLocalUserId(actorId).defaultIfEmpty(new UUID(0, 0));
    }

    private Mono<Void> requireSelf(UUID driverId, UUID actorId) {
        return local(actorId).flatMap(localId -> localId.equals(driverId)
                ? Mono.<Void>empty()
                : Mono.<Void>error(new AccessDeniedException("Seul le chauffeur concerné peut demander ou retirer une absence.")));
    }

    private Mono<Void> requireManager(UUID driverId, UUID actorId) {
        return drivers.findById(driverId)
                .switchIfEmpty(Mono.error(DriverException.notFound()))
                .flatMap(d -> d.fleetId() == null ? Mono.<Void>error(DriverException.notInFleet())
                        : accessScope.assertFleetAccess(actorId, d.fleetId()));
    }

    private Mono<Void> selfOrManager(UUID driverId, UUID actorId) {
        return local(actorId).flatMap(localId -> localId.equals(driverId) ? Mono.<Void>empty() : requireManager(driverId, actorId));
    }

    private Mono<String> driverName(UUID driverId) {
        return db.sql("SELECT TRIM(COALESCE(first_name, '') || ' ' || COALESCE(last_name, '')) AS n FROM fleet.users WHERE id = :id")
                .bind("id", driverId)
                .map((row, meta) -> row.get("n", String.class))
                .one()
                .filter(n -> !n.isBlank())
                .defaultIfEmpty("Un chauffeur");
    }
}
