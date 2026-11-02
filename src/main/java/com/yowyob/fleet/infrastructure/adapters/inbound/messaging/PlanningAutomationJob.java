package com.yowyob.fleet.infrastructure.adapters.inbound.messaging;

import java.time.LocalDateTime;
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
 * Automatismes du planning (toutes les 5 minutes et au démarrage) :
 *   * un trajet planifié non parti 2 h après l'heure prévue devient « manqué » (MISSED) et le
 *     gestionnaire de la flotte est notifié ;
 *   * un poste dont l'heure de fin est passée se termine : COMPLETED avec km = somme des trajets
 *     terminés qui lui sont rattachés ; un poste jamais démarré dont tous les trajets ont été
 *     manqués devient NO_SHOW. Un poste dont un trajet est encore en cours attend son retour.
 */
@Component
@RequiredArgsConstructor
public class PlanningAutomationJob {

    private static final Logger log = LoggerFactory.getLogger(PlanningAutomationJob.class);
    static final int MISSED_AFTER_HOURS = 2;

    private final DatabaseClient db;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        run();
    }

    @Scheduled(fixedDelay = 300_000, initialDelay = 300_000)
    public void run() {
        // Horloge applicative : les dates de trajet et de poste sont enregistrées en heure locale.
        LocalDateTime now = LocalDateTime.now();
        markMissedTrips(now)
                .then(closeEndedShifts(now))
                .onErrorResume(e -> {
                    log.warn("Automatismes du planning ignorés : {}", e.getMessage());
                    return Mono.empty();
                })
                .subscribe();
    }

    Mono<Void> markMissedTrips(LocalDateTime now) {
        return db.sql("""
                WITH missed AS (
                    UPDATE fleet.trips t
                    SET status = 'MISSED',
                        cancel_reason = 'Non parti ' || :hours || ' h après l''heure de départ prévue'
                    WHERE t.status = 'SCHEDULED' AND t.deleted_at IS NULL
                      AND (t.start_date + COALESCE(t.start_time, TIME '00:00')) < :limit
                    RETURNING t.id, t.trip_code, t.fleet_id, t.tenant_id
                )
                INSERT INTO fleet.notifications (id, user_id, title, message, type, is_read, created_at, data, tenant_id)
                SELECT gen_random_uuid(), f.manager_id, 'Trajet manqué',
                       'Le trajet ' || m.trip_code || ' n''est pas parti dans les ' || :hours
                           || ' h suivant l''heure prévue : il est marqué manqué.',
                       'WARNING', FALSE, :now,
                       json_build_object('type', 'TRIP_MISSED', 'tripId', m.id)::text, m.tenant_id
                FROM missed m JOIN fleet.fleets f ON f.id = m.fleet_id
                WHERE f.manager_id IS NOT NULL
                """)
                .bind("hours", MISSED_AFTER_HOURS)
                .bind("limit", now.minusHours(MISSED_AFTER_HOURS))
                .bind("now", now)
                .fetch().rowsUpdated()
                .doOnNext(n -> {
                    if (n > 0) log.info("⏱️ {} trajet(s) marqué(s) manqué(s)", n);
                })
                .then();
    }

    Mono<Void> closeEndedShifts(LocalDateTime now) {
        String ended = """
                a.status IN ('PENDING', 'IN_PROGRESS') AND a.end_datetime < :now
                AND NOT EXISTS (SELECT 1 FROM fleet.trips t WHERE t.assignment_id = a.id
                                AND t.deleted_at IS NULL AND t.status IN ('SCHEDULED', 'DEPARTED', 'ARRIVED'))
                """;
        Mono<Long> noShows = db.sql("""
                UPDATE fleet.assignments a SET status = 'NO_SHOW', updated_at = :now
                WHERE a.status = 'PENDING' AND\s""" + ended + """
                  AND EXISTS (SELECT 1 FROM fleet.trips t WHERE t.assignment_id = a.id AND t.status = 'MISSED')
                  AND NOT EXISTS (SELECT 1 FROM fleet.trips t WHERE t.assignment_id = a.id AND t.status = 'COMPLETED')
                """)
                .bind("now", now)
                .fetch().rowsUpdated();
        Mono<Long> completed = db.sql("""
                UPDATE fleet.assignments a SET status = 'COMPLETED', updated_at = :now,
                    actual_km = (SELECT COALESCE(SUM(COALESCE(t.computed_distance_km, t.distance_km, 0)), 0)
                                 FROM fleet.trips t
                                 WHERE t.assignment_id = a.id AND t.status = 'COMPLETED' AND t.deleted_at IS NULL)
                WHERE\s""" + ended)
                .bind("now", now)
                .fetch().rowsUpdated();
        return noShows.then(completed)
                .doOnNext(n -> {
                    if (n > 0) log.info("🗓️ {} poste(s) terminé(s) automatiquement", n);
                })
                .then();
    }
}
