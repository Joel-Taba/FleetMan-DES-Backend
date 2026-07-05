package com.yowyob.fleet.infrastructure.adapters.outbound.external;

import com.yowyob.fleet.domain.model.GeofenceZone;
import com.yowyob.fleet.domain.model.Vehicle;
import com.yowyob.fleet.domain.ports.out.ExternalGeofencePort;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * Simulateur du service Geofence externe.
 * Activé par : application.external.geofence-mode=fake
 * Toutes les opérations retournent des réponses fictives sans appel HTTP.
 */
@Slf4j
public class FakeGeofenceAdapter implements ExternalGeofencePort {

    private static final String FAKE_TOKEN = "fake-geofence-token";

    @Override
    public Mono<String> getSystemToken() {
        return Mono.just(FAKE_TOKEN);
    }

    @Override
    public Mono<UUID> synchronizeZone(GeofenceZone zone) {
        UUID fakeId = UUID.randomUUID();
        log.info("🛠 [FAKE GEOFENCE] synchronizeZone: {} → fakeId={}", zone.name(), fakeId);
        return Mono.just(fakeId);
    }

    @Override
    public Mono<Void> updateRemoteZone(String type, UUID id, Map<String, Object> updates) {
        log.info("🛠 [FAKE GEOFENCE] updateRemoteZone: type={} id={}", type, id);
        return Mono.empty();
    }

    @Override
    public Mono<Void> deleteRemoteZone(String type, UUID zoneId) {
        log.info("🛠 [FAKE GEOFENCE] deleteRemoteZone: type={} id={}", type, zoneId);
        return Mono.empty();
    }

    @Override
    public Mono<String> checkPointInZone(UUID zoneId, Double lat, Double lng) {
        log.info("🛠 [FAKE GEOFENCE] checkPointInZone: zone={} ({},{})", zoneId, lat, lng);
        return Mono.just("INSIDE");
    }

    @Override
    public Mono<List<Map<String, Object>>> getZonesByOwner(UUID ownerId, String category) {
        log.info("🛠 [FAKE GEOFENCE] getZonesByOwner: owner={}", ownerId);
        return Mono.just(Collections.emptyList());
    }

    @Override
    public Mono<List<Map<String, Object>>> listRemoteZones(String category) {
        log.info("🛠 [FAKE GEOFENCE] listRemoteZones: category={}", category);
        return Mono.just(Collections.emptyList());
    }

    @Override
    public Mono<Map<String, Object>> getRemoteZoneDetails(String type, UUID id) {
        log.info("🛠 [FAKE GEOFENCE] getRemoteZoneDetails: type={} id={}", type, id);
        Map<String, Object> fake = new HashMap<>();
        fake.put("id", id.toString());
        fake.put("title", "Zone Fake");
        fake.put("type", type);
        return Mono.just(fake);
    }

    @Override
    public Mono<Map<String, Object>> fetchRemoteAlerts(int page, int size) {
        log.info("🛠 [FAKE GEOFENCE] fetchRemoteAlerts: page={} size={}", page, size);
        Map<String, Object> result = new HashMap<>();
        result.put("content", Collections.emptyList());
        result.put("totalElements", 0);
        return Mono.just(result);
    }

    @Override
    public Flux<Map<String, Object>> getZonesByManager(UUID managerId, String category) {
        log.info("🛠 [FAKE GEOFENCE] getZonesByManager: manager={}", managerId);
        return Flux.empty();
    }

    @Override
    public Mono<Void> registerVehicleAndAssignToZone(Vehicle vehicle, UUID zoneId, String zoneType) {
        log.info("🛠 [FAKE GEOFENCE] registerVehicleAndAssignToZone: plate={} zone={}", vehicle.licensePlate(), zoneId);
        return Mono.empty();
    }

    @Override
    public Mono<String> registerRemoteVehicle(Vehicle vehicle) {
        String fakeRemoteId = UUID.randomUUID().toString();
        log.info("🛠 [FAKE GEOFENCE] registerRemoteVehicle: plate={} → fakeId={}", vehicle.licensePlate(), fakeRemoteId);
        return Mono.just(fakeRemoteId);
    }

    @Override
    public Mono<Void> addVehicleToZone(String remoteVehicleId, UUID zoneId, String zoneType) {
        log.info("🛠 [FAKE GEOFENCE] addVehicleToZone: vehicle={} zone={}", remoteVehicleId, zoneId);
        return Mono.empty();
    }
}
