package com.yowyob.fleet.infrastructure.adapters.inbound.messaging;

import com.yowyob.fleet.domain.model.DocumentCatalog;
import java.time.LocalDate;
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
 * Chaque nuit (et au démarrage) : alerte sur les pièces en vigueur qui expirent (J-30, J-15, J-7)
 * ou ont expiré. Destinataires : le gestionnaire de la flotte, le chauffeur concerné ; pour un
 * gestionnaire, lui-même et son administrateur. Une seule notification par palier et par
 * destinataire (fleet.document_alerts).
 */
@Component
@RequiredArgsConstructor
public class DocumentExpiryCheckJob {

    private static final Logger log = LoggerFactory.getLogger(DocumentExpiryCheckJob.class);

    private final DatabaseClient db;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        sendAlerts();
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void sendAlerts() {
        LocalDate today = LocalDate.now();
        String labels = java.util.Arrays.stream(DocumentCatalog.TargetType.values())
                .flatMap(t -> DocumentCatalog.entries(t).stream())
                .map(e -> "WHEN '" + e.docType() + "' THEN '" + e.label().replace("'", "''") + "' ")
                .distinct()
                .collect(java.util.stream.Collectors.joining());
        db.sql("""
                WITH due AS (
                    SELECT d.id, d.target_type, d.target_id, d.doc_type, d.expiry_date, d.tenant_id,
                           CASE WHEN d.expiry_date < :today THEN 'EXPIRED'
                                WHEN d.expiry_date <= :today + 7 THEN 'J7'
                                WHEN d.expiry_date <= :today + 15 THEN 'J15'
                                ELSE 'J30' END AS alert_type
                    FROM fleet.documents d
                    WHERE d.is_current AND d.archived_at IS NULL AND d.review_status = 'APPROVED'
                      AND d.expiry_date <= :today + 30
                ),
                recipients AS (
                    SELECT due.*, f.manager_id AS user_id, v.license_plate AS who
                    FROM due JOIN fleet.vehicles v ON due.target_type = 'VEHICLE' AND v.id = due.target_id
                    JOIN fleet.fleets f ON f.id = v.fleet_id
                    UNION ALL
                    SELECT due.*, r.user_id, TRIM(COALESCE(u.first_name, '') || ' ' || COALESCE(u.last_name, ''))
                    FROM due JOIN fleet.drivers dr ON due.target_type = 'DRIVER' AND dr.user_id = due.target_id
                    JOIN fleet.users u ON u.id = dr.user_id
                    LEFT JOIN fleet.fleets f ON f.id = dr.fleet_id
                    CROSS JOIN LATERAL (VALUES (dr.user_id), (f.manager_id)) AS r(user_id)
                    UNION ALL
                    SELECT due.*, r.user_id, TRIM(COALESCE(u.first_name, '') || ' ' || COALESCE(u.last_name, ''))
                    FROM due JOIN fleet.fleet_managers fm ON due.target_type = 'MANAGER' AND fm.user_id = due.target_id
                    JOIN fleet.users u ON u.id = fm.user_id
                    CROSS JOIN LATERAL (VALUES (fm.user_id), (fm.created_by_admin_id)) AS r(user_id)
                ),
                fresh AS (
                    INSERT INTO fleet.document_alerts (id, document_id, document_type, alert_type, sent_at, recipient_id, tenant_id)
                    SELECT gen_random_uuid(), r.id, r.target_type, r.alert_type, :now, r.user_id, r.tenant_id
                    FROM recipients r
                    WHERE r.user_id IS NOT NULL
                    ON CONFLICT (document_id, alert_type, recipient_id) DO NOTHING
                    RETURNING document_id, alert_type, recipient_id
                )
                INSERT INTO fleet.notifications (id, user_id, title, message, type, is_read, created_at, data, tenant_id)
                SELECT gen_random_uuid(), r.user_id,
                       CASE WHEN r.alert_type = 'EXPIRED' THEN 'Document expiré' ELSE 'Document bientôt expiré' END,
                       r.who || ' : ' || CASE r.doc_type LABELS ELSE r.doc_type END || CASE WHEN r.alert_type = 'EXPIRED' THEN ' a expiré le ' ELSE ' expire le ' END
                           || to_char(r.expiry_date, 'DD/MM/YYYY') || '.',
                       CASE WHEN r.alert_type IN ('EXPIRED', 'J7') THEN 'WARNING' ELSE 'INFO' END,
                       FALSE, :now,
                       json_build_object('type', 'DOCUMENT', 'documentId', r.id, 'alert', r.alert_type)::text, r.tenant_id
                FROM fresh JOIN recipients r
                  ON r.id = fresh.document_id AND r.alert_type = fresh.alert_type AND r.user_id = fresh.recipient_id
                """.replace("LABELS", labels))
                .bind("today", today)
                .bind("now", LocalDateTime.now())
                .fetch().rowsUpdated()
                .doOnNext(n -> {
                    if (n > 0) log.info("📄 {} alerte(s) d'expiration de document envoyée(s)", n);
                })
                .onErrorResume(e -> {
                    log.warn("Alertes d'expiration de documents ignorées : {}", e.getMessage());
                    return Mono.empty();
                })
                .subscribe();
    }
}
