package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.FleetManagerGeofenceZoneEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface FleetManagerGeofenceZoneR2dbcRepository extends ReactiveCrudRepository<FleetManagerGeofenceZoneEntity, UUID> {
    
    /**
     * Récupère toutes les zones associées à un FleetManager
     */
    Flux<FleetManagerGeofenceZoneEntity> findByFleetManagerId(UUID fleetManagerId);
    
    /**
     * Vérifie si une association existe entre un FleetManager et une Zone
     */
    Mono<Boolean> existsByFleetManagerIdAndZoneId(UUID fleetManagerId, UUID zoneId);
}
