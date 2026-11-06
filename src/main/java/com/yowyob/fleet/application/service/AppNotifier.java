package com.yowyob.fleet.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Notifications in-app entre rôles (chauffeur ↔ gestionnaire ↔ administrateur).
 *
 * Format du champ {@code data} (lu par les centres de notifications web et mobile pour ouvrir le bon écran) :
 * {@code {"type":"<EVENEMENT>","<entite>Id":"<uuid>", ...}}, par ex. {@code {"type":"INCIDENT_DECLARED","incidentId":"…"}}.
 * Clés d'identifiant reconnues : incidentId, tripId, stopId, submissionId, assignmentId, scheduleId, documentId,
 * fuelRechargeId, maintenanceId, expenseId, vehicleId, driverId, absenceId.
 *
 * Une notification n'échoue jamais l'action métier qui la déclenche : les erreurs sont journalisées puis ignorées.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppNotifier {

    public enum Level { INFO, SUCCESS, WARNING }

    private final DatabaseClient db;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Références d'entités à joindre ({@code refs("tripId", id, "stopId", stopId)}), valeurs nulles ignorées. */
    public static Map<String, Object> refs(Object... keyValues) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            if (keyValues[i + 1] != null) out.put(String.valueOf(keyValues[i]), String.valueOf(keyValues[i + 1]));
        }
        return out;
    }

    /** Notifie un utilisateur local (sauf s'il est l'auteur de l'action, {@code actorId}). */
    public Mono<Void> toUser(UUID userId, UUID actorId, String title, String message, Level level,
                             String event, Map<String, Object> refs) {
        if (userId == null || userId.equals(actorId)) return Mono.empty();
        return db.sql("""
                        INSERT INTO fleet.notifications (id, user_id, title, message, type, is_read, created_at, data, tenant_id)
                        SELECT gen_random_uuid(), u.id, :title, :message, :level, FALSE, :now, :data, u.tenant_id
                        FROM fleet.users u WHERE u.id = :user
                        """)
                .bind("title", title)
                .bind("message", message)
                .bind("level", level.name())
                .bind("now", LocalDateTime.now())
                .bind("data", data(event, refs))
                .bind("user", userId)
                .fetch().rowsUpdated()
                .then()
                .onErrorResume(e -> {
                    log.warn("⚠️ [NOTIF] {} non envoyée à {} : {}", event, userId, e.getMessage());
                    return Mono.empty();
                });
    }

    /** Notifie plusieurs utilisateurs (doublons et auteur exclus). */
    public Mono<Void> toUsers(Collection<UUID> userIds, UUID actorId, String title, String message, Level level,
                              String event, Map<String, Object> refs) {
        return Flux.fromIterable(userIds)
                .filter(Objects::nonNull)
                .distinct()
                .concatMap(u -> toUser(u, actorId, title, message, level, event, refs))
                .then();
    }

    /** Notifie le gestionnaire de la flotte (fleet.fleets.manager_id). */
    public Mono<Void> toFleetManager(UUID fleetId, UUID actorId, String title, String message, Level level,
                                     String event, Map<String, Object> refs) {
        if (fleetId == null) return Mono.empty();
        return db.sql("SELECT manager_id FROM fleet.fleets WHERE id = :fleet AND manager_id IS NOT NULL")
                .bind("fleet", fleetId)
                .map((row, meta) -> row.get("manager_id", UUID.class))
                .one()
                .flatMap(managerId -> toUser(managerId, actorId, title, message, level, event, refs))
                .onErrorResume(e -> {
                    log.warn("⚠️ [NOTIF] gestionnaire de la flotte {} introuvable : {}", fleetId, e.getMessage());
                    return Mono.empty();
                });
    }

    /** Notifie le gestionnaire de la flotte du chauffeur (fleet.drivers.fleet_id). */
    public Mono<Void> toDriverManager(UUID driverId, UUID actorId, String title, String message, Level level,
                                      String event, Map<String, Object> refs) {
        if (driverId == null) return Mono.empty();
        return db.sql("SELECT fleet_id FROM fleet.drivers WHERE user_id = :driver AND fleet_id IS NOT NULL")
                .bind("driver", driverId)
                .map((row, meta) -> row.get("fleet_id", UUID.class))
                .one()
                .flatMap(fleetId -> toFleetManager(fleetId, actorId, title, message, level, event, refs));
    }

    String data(String event, Map<String, Object> refs) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", event);
        if (refs != null) payload.putAll(refs);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return "{\"type\":\"" + event + "\"}";
        }
    }
}
