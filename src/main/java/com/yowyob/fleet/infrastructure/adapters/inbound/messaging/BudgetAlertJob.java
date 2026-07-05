package com.yowyob.fleet.infrastructure.adapters.inbound.messaging;

import com.yowyob.fleet.domain.model.Budget;
import com.yowyob.fleet.domain.ports.out.BudgetPersistencePort;
import com.yowyob.fleet.domain.ports.out.FleetRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

/**
 * Job planifié : Vérification quotidienne des alertes budgétaires.
 *
 * S'exécute chaque jour à 7h00 et :
 * 1. Recalcule le montant consommé de tous les budgets actifs (mois courant)
 * 2. Détecte les budgets atteignant 80% de consommation → alerte WARNING
 * 3. Détecte les budgets dépassant 100% → alerte EXCEEDED
 * 4. Publie les alertes via le port de notification (à brancher sur le module Alertes)
 *
 * NOTE : En environnement multi-pods (Kubernetes), ajouter ShedLock
 * pour éviter les exécutions parallèles.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BudgetAlertJob {

    private final BudgetPersistencePort budgetPersistence;
    private final FleetRepositoryPort fleetPort;

    /**
     * Exécution quotidienne à 7h00.
     */
    @Scheduled(cron = "0 0 7 * * *")
    public void checkBudgetAlerts() {
        log.info("=== Démarrage du job de vérification des alertes budgétaires ===");
        LocalDate currentMonth = LocalDate.now().withDayOfMonth(1);

        fleetPort.findAll()
                .flatMap(fleet -> budgetPersistence
                        .findActiveByManagerId(fleet.managerId(), currentMonth)
                        .flatMap(budget -> processAlert(budget))
                        .doOnError(e -> log.error("Erreur traitement budget pour manager {}: {}",
                                fleet.managerId(), e.getMessage()))
                        .onErrorResume(e -> reactor.core.publisher.Flux.empty())
                )
                .doOnComplete(() -> log.info("=== Job alertes budgétaires terminé ==="))
                .subscribe();
    }

    /**
     * Traite les alertes pour un budget donné.
     * Envoie une alerte une seule fois par niveau (80% et 100%).
     */
    private Mono<Budget> processAlert(Budget budget) {
        Budget.AlertLevel level = budget.getAlertLevel();
        boolean needsSave = false;

        if (level == Budget.AlertLevel.WARNING && !budget.isAlert80Sent()) {
            log.warn("🟡 ALERTE BUDGET 80% — Entité {} ({}) : {}% consommé ({}/{} FCFA)",
                    budget.getEntityId(),
                    budget.getScope(),
                    budget.consumptionRate(),
                    budget.getConsumed(),
                    budget.getAmount()
            );
            budget.setAlert80Sent(true);
            needsSave = true;
            // TODO Module 8 — Alertes : notificationPort.sendBudgetAlert(budget, AlertLevel.WARNING)
        }

        if (level == Budget.AlertLevel.EXCEEDED && !budget.isAlert100Sent()) {
            log.warn("🔴 ALERTE BUDGET 100% DÉPASSÉ — Entité {} ({}) : {}% consommé ({}/{} FCFA)",
                    budget.getEntityId(),
                    budget.getScope(),
                    budget.consumptionRate(),
                    budget.getConsumed(),
                    budget.getAmount()
            );
            budget.setAlert100Sent(true);
            needsSave = true;
            // TODO Module 8 — Alertes : notificationPort.sendBudgetAlert(budget, AlertLevel.EXCEEDED)
        }

        if (needsSave) {
            return budgetPersistence.save(budget)
                    .doOnSuccess(b -> log.debug("✅ Flags alerte mis à jour pour budget {}", b.getId()));
        }

        return Mono.just(budget);
    }
}
