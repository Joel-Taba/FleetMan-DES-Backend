package com.yowyob.fleet.infrastructure.adapters.inbound.messaging;

import com.yowyob.fleet.application.service.AppNotifier;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Suivi des postes entre gestionnaire et chauffeur (toutes les 10 minutes et au démarrage) :
 *   * un poste modifié (ou ajouté) après publication et toujours sans accusé de réception à moins de
 *     {@value #UNACK_WINDOW_HOURS} h de son début : le gestionnaire de la flotte est alerté, une fois
 *     par changement (SHIFT_UNACKNOWLEDGED) ;
 *   * un poste passé « absent » (NO_SHOW) par la clôture automatique : le chauffeur est prévenu une
 *     fois (SHIFT_NO_SHOW). Un NO_SHOW saisi par le gestionnaire est notifié tout de suite et marqué.
 */
@Component
@RequiredArgsConstructor
public class WorkforceAlertJob {

    private static final Logger log = LoggerFactory.getLogger(WorkforceAlertJob.class);
    static final int UNACK_WINDOW_HOURS = 4;
    /** Un NO_SHOW plus ancien n'est plus notifié (pas de rattrapage massif après un arrêt du serveur). */
    static final int NO_SHOW_LOOKBACK_HOURS = 12;
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM 'à' HH:mm");
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");

    private final DatabaseClient db;
    private final AppNotifier notifier;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        run();
    }

    @Scheduled(fixedDelay = 600_000, initialDelay = 600_000)
    public void run() {
        LocalDateTime now = LocalDateTime.now();
        alertUnacknowledged(now)
                .then(notifyAutomaticNoShows(now))
                .onErrorResume(e -> {
                    log.warn("Suivi des postes ignoré : {}", e.getMessage());
                    return Mono.empty();
                })
                .subscribe();
    }

    record ShiftRow(UUID id, UUID scheduleId, UUID fleetId, UUID driverId, LocalDateTime start, LocalDateTime end,
                    String driverName, String plate) {
        String when() {
            return "le " + start.format(WHEN) + " → " + end.format(HOUR);
        }
    }

    Mono<Void> alertUnacknowledged(LocalDateTime now) {
        return db.sql("""
                WITH due AS (
                    UPDATE fleet.assignments a SET unack_alerted_at = :now
                    WHERE a.status = 'PENDING' AND a.acknowledged_at IS NULL AND a.changed_at IS NOT NULL
                      AND a.unack_alerted_at IS NULL
                      AND a.start_datetime > :now AND a.start_datetime <= :limit
                      AND (a.schedule_id IS NULL OR EXISTS (SELECT 1 FROM fleet.schedules s
                                                            WHERE s.id = a.schedule_id AND s.status = 'PUBLISHED'))
                    RETURNING a.id, a.schedule_id, a.fleet_id, a.driver_id, a.vehicle_id, a.start_datetime, a.end_datetime
                )
                SELECT due.*, TRIM(COALESCE(u.first_name, '') || ' ' || COALESCE(u.last_name, '')) AS driver_name,
                       v.license_plate
                FROM due LEFT JOIN fleet.users u ON u.id = due.driver_id
                LEFT JOIN fleet.vehicles v ON v.id = due.vehicle_id
                """)
                .bind("now", now)
                .bind("limit", now.plusHours(UNACK_WINDOW_HOURS))
                .map((row, meta) -> row(row))
                .all()
                .concatMap(r -> notifier.toFleetManager(r.fleetId(), null, "Poste non confirmé",
                        (r.driverName().isBlank() ? "Le chauffeur" : r.driverName()) + " n'a pas accusé réception du changement de son poste "
                                + r.when() + (r.plate() != null ? " (" + r.plate() + ")" : "")
                                + ". Le poste commence dans moins de " + UNACK_WINDOW_HOURS + " h : contactez-le.",
                        AppNotifier.Level.WARNING, "SHIFT_UNACKNOWLEDGED",
                        AppNotifier.refs("assignmentId", r.id(), "scheduleId", r.scheduleId(), "driverId", r.driverId()))
                        .thenReturn(1))
                .count()
                .doOnNext(n -> {
                    if (n > 0) log.info("🔔 {} poste(s) non confirmé(s) signalé(s) aux gestionnaires", n);
                })
                .then();
    }

    Mono<Void> notifyAutomaticNoShows(LocalDateTime now) {
        return db.sql("""
                WITH due AS (
                    UPDATE fleet.assignments a SET no_show_notified_at = :now
                    WHERE a.status = 'NO_SHOW' AND a.no_show_notified_at IS NULL
                      AND COALESCE(a.updated_at, a.end_datetime) >= :since
                    RETURNING a.id, a.schedule_id, a.fleet_id, a.driver_id, a.vehicle_id, a.start_datetime, a.end_datetime
                )
                SELECT due.*, '' AS driver_name, v.license_plate
                FROM due LEFT JOIN fleet.vehicles v ON v.id = due.vehicle_id
                """)
                .bind("now", now)
                .bind("since", now.minusHours(NO_SHOW_LOOKBACK_HOURS))
                .map((row, meta) -> row(row))
                .all()
                .concatMap(r -> notifier.toUser(r.driverId(), null, "Absence au poste",
                        "Votre poste " + r.when() + " est clos sans aucun trajet effectué : vous êtes marqué absent."
                                + " Contactez votre gestionnaire si c'est une erreur.",
                        AppNotifier.Level.WARNING, "SHIFT_NO_SHOW",
                        AppNotifier.refs("assignmentId", r.id(), "scheduleId", r.scheduleId()))
                        .thenReturn(1))
                .count()
                .then();
    }

    private static ShiftRow row(io.r2dbc.spi.Readable row) {
        String name = row.get("driver_name", String.class);
        return new ShiftRow(row.get("id", UUID.class), row.get("schedule_id", UUID.class), row.get("fleet_id", UUID.class),
                row.get("driver_id", UUID.class), row.get("start_datetime", LocalDateTime.class),
                row.get("end_datetime", LocalDateTime.class), name == null ? "" : name, row.get("license_plate", String.class));
    }
}
