package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.MaintenanceEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface MaintenanceR2dbcRepository extends ReactiveCrudRepository<MaintenanceEntity, UUID> {

    Flux<MaintenanceEntity> findByVehicleId(UUID vehicleId);

    Flux<MaintenanceEntity> findByDriverId(UUID driverId);

    /**
     * Récupère toutes les maintenances des véhicules appartenant à un manager donné.
     */
    @Query("SELECT m.* FROM fleet.maintenances m " +
           "JOIN fleet.vehicles v ON m.vehicle_id = v.id " +
           "WHERE v.manager_id = :managerId " +
           "ORDER BY m.date_time DESC")
    Flux<MaintenanceEntity> findAllByManagerId(UUID managerId);

    /**
     * Maintenances dans une plage de dates.
     */
    @Query("SELECT * FROM fleet.maintenances WHERE date_time BETWEEN :start AND :end ORDER BY date_time DESC")
    Flux<MaintenanceEntity> findByDateRange(LocalDateTime start, LocalDateTime end);

    /**
     * Compte les maintenances impliquant un chauffeur donné.
     */
    @Query("SELECT COUNT(*) FROM fleet.maintenances WHERE driver_id = :driverId")
    Mono<Long> countByDriverId(UUID driverId);

    @Query("""
            SELECT COALESCE(SUM(m.cost), 0) FROM fleet.maintenances m
            JOIN fleet.vehicles v ON m.vehicle_id = v.id
            WHERE v.fleet_id = :fleetId
              AND m.date_time >= :start AND m.date_time < :end
            """)
    Mono<BigDecimal> sumCostByFleetAndPeriod(UUID fleetId, LocalDateTime start, LocalDateTime end);

    @Query("""
            SELECT COALESCE(SUM(m.cost), 0) FROM fleet.maintenances m
            WHERE m.vehicle_id = :vehicleId
              AND m.date_time >= :start AND m.date_time < :end
            """)
    Mono<BigDecimal> sumCostByVehicleAndPeriod(UUID vehicleId, LocalDateTime start, LocalDateTime end);
}
