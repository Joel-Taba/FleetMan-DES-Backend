package com.yowyob.fleet.domain.ports.out;

import com.yowyob.fleet.domain.model.Expense;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Port sortant : contrat de persistance des Dépenses.
 * Implémenté par ExpensePersistenceAdapter dans la couche infrastructure (R2DBC).
 */
public interface ExpensePersistencePort {

    Mono<Expense> save(Expense expense);

    Mono<Expense> findById(UUID id);

    Flux<Expense> findAll();

    /**
     * Récupère toutes les dépenses des flottes gérées par un manager.
     */
    Flux<Expense> findAllByManagerId(UUID managerId);

    /**
     * Récupère les dépenses d'une flotte spécifique.
     */
    Flux<Expense> findByFleetId(UUID fleetId);

    /**
     * Récupère les dépenses d'un véhicule spécifique.
     */
    Flux<Expense> findByVehicleId(UUID vehicleId);

    /**
     * Récupère les dépenses d'un chauffeur spécifique.
     */
    Flux<Expense> findByDriverId(UUID driverId);

    /**
     * Filtre par type de dépense pour un manager.
     */
    Flux<Expense> findByTypeAndManagerId(Expense.ExpenseType type, UUID managerId);

    /**
     * Filtre par statut de validation pour un manager.
     */
    Flux<Expense> findByStatusAndManagerId(Expense.ExpenseStatus status, UUID managerId);

    /**
     * Dépenses dans une plage de dates pour un manager.
     */
    Flux<Expense> findByDateRange(LocalDateTime start, LocalDateTime end, UUID managerId);

    /**
     * Dépenses approuvées d'un véhicule sur une période.
     */
    Flux<Expense> findApprovedByVehicleAndDateRange(UUID vehicleId,
                                                     LocalDateTime start,
                                                     LocalDateTime end);

    /**
     * Dépenses approuvées d'une flotte sur une période.
     */
    Flux<Expense> findApprovedByFleetAndDateRange(UUID fleetId,
                                                   LocalDateTime start,
                                                   LocalDateTime end);

    // ── Agrégats pour KPIs & Budgets ──────────────────────────────────────────

    /**
     * Somme des dépenses approuvées pour un véhicule.
     */
    Mono<BigDecimal> getTotalApprovedByVehicleId(UUID vehicleId);

    /**
     * Somme des dépenses approuvées pour une flotte.
     */
    Mono<BigDecimal> getTotalApprovedByFleetId(UUID fleetId);

    /**
     * Somme des dépenses approuvées d'une flotte sur un mois donné.
     * Utilisé pour le recalcul budgétaire mensuel.
     */
    Mono<BigDecimal> getTotalApprovedByFleetAndMonth(UUID fleetId,
                                                      LocalDateTime monthStart,
                                                      LocalDateTime monthEnd);

    /**
     * Somme des dépenses approuvées d'un véhicule sur un mois donné.
     */
    Mono<BigDecimal> getTotalApprovedByVehicleAndMonth(UUID vehicleId,
                                                        LocalDateTime monthStart,
                                                        LocalDateTime monthEnd);

    Mono<Boolean> existsById(UUID id);

    Mono<Void> deleteById(UUID id);
}
