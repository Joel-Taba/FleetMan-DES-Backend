package com.yowyob.fleet.domain.ports.out;

import com.yowyob.fleet.domain.model.Budget;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Port sortant : contrat de persistance des Budgets.
 * Implémenté par BudgetPersistenceAdapter dans la couche infrastructure (R2DBC).
 */
public interface BudgetPersistencePort {

    Mono<Budget> save(Budget budget);

    Mono<Budget> findById(UUID id);

    Flux<Budget> findAll();

    /**
     * Récupère tous les budgets créés par un manager.
     */
    Flux<Budget> findAllByManagerId(UUID managerId);

    /**
     * Récupère les budgets d'une flotte (scope = FLEET).
     */
    Flux<Budget> findByFleetId(UUID fleetId);

    /**
     * Récupère les budgets d'un véhicule (scope = VEHICLE).
     */
    Flux<Budget> findByVehicleId(UUID vehicleId);

    /**
     * Récupère le budget d'une entité pour un mois donné.
     */
    Mono<Budget> findByEntityAndMonth(Budget.BudgetScope scope, UUID entityId, LocalDate month);

    /**
     * Vérifie si un budget existe pour une entité ce mois.
     */
    Mono<Boolean> existsByEntityAndMonth(Budget.BudgetScope scope, UUID entityId, LocalDate month);

    /**
     * Récupère tous les budgets actifs (mois courant) d'un manager.
     * Utilisé par le job d'alerte.
     */
    Flux<Budget> findActiveByManagerId(UUID managerId, LocalDate currentMonth);

    Mono<Boolean> existsById(UUID id);

    Mono<Void> deleteById(UUID id);
}
