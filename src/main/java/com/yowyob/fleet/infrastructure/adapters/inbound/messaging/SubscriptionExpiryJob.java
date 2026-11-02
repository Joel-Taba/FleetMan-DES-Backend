package com.yowyob.fleet.infrastructure.adapters.inbound.messaging;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Expire les abonnements dépassés (hors période de grâce). Le compte n'est pas désactivé :
 * le gestionnaire se connecte pour renouveler, les fonctions restent bloquées d'ici là.
 */
@Component
@RequiredArgsConstructor
public class SubscriptionExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionExpiryJob.class);

    private final DatabaseClient db;

    @Scheduled(cron = "0 30 2 * * *")
    public void expireSubscriptions() {
        log.info("=== Vérification expiration abonnements ===");
        db.sql("""
                SELECT c.id AS user_id, c.subscription_end,
                       COALESCE(
                         (SELECT setting_value::int FROM fleet.app_settings WHERE setting_key = 'subscription_grace_days'),
                         COALESCE(sp.grace_days, 7)
                       ) AS grace_days
                FROM fleet.companies c
                LEFT JOIN fleet.subscription_plans sp ON sp.id = c.plan_id
                WHERE c.subscription_status = 'ACTIVE'
                  AND c.subscription_end IS NOT NULL
                  AND c.subscription_end + (
                        COALESCE(
                          (SELECT setting_value::int FROM fleet.app_settings WHERE setting_key = 'subscription_grace_days'),
                          COALESCE(sp.grace_days, 7)
                        ) || ' days'
                      )::interval < CURRENT_DATE
                """)
                .fetch()
                .all()
                .flatMap(row -> {
                    UUID userId = (UUID) row.get("user_id");
                    // Les comptes restent actifs : l'administrateur doit pouvoir se connecter pour
                    // renouveler. PlanAccessWebFilter bloque les fonctions tant que c'est expiré.
                    return db.sql("UPDATE fleet.companies SET subscription_status = 'EXPIRED', updated_at = NOW() WHERE id = :id")
                            .bind("id", userId)
                            .fetch()
                            .rowsUpdated()
                            .doOnSuccess(v -> log.info("Abonnement expiré pour l'entreprise {}", userId))
                            .onErrorResume(e -> {
                                log.warn("Échec expiration {}: {}", userId, e.getMessage());
                                return reactor.core.publisher.Mono.empty();
                            });
                })
                .doOnComplete(() -> log.info("=== Expiration abonnements terminée ==="))
                .subscribe();
    }
}
