package com.yowyob.fleet.domain.ports.out;

import com.yowyob.fleet.domain.model.GeofenceZone;
import com.yowyob.fleet.domain.model.Vehicle;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ExternalGeofencePort {
    Mono<UUID> synchronizeZone(GeofenceZone zone);
    Mono<Void> updateRemoteZone(String type, UUID id, Map<String, Object> updates);
    Mono<Void> deleteRemoteZone(String type, UUID zoneId);
    Mono<String> checkPointInZone(UUID zoneId, Double lat, Double lng);
    
    // NOUVELLE MÉTHODE
    Mono<List<Map<String, Object>>> getZonesByOwner(UUID ownerId, String category);
    // Nouvelles méthodes de lecture
    Mono<List<Map<String, Object>>> listRemoteZones(String category); // all, circles, polygons
    Mono<Map<String, Object>> getRemoteZoneDetails(String type, UUID id);
    Mono<Map<String, Object>> fetchRemoteAlerts(int page, int size);
    
    Mono<String> getSystemToken();
    Flux<Map<String, Object>> getZonesByManager(UUID managerId, String category);
    Mono<Void> registerVehicleAndAssignToZone(Vehicle vehicle, UUID zoneId, String zoneType);
    // --- NOUVELLES MÉTHODES ---
    
    /**
     * Crée le véhicule dans le système Geofence et retourne son ID interne.
     */
    Mono<String> registerRemoteVehicle(Vehicle vehicle);

    /**
     * Assigne un véhicule à une zone en utilisant son ID distant.
     */
    Mono<Void> addVehicleToZone(String remoteVehicleId, UUID zoneId, String zoneType);
    
}