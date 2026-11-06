package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.DriverException;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Messagerie entre le gestionnaire d'une flotte et ses chauffeurs : consignes, informations, questions.
 * Une conversation par chauffeur ; un message peut aussi être adressé à tous les chauffeurs actifs d'une flotte.
 * Chaque message est notifié au destinataire (MESSAGE_RECEIVED, clés messageId et driverId).
 */
@Service
@RequiredArgsConstructor
public class DriverMessageService {

    public static final int MAX_LENGTH = 2000;

    public record Message(UUID id, UUID driverId, UUID senderId, String senderName, boolean fromDriver, String body,
                          boolean broadcast, LocalDateTime readAt, LocalDateTime createdAt) {}

    public record Conversation(UUID driverId, String driverName, UUID fleetId, String fleetName, String lastMessage,
                               boolean lastFromDriver, LocalDateTime lastAt, long unread) {}

    private final DatabaseClient db;
    private final AccessScopeService accessScope;
    private final ResourceAccessService resourceAccess;
    private final AppNotifier notifier;

    static String clean(String body) {
        String text = body == null ? "" : body.trim();
        if (text.isEmpty()) throw new IllegalArgumentException("Le message est vide.");
        if (text.length() > MAX_LENGTH) throw new IllegalArgumentException("Le message dépasse " + MAX_LENGTH + " caractères.");
        return text;
    }

    static String preview(String body) {
        return body.length() > 140 ? body.substring(0, 137) + "..." : body;
    }

    // ── Gestionnaire ─────────────────────────────────────────────────────────

    /** Conversations des chauffeurs des flottes gérées, la plus récente d'abord. */
    public Flux<Conversation> conversations(UUID actorId) {
        return accessScope.resolveLocalUserId(actorId).flatMapMany(localId -> db.sql("""
                        SELECT d.user_id, TRIM(COALESCE(u.first_name, '') || ' ' || COALESCE(u.last_name, '')) AS driver_name,
                               f.id AS fleet_id, f.name AS fleet_name,
                               last.body AS last_body, last.from_driver AS last_from_driver, last.created_at AS last_at,
                               (SELECT COUNT(*) FROM fleet.driver_messages m
                                 WHERE m.thread_driver_id = d.user_id AND m.from_driver AND m.read_at IS NULL) AS unread
                        FROM fleet.drivers d
                        JOIN fleet.fleets f ON f.id = d.fleet_id
                        JOIN fleet.users u ON u.id = d.user_id
                        LEFT JOIN LATERAL (SELECT body, from_driver, created_at FROM fleet.driver_messages m
                                           WHERE m.thread_driver_id = d.user_id ORDER BY created_at DESC LIMIT 1) last ON TRUE
                        WHERE f.manager_id = :me AND COALESCE(d.status, 'ACTIVE') <> 'LEFT'
                        ORDER BY last.created_at DESC NULLS LAST, driver_name
                        """)
                .bind("me", localId)
                .map((row, meta) -> new Conversation(
                        row.get("user_id", UUID.class), row.get("driver_name", String.class),
                        row.get("fleet_id", UUID.class), row.get("fleet_name", String.class),
                        row.get("last_body", String.class), Boolean.TRUE.equals(row.get("last_from_driver", Boolean.class)),
                        row.get("last_at", LocalDateTime.class), row.get("unread", Long.class)))
                .all());
    }

    /** Fil d'un chauffeur, lu par son gestionnaire ou par le chauffeur lui-même (marque comme lus les messages reçus). */
    @Transactional
    public Flux<Message> thread(UUID actorId, UUID driverId) {
        return resourceAccess.assertDriverAccess(actorId, driverId)
                .then(accessScope.resolveLocalUserId(actorId))
                .flatMapMany(localId -> {
                    boolean viewerIsDriver = localId.equals(driverId);
                    return db.sql("""
                                    UPDATE fleet.driver_messages SET read_at = :now
                                    WHERE thread_driver_id = :driver AND from_driver = :fromOther AND read_at IS NULL
                                    """)
                            .bind("now", LocalDateTime.now())
                            .bind("driver", driverId)
                            .bind("fromOther", !viewerIsDriver)
                            .fetch().rowsUpdated()
                            // Les notifications « message reçu » de ce fil deviennent lues en même temps.
                            .then(db.sql("""
                                            UPDATE fleet.notifications SET is_read = TRUE
                                            WHERE user_id = :viewer AND is_read = FALSE
                                              AND data LIKE '%"MESSAGE_RECEIVED"%' AND data LIKE :driverKey
                                            """)
                                    .bind("viewer", localId)
                                    .bind("driverKey", "%" + driverId + "%")
                                    .fetch().rowsUpdated())
                            .thenMany(list(driverId));
                });
    }

    @Transactional
    public Mono<Message> sendToDriver(UUID actorId, UUID driverId, String body) {
        String text = clean(body);
        return resourceAccess.assertDriverAccess(actorId, driverId)
                .then(accessScope.resolveLocalUserId(actorId))
                .flatMap(localId -> {
                    if (localId.equals(driverId)) {
                        return Mono.<Message>error(new AccessDeniedException("Utilisez votre messagerie de chauffeur."));
                    }
                    return fleetOf(driverId).flatMap(fleetId -> insert(fleetId, driverId, localId, false, text, null))
                            .flatMap(m -> notifyDriver(m, localId).thenReturn(m));
                });
    }

    /** Message à tous les chauffeurs actifs de la flotte ; renvoie le nombre de destinataires. */
    @Transactional
    public Mono<Long> broadcast(UUID actorId, UUID fleetId, String body) {
        String text = clean(body);
        UUID broadcastId = UUID.randomUUID();
        return accessScope.assertFleetAccess(actorId, fleetId)
                .then(accessScope.resolveLocalUserId(actorId))
                .flatMap(localId -> db.sql("""
                                SELECT user_id FROM fleet.drivers WHERE fleet_id = :fleet AND COALESCE(status, 'ACTIVE') <> 'LEFT'
                                """)
                        .bind("fleet", fleetId)
                        .map((row, meta) -> row.get("user_id", UUID.class))
                        .all()
                        .concatMap(driverId -> insert(fleetId, driverId, localId, false, text, broadcastId)
                                .flatMap(m -> notifyDriver(m, localId).thenReturn(m)))
                        .count());
    }

    // ── Chauffeur ────────────────────────────────────────────────────────────

    /** Fil du chauffeur connecté. */
    public Flux<Message> threadOfSelf(UUID actorId) {
        return accessScope.resolveLocalUserId(actorId).flatMapMany(localId -> thread(actorId, localId));
    }

    @Transactional
    public Mono<Message> sendFromDriver(UUID actorId, String body) {
        String text = clean(body);
        return accessScope.resolveLocalUserId(actorId).flatMap(localId -> fleetOf(localId)
                .flatMap(fleetId -> insert(fleetId, localId, localId, true, text, null)
                        .flatMap(m -> notifier.toFleetManager(fleetId, localId, "Message de " + nameOf(m),
                                        preview(text), AppNotifier.Level.INFO, "MESSAGE_RECEIVED",
                                        AppNotifier.refs("messageId", m.id(), "driverId", localId))
                                .thenReturn(m))));
    }

    public Mono<Long> unreadForDriver(UUID actorId) {
        return accessScope.resolveLocalUserId(actorId).flatMap(localId -> db.sql("""
                        SELECT COUNT(*) AS n FROM fleet.driver_messages
                        WHERE thread_driver_id = :driver AND NOT from_driver AND read_at IS NULL
                        """)
                .bind("driver", localId)
                .map((row, meta) -> row.get("n", Long.class))
                .one());
    }

    // ── Commun ───────────────────────────────────────────────────────────────

    private Mono<UUID> fleetOf(UUID driverId) {
        return db.sql("SELECT fleet_id FROM fleet.drivers WHERE user_id = :driver")
                .bind("driver", driverId)
                .map((row, meta) -> java.util.Optional.ofNullable(row.get("fleet_id", UUID.class)))
                .one()
                .switchIfEmpty(Mono.error(DriverException.notFound()))
                .flatMap(f -> f.map(Mono::just).orElseGet(() -> Mono.error(DriverException.notInFleet())));
    }

    private Mono<Message> insert(UUID fleetId, UUID driverId, UUID senderId, boolean fromDriver, String text, UUID broadcastId) {
        UUID id = UUID.randomUUID();
        var spec = db.sql("""
                        INSERT INTO fleet.driver_messages (id, fleet_id, thread_driver_id, sender_id, from_driver, body, broadcast_id,
                                                           created_at, tenant_id)
                        SELECT :id, :fleet, :driver, :sender, :fromDriver, :body, :broadcast, :now, f.tenant_id
                        FROM fleet.fleets f WHERE f.id = :fleet
                        """)
                .bind("id", id).bind("fleet", fleetId).bind("driver", driverId).bind("sender", senderId)
                .bind("fromDriver", fromDriver).bind("body", text).bind("now", LocalDateTime.now());
        spec = broadcastId != null ? spec.bind("broadcast", broadcastId) : spec.bindNull("broadcast", UUID.class);
        return spec.fetch().rowsUpdated().then(list(driverId).filter(m -> m.id().equals(id)).next());
    }

    private Flux<Message> list(UUID driverId) {
        return db.sql("""
                        SELECT m.id, m.thread_driver_id, m.sender_id, m.from_driver, m.body, m.broadcast_id, m.read_at, m.created_at,
                               TRIM(COALESCE(u.first_name, '') || ' ' || COALESCE(u.last_name, '')) AS sender_name
                        FROM fleet.driver_messages m LEFT JOIN fleet.users u ON u.id = m.sender_id
                        WHERE m.thread_driver_id = :driver
                        ORDER BY m.created_at
                        """)
                .bind("driver", driverId)
                .map((row, meta) -> new Message(
                        row.get("id", UUID.class), row.get("thread_driver_id", UUID.class), row.get("sender_id", UUID.class),
                        row.get("sender_name", String.class), Boolean.TRUE.equals(row.get("from_driver", Boolean.class)),
                        row.get("body", String.class), row.get("broadcast_id", UUID.class) != null,
                        row.get("read_at", LocalDateTime.class), row.get("created_at", LocalDateTime.class)))
                .all();
    }

    private Mono<Void> notifyDriver(Message m, UUID actorId) {
        String title = m.broadcast() ? "Message à toute la flotte" : "Message de votre gestionnaire";
        return notifier.toUser(m.driverId(), actorId, title, preview(m.body()), AppNotifier.Level.INFO, "MESSAGE_RECEIVED",
                AppNotifier.refs("messageId", m.id(), "driverId", m.driverId()));
    }

    private static String nameOf(Message m) {
        return m.senderName() == null || m.senderName().isBlank() ? "votre chauffeur" : m.senderName();
    }

}
