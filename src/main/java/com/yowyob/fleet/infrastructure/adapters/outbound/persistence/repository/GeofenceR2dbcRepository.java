package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.GeofenceZoneEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import java.util.UUID;

@Repository
public interface GeofenceR2dbcRepository extends ReactiveCrudRepository<GeofenceZoneEntity, UUID> {
   
    Flux<GeofenceZoneEntity> findByManagerId(UUID managerId);

    // CORRECTION : On retire le JOIN inutile et on filtre réellement par fleet_id
    @Query("SELECT * FROM fleet.geofence_zones WHERE manager_id = :managerId AND fleet_id = :fleetId")
    Flux<GeofenceZoneEntity> findAllByManagerIdAndFleetId(UUID managerId, UUID fleetId);

    Flux<GeofenceZoneEntity> findAllByFleetId(UUID fleetId);
}