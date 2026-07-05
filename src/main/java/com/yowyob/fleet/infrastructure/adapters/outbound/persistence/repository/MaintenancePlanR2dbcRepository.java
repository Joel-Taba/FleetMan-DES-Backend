package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.MaintenancePlanEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface MaintenancePlanR2dbcRepository extends ReactiveCrudRepository<MaintenancePlanEntity, UUID> {

    @Query("SELECT * FROM fleet.maintenance_plans WHERE fleet_id = :fleetId ORDER BY maintenance_type, scope")
    Flux<MaintenancePlanEntity> findByFleetId(UUID fleetId);

    @Query("SELECT * FROM fleet.maintenance_plans WHERE vehicle_id = :vehicleId AND scope = 'VEHICLE' ORDER BY maintenance_type")
    Flux<MaintenancePlanEntity> findByVehicleId(UUID vehicleId);

    @Query("SELECT * FROM fleet.maintenance_plans WHERE manager_id = :managerId ORDER BY fleet_id, maintenance_type")
    Flux<MaintenancePlanEntity> findByManagerId(UUID managerId);

    @Query("SELECT * FROM fleet.maintenance_plans WHERE fleet_id = :fleetId AND active = true ORDER BY maintenance_type")
    Flux<MaintenancePlanEntity> findActiveByFleetId(UUID fleetId);

    /**
     * Plan effectif pour un véhicule/type : priorité VEHICLE > FLEET.
     * Si un plan VEHICLE existe, il prime sur le plan FLEET.
     */
    @Query("""
            SELECT * FROM fleet.maintenance_plans
            WHERE fleet_id = :fleetId
              AND maintenance_type = :maintenanceType
              AND active = true
              AND (scope = 'FLEET' OR (scope = 'VEHICLE' AND vehicle_id = :vehicleId))
            ORDER BY scope DESC
            LIMIT 1
            """)
    Mono<MaintenancePlanEntity> findEffectivePlan(UUID vehicleId, UUID fleetId, String maintenanceType);
}
