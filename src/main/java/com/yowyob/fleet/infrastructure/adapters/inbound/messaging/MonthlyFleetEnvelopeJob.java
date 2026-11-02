package com.yowyob.fleet.infrastructure.adapters.inbound.messaging;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Chaque début de mois (et au démarrage), crée le budget FLEET du mois de chaque flotte à
 * partir de son enveloppe récurrente (fleets.monthly_budget) quand l'administrateur n'en a
 * pas déjà fixé un pour ce mois.
 */
@Component
@RequiredArgsConstructor
public class MonthlyFleetEnvelopeJob {

    private static final Logger log = LoggerFactory.getLogger(MonthlyFleetEnvelopeJob.class);

    private final DatabaseClient db;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        createMissingEnvelopes();
    }

    @Scheduled(cron = "0 5 0 1 * *")
    public void createMissingEnvelopes() {
        db.sql("""
                WITH created AS (
                    INSERT INTO fleet.budgets (id, scope, entity_id, manager_id, budget_month, amount, consumed,
                                               alert_level, notes, tenant_id)
                    SELECT gen_random_uuid(), 'FLEET', f.id, f.manager_id, date_trunc('month', CURRENT_DATE)::date,
                           f.monthly_budget, 0, 'NORMAL', 'Enveloppe mensuelle de la flotte', f.tenant_id
                    FROM fleet.fleets f
                    WHERE f.deleted_at IS NULL AND f.monthly_budget > 0 AND f.manager_id IS NOT NULL
                      AND NOT EXISTS (SELECT 1 FROM fleet.budgets b WHERE b.scope = 'FLEET' AND b.entity_id = f.id
                                      AND b.budget_month = date_trunc('month', CURRENT_DATE)::date)
                    RETURNING id, scope, entity_id, budget_month, amount, created_at, tenant_id
                )
                INSERT INTO fleet.budget_events (id, budget_id, scope, entity_id, budget_month, event_type, new_amount,
                                                 consumed, note, created_at, tenant_id)
                SELECT gen_random_uuid(), id, scope, entity_id, budget_month, 'CREATED', amount, 0,
                       'Enveloppe mensuelle de la flotte', created_at, tenant_id
                FROM created
                """)
                .fetch()
                .rowsUpdated()
                .doOnNext(n -> {
                    if (n > 0) {
                        log.info("💰 {} enveloppe(s) de flotte créée(s) pour le mois", n);
                    }
                })
                .onErrorResume(e -> {
                    log.warn("Création des enveloppes mensuelles ignorée : {}", e.getMessage());
                    return reactor.core.publisher.Mono.empty();
                })
                .subscribe();
    }
}
