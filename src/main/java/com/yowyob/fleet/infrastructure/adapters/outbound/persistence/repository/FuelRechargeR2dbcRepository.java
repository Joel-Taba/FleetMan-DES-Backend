package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.FuelRechargeEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface FuelRechargeR2dbcRepository extends ReactiveCrudRepository<FuelRechargeEntity, UUID> {

    Flux<FuelRechargeEntity> findByVehicleId(UUID vehicleId);

    Flux<FuelRechargeEntity> findByDriverId(UUID driverId);

    /**
     * Récupère toutes les recharges des véhicules appartenant à un manager donné.
     */
    @Query("SELECT fr.* FROM fleet.fuel_recharges fr " +
           "JOIN fleet.vehicles v ON fr.vehicle_id = v.id " +
           "WHERE v.manager_id = :managerId " +
           "ORDER BY fr.recharge_date_time DESC")
    Flux<FuelRechargeEntity> findAllByManagerId(UUID managerId);

    /**
     * Recharges dans une plage de dates pour un manager donné.
     */
    @Query("SELECT fr.* FROM fleet.fuel_recharges fr " +
           "JOIN fleet.vehicles v ON fr.vehicle_id = v.id " +
           "WHERE fr.recharge_date_time BETWEEN :start AND :end AND v.manager_id = :managerId " +
           "ORDER BY fr.recharge_date_time DESC")
    Flux<FuelRechargeEntity> findByDateRangeAndManagerId(LocalDateTime start, LocalDateTime end, UUID managerId);

    // ── Agrégats KPIs ─────────────────────────────────────────────────────────

    /**
     * Quantité totale rechargée (litres) pour un véhicule.
     */
    @Query("SELECT COALESCE(SUM(quantity), 0) FROM fleet.fuel_recharges WHERE vehicle_id = :vehicleId")
    Mono<BigDecimal> getTotalQuantityByVehicleId(UUID vehicleId);

    /**
     * Coût total des recharges pour un véhicule.
     */
    @Query("SELECT COALESCE(SUM(price), 0) FROM fleet.fuel_recharges WHERE vehicle_id = :vehicleId")
    Mono<BigDecimal> getTotalCostByVehicleId(UUID vehicleId);
}
