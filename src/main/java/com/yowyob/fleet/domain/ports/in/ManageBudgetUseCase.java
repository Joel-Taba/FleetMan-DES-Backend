package com.yowyob.fleet.domain.ports.in;

import com.yowyob.fleet.domain.model.Budget;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Port d'entrée : cas d'utilisation pour la gestion des Budgets.
 * Invoqué par BudgetController.
 */
public interface ManageBudgetUseCase {

    // ── Commandes ─────────────────────────────────────────────────────────────

    record CreateBudgetCommand(
            Budget.BudgetScope scope,
            UUID entityId,          // fleetId ou vehicleId selon scope
            UUID managerId,
            LocalDate budgetMonth,  // Normalisé au 1er du mois
            BigDecimal amount,
            String notes            // Optionnel
    ) {}

    record UpdateBudgetCommand(
            UUID budgetId,
            BigDecimal amount,      // Nouveau plafond
            String notes            // Optionnel
    ) {}

    // ── Use Cases — CRUD ──────────────────────────────────────────────────────

    /**
     * Crée un budget mensuel pour une flotte ou un véhicule.
     * Vérifie qu'aucun budget n'existe déjà pour cette entité ce mois-ci.
     */
    Mono<Budget> createBudget(CreateBudgetCommand command);

    /**
     * Récupère un budget par son identifiant.
     */
    Mono<Budget> getById(UUID id);

    /**
     * Liste tous les budgets d'un manager.
     */
    Flux<Budget> getAllByManager(UUID managerId);

    /**
     * Liste les budgets d'une flotte spécifique.
     */
    Flux<Budget> getByFleet(UUID fleetId);

    /**
     * Liste les budgets d'un véhicule spécifique.
     */
    Flux<Budget> getByVehicle(UUID vehicleId);

    /**
     * Récupère le budget actif (mois courant) pour une entité.
     */
    Mono<Budget> getCurrentBudget(Budget.BudgetScope scope, UUID entityId);

    /**
     * Récupère le budget pour une entité et un mois donné.
     */
    Mono<Budget> getBudgetForMonth(Budget.BudgetScope scope, UUID entityId, LocalDate month);

    /**
     * Met à jour le plafond ou les notes d'un budget existant.
     */
    Mono<Budget> update(UpdateBudgetCommand command);

    /**
     * Supprime un budget.
     */
    Mono<Void> delete(UUID id);

    // ── Use Cases — Recalcul ──────────────────────────────────────────────────

    /**
     * Recalcule le montant consommé d'un budget depuis les dépenses approuvées.
     * Appelé automatiquement après chaque création/validation de dépense.
     * Retourne le budget mis à jour avec le nouveau niveau d'alerte.
     */
    Mono<Budget> recalculateConsumed(UUID budgetId);

    /**
     * Recalcule tous les budgets actifs d'un manager.
     * Appelé par le job planifié BudgetAlertJob.
     */
    Flux<Budget> recalculateAllActiveByManager(UUID managerId);

    // ── Records résumé ────────────────────────────────────────────────────────

    /**
     * Vue résumée d'un budget avec le taux de consommation.
     */
    record BudgetStatusDto(
            UUID budgetId,
            Budget.BudgetScope scope,
            UUID entityId,
            LocalDate budgetMonth,
            BigDecimal amount,
            BigDecimal consumed,
            BigDecimal remaining,
            BigDecimal consumptionRate,
            Budget.AlertLevel alertLevel,
            boolean exceeded
    ) {
        public static BudgetStatusDto from(Budget b) {
            return new BudgetStatusDto(
                    b.getId(),
                    b.getScope(),
                    b.getEntityId(),
                    b.getBudgetMonth(),
                    b.getAmount(),
                    b.getConsumed(),
                    b.remaining(),
                    b.consumptionRate(),
                    b.getAlertLevel(),
                    b.isExceeded()
            );
        }
    }
}
