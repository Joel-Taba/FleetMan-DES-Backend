package com.yowyob.fleet.domain.ports.in;

import com.yowyob.fleet.domain.model.GeofenceZone;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.GeofenceEventEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ManageGeofenceUseCase {


    Mono<GeofenceZone> createZone(GeofenceZone zone);

    Mono<Void> assignZoneToFleet(UUID zoneId, UUID fleetId, UUID managerId);

    Flux<Map<String, Object>> getAllExternalZones(String category);

    Flux<Map<String, Object>> getMyExternalZones(UUID managerId, String category);
    
    Flux<Map<String, Object>> getZonesByManager(UUID managerId, String category);

    Flux<Map<String, Object>> getZonesByFleet(UUID managerId, UUID fleetId);

    Mono<Map<String, Object>> getZoneDetails(UUID zoneId, UUID managerId);
    
    Flux<GeofenceZone> getMyZones(UUID managerId);
    
    Flux<GeofenceZone> getZonesByFleet(UUID fleetId);

    Flux<Map<String, Object>> getZonesByFleetManager(UUID fleetManagerId);

    Mono<GeofenceZone> getZoneDetails(UUID zoneId);
    
    Mono<Map<String, Object>> getExternalAlerts(int page, int size);
        
    Mono<Map<String, Object>> getExternalZoneDetails(String type, UUID id);

    Mono<Void> updateRemoteZone(String type, UUID id, Map<String, Object> updates);

    Mono<String> checkPointInZone(UUID zoneId, Double lat, Double lng);

    Mono<GeofenceZone> updateZone(UUID zoneId, GeofenceZone zone);

    Mono<Void> deleteZone(UUID zoneId, String type, UUID managerId);





}