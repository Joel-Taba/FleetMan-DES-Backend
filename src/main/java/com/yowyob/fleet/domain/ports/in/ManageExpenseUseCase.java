package com.yowyob.fleet.domain.ports.in;

import com.yowyob.fleet.domain.model.Expense;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Port d'entrée : cas d'utilisation pour la gestion des Dépenses.
 * Invoqué par ExpenseController.
 *
 * Agrège les dépenses provenant de trois sources :
 * - Automatique : FuelRecharge, Maintenance, Incident (via projection)
 * - Manuel : saisie directe par le Manager ou Driver
 */
public interface ManageExpenseUseCase {

    // ── Commandes ─────────────────────────────────────────────────────────────

    record CreateManualExpenseCommand(
            Expense.ExpenseType expenseType,    // Limité à FINE, TOLL, OTHER pour saisie manuelle
            BigDecimal amount,
            String description,
            LocalDateTime expenseDate,          // Optionnel — défaut now()
            UUID vehicleId,
            UUID driverId                       // Optionnel
    ) {}

    record UpdateExpenseCommand(
            UUID expenseId,
            BigDecimal amount,                  // Optionnel — montant mis à jour
            String description                  // Optionnel
    ) {}

    record ValidateExpenseCommand(
            UUID expenseId,
            UUID managerId,
            String rejectionReason              // Obligatoire si rejet, null si approbation
    ) {}

    // ── Use Cases — Création ──────────────────────────────────────────────────

    /**
     * Crée une dépense manuelle (FINE, TOLL, OTHER).
     * La dépense démarre en statut PENDING.
     * Vérifie l'existence du véhicule.
     * Met à jour les budgets actifs de la flotte.
     */
    Mono<Expense> createManualExpense(CreateManualExpenseCommand command);

    /**
     * Crée une dépense auto-générée depuis une opération existante.
     * Utilisé en interne après la création d'une Maintenance, FuelRecharge ou Incident.
     * La dépense est directement en statut APPROVED.
     */
    Mono<Expense> createAutoExpense(Expense.ExpenseType type,
                                    BigDecimal amount,
                                    String description,
                                    UUID sourceId,
                                    UUID vehicleId,
                                    String vehicleRegistration,
                                    UUID fleetId,
                                    UUID managerId,
                                    UUID driverId,
                                    String driverFullName);

    // ── Use Cases — Lecture ───────────────────────────────────────────────────

    /**
     * Récupère une dépense par son identifiant.
     */
    Mono<Expense> getById(UUID id);

    /**
     * Liste toutes les dépenses d'un manager (tous véhicules, toutes flottes).
     */
    Flux<Expense> getAllByManager(UUID managerId);

    /**
     * Liste les dépenses d'une flotte spécifique.
     */
    Flux<Expense> getByFleet(UUID fleetId);

    /**
     * Liste les dépenses d'un véhicule spécifique.
     */
    Flux<Expense> getByVehicle(UUID vehicleId);

    /**
     * Liste les dépenses d'un chauffeur spécifique.
     */
    Flux<Expense> getByDriver(UUID driverId);

    /**
     * Filtre les dépenses par type (FUEL, MAINTENANCE, INCIDENT, FINE, TOLL, OTHER).
     */
    Flux<Expense> getByType(Expense.ExpenseType type, UUID managerId);

    /**
     * Filtre les dépenses par statut (PENDING, APPROVED, REJECTED).
     */
    Flux<Expense> getByStatus(Expense.ExpenseStatus status, UUID managerId);

    /**
     * Dépenses dans une plage de dates.
     */
    Flux<Expense> getByDateRange(LocalDateTime start, LocalDateTime end, UUID managerId);

    /**
     * Dépenses en attente de validation (PENDING).
     * Utile pour le tableau de bord de validation du Manager.
     */
    Flux<Expense> getPendingExpenses(UUID managerId);

    // ── Use Cases — Agrégats / KPIs ───────────────────────────────────────────

    /**
     * Coût total des dépenses approuvées pour un véhicule.
     */
    Mono<BigDecimal> getTotalApprovedByVehicle(UUID vehicleId);

    /**
     * Coût total des dépenses approuvées pour une flotte.
     */
    Mono<BigDecimal> getTotalApprovedByFleet(UUID fleetId);

    /**
     * Coût total des dépenses par type pour un manager (pour les graphiques camembert).
     */
    Mono<ExpenseSummaryDto> getSummaryByManager(UUID managerId);

    // ── Use Cases — Validation ────────────────────────────────────────────────

    /**
     * Approuve une dépense manuelle en attente.
     */
    Mono<Expense> approve(UUID expenseId, UUID managerId);

    /**
     * Rejette une dépense manuelle avec un motif obligatoire.
     */
    Mono<Expense> reject(ValidateExpenseCommand command);

    // ── Use Cases — Modification / Suppression ────────────────────────────────

    /**
     * Met à jour une dépense manuelle en attente (montant, description).
     * Interdit sur les dépenses auto-générées ou déjà validées.
     */
    Mono<Expense> update(UpdateExpenseCommand command);

    /**
     * Supprime une dépense manuelle.
     * Interdit sur les dépenses auto-générées.
     */
    Mono<Void> delete(UUID id);

    // ── Records de résumé ─────────────────────────────────────────────────────

    /**
     * Résumé des dépenses par type pour un manager sur une période.
     */
    record ExpenseSummaryDto(
            BigDecimal totalFuel,
            BigDecimal totalMaintenance,
            BigDecimal totalIncident,
            BigDecimal totalFine,
            BigDecimal totalToll,
            BigDecimal totalOther,
            BigDecimal grandTotal,
            long totalCount
    ) {}
}
