package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.BudgetEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

@Repository
public interface BudgetR2dbcRepository extends ReactiveCrudRepository<BudgetEntity, UUID> {

    @Query("SELECT * FROM fleet.budgets WHERE manager_id = :managerId ORDER BY budget_month DESC")
    Flux<BudgetEntity> findAllByManagerId(UUID managerId);

    @Query("SELECT * FROM fleet.budgets WHERE scope = 'FLEET' AND entity_id = :fleetId ORDER BY budget_month DESC")
    Flux<BudgetEntity> findByFleetId(UUID fleetId);

    @Query("SELECT * FROM fleet.budgets WHERE scope = 'VEHICLE' AND entity_id = :vehicleId ORDER BY budget_month DESC")
    Flux<BudgetEntity> findByVehicleId(UUID vehicleId);

    /**
     * Budget d'une entité pour un mois donné (contrainte d'unicité).
     */
    @Query("SELECT * FROM fleet.budgets WHERE scope = :scope AND entity_id = :entityId AND budget_month = :month LIMIT 1")
    Mono<BudgetEntity> findByEntityAndMonth(String scope, UUID entityId, LocalDate month);

    /**
     * Vérifie l'existence d'un budget pour une entité ce mois.
     */
    @Query("SELECT COUNT(*) > 0 FROM fleet.budgets WHERE scope = :scope AND entity_id = :entityId AND budget_month = :month")
    Mono<Boolean> existsByEntityAndMonth(String scope, UUID entityId, LocalDate month);

    /**
     * Budgets actifs (mois courant) d'un manager. Utilisé par le job d'alerte.
     */
    @Query("SELECT * FROM fleet.budgets WHERE manager_id = :managerId AND budget_month = :currentMonth")
    Flux<BudgetEntity> findActiveByManagerId(UUID managerId, LocalDate currentMonth);
}
