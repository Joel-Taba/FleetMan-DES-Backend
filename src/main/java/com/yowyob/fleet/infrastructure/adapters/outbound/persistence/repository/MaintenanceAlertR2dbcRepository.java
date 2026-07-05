package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.MaintenanceAlertEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface MaintenanceAlertR2dbcRepository extends ReactiveCrudRepository<MaintenanceAlertEntity, UUID> {

    @Query("SELECT * FROM fleet.maintenance_alerts WHERE manager_id = :managerId AND status != 'RESOLVED' ORDER BY status DESC, days_remaining ASC NULLS LAST")
    Flux<MaintenanceAlertEntity> findActiveByManagerId(UUID managerId);

    @Query("SELECT * FROM fleet.maintenance_alerts WHERE manager_id = :managerId AND status IN ('DUE','OVERDUE') ORDER BY status DESC, days_remaining ASC NULLS LAST")
    Flux<MaintenanceAlertEntity> findUrgentByManagerId(UUID managerId);

    @Query("SELECT * FROM fleet.maintenance_alerts WHERE vehicle_id = :vehicleId ORDER BY created_at DESC")
    Flux<MaintenanceAlertEntity> findByVehicleId(UUID vehicleId);

    @Query("SELECT * FROM fleet.maintenance_alerts WHERE fleet_id = :fleetId AND status != 'RESOLVED' ORDER BY status DESC, days_remaining ASC NULLS LAST")
    Flux<MaintenanceAlertEntity> findActiveByFleetId(UUID fleetId);

    /**
     * Alerte active unique par véhicule et type de maintenance.
     * Utilisé pour éviter les doublons lors de la génération.
     */
    @Query("SELECT * FROM fleet.maintenance_alerts WHERE vehicle_id = :vehicleId AND maintenance_type = :maintenanceType AND status != 'RESOLVED' LIMIT 1")
    Mono<MaintenanceAlertEntity> findActiveByVehicleAndType(UUID vehicleId, String maintenanceType);
}
