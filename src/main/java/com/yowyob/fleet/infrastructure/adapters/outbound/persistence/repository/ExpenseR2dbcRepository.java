package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.ExpenseEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface ExpenseR2dbcRepository extends ReactiveCrudRepository<ExpenseEntity, UUID> {

    Flux<ExpenseEntity> findByVehicleId(UUID vehicleId);

    Flux<ExpenseEntity> findByDriverId(UUID driverId);

    Flux<ExpenseEntity> findByFleetId(UUID fleetId);

    /**
     * Toutes les dépenses d'un manager (via manager_id sur la dépense elle-même).
     */
    @Query("SELECT * FROM fleet.expenses WHERE manager_id = :managerId ORDER BY expense_date DESC")
    Flux<ExpenseEntity> findAllByManagerId(UUID managerId);

    /**
     * Filtre par type pour un manager.
     */
    @Query("SELECT * FROM fleet.expenses WHERE expense_type = :type AND manager_id = :managerId ORDER BY expense_date DESC")
    Flux<ExpenseEntity> findByTypeAndManagerId(String type, UUID managerId);

    /**
     * Filtre par statut pour un manager.
     */
    @Query("SELECT * FROM fleet.expenses WHERE status = :status AND manager_id = :managerId ORDER BY expense_date DESC")
    Flux<ExpenseEntity> findByStatusAndManagerId(String status, UUID managerId);

    /**
     * Dépenses dans une plage de dates pour un manager.
     */
    @Query("SELECT * FROM fleet.expenses WHERE expense_date BETWEEN :start AND :end AND manager_id = :managerId ORDER BY expense_date DESC")
    Flux<ExpenseEntity> findByDateRangeAndManagerId(LocalDateTime start, LocalDateTime end, UUID managerId);

    /**
     * Dépenses approuvées d'un véhicule sur une période.
     */
    @Query("SELECT * FROM fleet.expenses WHERE vehicle_id = :vehicleId AND status = 'APPROVED' AND expense_date BETWEEN :start AND :end ORDER BY expense_date DESC")
    Flux<ExpenseEntity> findApprovedByVehicleAndDateRange(UUID vehicleId, LocalDateTime start, LocalDateTime end);

    /**
     * Dépenses approuvées d'une flotte sur une période.
     */
    @Query("SELECT * FROM fleet.expenses WHERE fleet_id = :fleetId AND status = 'APPROVED' AND expense_date BETWEEN :start AND :end ORDER BY expense_date DESC")
    Flux<ExpenseEntity> findApprovedByFleetAndDateRange(UUID fleetId, LocalDateTime start, LocalDateTime end);

    // ── Agrégats ──────────────────────────────────────────────────────────────

    @Query("SELECT COALESCE(SUM(amount), 0) FROM fleet.expenses WHERE vehicle_id = :vehicleId AND status = 'APPROVED'")
    Mono<BigDecimal> getTotalApprovedByVehicleId(UUID vehicleId);

    @Query("SELECT COALESCE(SUM(amount), 0) FROM fleet.expenses WHERE fleet_id = :fleetId AND status = 'APPROVED'")
    Mono<BigDecimal> getTotalApprovedByFleetId(UUID fleetId);

    @Query("SELECT COALESCE(SUM(amount), 0) FROM fleet.expenses WHERE fleet_id = :fleetId AND status = 'APPROVED' AND expense_date BETWEEN :monthStart AND :monthEnd")
    Mono<BigDecimal> getTotalApprovedByFleetAndMonth(UUID fleetId, LocalDateTime monthStart, LocalDateTime monthEnd);

    @Query("SELECT COALESCE(SUM(amount), 0) FROM fleet.expenses WHERE vehicle_id = :vehicleId AND status = 'APPROVED' AND expense_date BETWEEN :monthStart AND :monthEnd")
    Mono<BigDecimal> getTotalApprovedByVehicleAndMonth(UUID vehicleId, LocalDateTime monthStart, LocalDateTime monthEnd);
}
