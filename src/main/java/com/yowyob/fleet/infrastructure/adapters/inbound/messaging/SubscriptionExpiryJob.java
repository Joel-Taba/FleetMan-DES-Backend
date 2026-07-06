package com.yowyob.fleet.infrastructure.adapters.inbound.messaging;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.UserLocalR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Expire les abonnements dépassés (hors période de grâce) et désactive les comptes.
 */
@Component
@RequiredArgsConstructor
public class SubscriptionExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionExpiryJob.class);

    private final DatabaseClient db;
    private final UserLocalR2dbcRepository userRepo;

    @Scheduled(cron = "0 30 2 * * *")
    public void expireSubscriptions() {
        log.info("=== Vérification expiration abonnements ===");
        db.sql("""
                SELECT fm.user_id, fm.subscription_end, COALESCE(sp.grace_days, 7) AS grace_days
                FROM fleet.fleet_managers fm
                LEFT JOIN fleet.subscription_plans sp ON sp.id = fm.plan_id
                WHERE fm.subscription_status = 'ACTIVE'
                  AND fm.subscription_end IS NOT NULL
                  AND fm.subscription_end + (COALESCE(sp.grace_days, 7) || ' days')::interval < CURRENT_DATE
                """)
                .fetch()
                .all()
                .flatMap(row -> {
                    UUID userId = (UUID) row.get("user_id");
                    return db.sql("UPDATE fleet.fleet_managers SET subscription_status = 'EXPIRED' WHERE user_id = :id")
                            .bind("id", userId)
                            .fetch()
                            .rowsUpdated()
                            .then(userRepo.findById(userId))
                            .flatMap(u -> {
                                u.setActive(false);
                                u.setNewRecord(false);
                                return userRepo.save(u);
                            })
                            .doOnSuccess(v -> log.info("Abonnement expiré pour manager {}", userId))
                            .onErrorResume(e -> {
                                log.warn("Échec expiration {}: {}", userId, e.getMessage());
                                return reactor.core.publisher.Mono.empty();
                            });
                })
                .doOnComplete(() -> log.info("=== Expiration abonnements terminée ==="))
                .subscribe();
    }
}
