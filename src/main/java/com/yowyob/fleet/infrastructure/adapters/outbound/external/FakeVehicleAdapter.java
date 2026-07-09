package com.yowyob.fleet.infrastructure.adapters.outbound.external;

import com.yowyob.fleet.domain.model.Vehicle;
import com.yowyob.fleet.domain.ports.out.ExternalVehiclePort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.VehicleRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simulateur du service externe Véhicule.
 * Activé par : application.external.vehicle-mode=fake
 */
@Slf4j
public class FakeVehicleAdapter implements ExternalVehiclePort {

    private final Map<UUID, Vehicle> store = new ConcurrentHashMap<>();

    @Override
    public Mono<Vehicle> getExternalVehicleInfo(UUID vehicleId, String token) {
        log.info("🛠 [FAKE VEHICLE] getExternalVehicleInfo: id={}", vehicleId);
        // Ne jamais écraser le cache local demo/seed : le store fake ne sert
        // qu'aux véhicules créés via createRemoteVehicle dans cette JVM.
        Vehicle cached = store.get(vehicleId);
        if (cached == null || cached.licensePlate() == null || cached.licensePlate().isBlank()) {
            return Mono.empty();
        }
        return Mono.just(cached);
    }

    @Override
    public Mono<Vehicle> createRemoteVehicle(VehicleRequest request, String token,
            String brandLabel, String modelLabel, String fuelLabel,
            String transLabel, String colorLabel, VehicleRemoteContext context) {
        return createRemoteVehicle(request, token, brandLabel, modelLabel, fuelLabel, transLabel, colorLabel);
    }

    @Override
    public Mono<Vehicle> updateRemoteVehicle(UUID vehicleId, VehicleRequest request, String token,
            String brandLabel, String modelLabel, String fuelLabel,
            String transLabel, String colorLabel, VehicleRemoteContext context) {
        return updateRemoteVehicle(vehicleId, request, token, brandLabel, modelLabel, fuelLabel, transLabel, colorLabel);
    }

    @Override
    public Mono<Vehicle> createRemoteVehicle(VehicleRequest request, String token,
            String brandLabel, String modelLabel, String fuelLabel,
            String transLabel, String colorLabel) {
        log.info("🛠 [FAKE VEHICLE] createRemoteVehicle: plate={}", request.licensePlate());
        UUID id = UUID.randomUUID();
        Vehicle created = new Vehicle(
                id,
                request.fleetId(),
                null,
                null,
                request.vehicleTypeId(),
                request.licensePlate(),
                request.vehicleSerialNumber() != null ? request.vehicleSerialNumber() : "FAKE-VIN-" + id.toString().substring(0, 8),
                brandLabel,
                modelLabel,
                request.manufacturingYear(),
                transLabel,
                fuelLabel,
                request.tankCapacity(),
                request.totalSeatNumber(),
                request.averageFuelConsumption(),
                colorLabel,
                "AVAILABLE",
                request.photoUrl(),
                request.serialNumberPhotoUrl(),
                request.registrationPhotoUrl(),
                Collections.emptyList(),
                null,
                null,
                null,
                null,
                id
        );
        store.put(id, created);
        return Mono.just(created);
    }

    @Override
    public Mono<Vehicle> updateRemoteVehicle(UUID vehicleId, VehicleRequest request, String token,
            String brandLabel, String modelLabel, String fuelLabel,
            String transLabel, String colorLabel) {
        log.info("🛠 [FAKE VEHICLE] updateRemoteVehicle: id={}", vehicleId);
        Vehicle existing = store.get(vehicleId);
        Vehicle updated = new Vehicle(
                vehicleId,
                request.fleetId() != null ? request.fleetId() : (existing != null ? existing.fleetId() : null),
                existing != null ? existing.managerId() : null,
                existing != null ? existing.currentDriverId() : null,
                request.vehicleTypeId() != null ? request.vehicleTypeId() : (existing != null ? existing.vehicleTypeId() : null),
                request.licensePlate(),
                request.vehicleSerialNumber() != null ? request.vehicleSerialNumber()
                        : (existing != null ? existing.vehicleSerialNumber() : null),
                brandLabel,
                modelLabel,
                request.manufacturingYear() != null ? request.manufacturingYear()
                        : (existing != null ? existing.manufacturingYear() : null),
                transLabel,
                fuelLabel,
                request.tankCapacity() != null ? request.tankCapacity()
                        : (existing != null ? existing.tankCapacity() : null),
                request.totalSeatNumber() != null ? request.totalSeatNumber()
                        : (existing != null ? existing.totalSeatNumber() : null),
                request.averageFuelConsumption() != null ? request.averageFuelConsumption()
                        : (existing != null ? existing.averageFuelConsumption() : null),
                colorLabel,
                existing != null ? existing.status() : "AVAILABLE",
                request.photoUrl() != null ? request.photoUrl() : (existing != null ? existing.photoUrl() : null),
                request.serialNumberPhotoUrl(),
                request.registrationPhotoUrl(),
                existing != null ? existing.illustrationImages() : List.of(),
                existing != null ? existing.financialParameters() : null,
                existing != null ? existing.maintenanceParameters() : null,
                existing != null ? existing.operationalParameters() : null,
                existing != null ? existing.geofenceRemoteId() : null,
                existing != null && existing.kernelResourceId() != null ? existing.kernelResourceId() : vehicleId
        );
        store.put(vehicleId, updated);
        return Mono.just(updated);
    }

    @Override
    public Mono<Vehicle> patchRemoteVehicle(UUID vehicleId, Map<String, Object> updates, String token) {
        log.info("🛠 [FAKE VEHICLE] patchRemoteVehicle: id={} keys={}", vehicleId, updates.keySet());
        Vehicle existing = store.get(vehicleId);
        // Véhicule seed / local uniquement : laisser VehicleService patcher le cache local.
        if (existing == null) {
            return Mono.empty();
        }
        Vehicle patched = new Vehicle(
                vehicleId,
                existing.fleetId(),
                existing.managerId(),
                existing.currentDriverId(),
                existing.vehicleTypeId(),
                updates.containsKey("licensePlate") ? str(updates, "licensePlate") : existing.licensePlate(),
                updates.containsKey("vehicleSerialNumber") ? str(updates, "vehicleSerialNumber") : existing.vehicleSerialNumber(),
                updates.containsKey("brand") ? str(updates, "brand") : existing.brand(),
                updates.containsKey("model") ? str(updates, "model") : existing.model(),
                updates.containsKey("manufacturingYear") ? intVal(updates, "manufacturingYear") : existing.manufacturingYear(),
                updates.containsKey("transmissionType") ? str(updates, "transmissionType") : existing.transmissionType(),
                updates.containsKey("fuelType") ? str(updates, "fuelType") : existing.fuelType(),
                updates.containsKey("tankCapacity") ? dbl(updates, "tankCapacity") : existing.tankCapacity(),
                updates.containsKey("totalSeatNumber") ? intVal(updates, "totalSeatNumber") : existing.totalSeatNumber(),
                updates.containsKey("averageFuelConsumption") ? dbl(updates, "averageFuelConsumption") : existing.averageFuelConsumption(),
                updates.containsKey("color") ? str(updates, "color") : existing.color(),
                updates.containsKey("status") ? str(updates, "status") : existing.status(),
                updates.containsKey("photoUrl") ? str(updates, "photoUrl") : existing.photoUrl(),
                existing.serialNumberPhotoUrl(),
                existing.registrationPhotoUrl(),
                existing.illustrationImages(),
                existing.financialParameters(),
                existing.maintenanceParameters(),
                existing.operationalParameters(),
                existing.geofenceRemoteId(),
                existing.kernelResourceId()
        );
        store.put(vehicleId, patched);
        return Mono.just(patched);
    }

    @Override
    public Mono<Void> deleteRemoteVehicle(UUID vehicleId, String token) {
        log.info("🛠 [FAKE VEHICLE] deleteRemoteVehicle: id={}", vehicleId);
        store.remove(vehicleId);
        return Mono.empty();
    }

    @Override
    public Mono<Void> uploadDocument(UUID vehicleId, String docType, FilePart file, String token) {
        log.info("🛠 [FAKE VEHICLE] uploadDocument: vehicle={} docType={}", vehicleId, docType);
        return Mono.empty();
    }

    @Override
    public Mono<Void> deleteDocument(UUID vehicleId, String docType, String token) {
        log.info("🛠 [FAKE VEHICLE] deleteDocument: vehicle={} docType={}", vehicleId, docType);
        return Mono.empty();
    }

    @Override
    public Mono<String> addImage(UUID vehicleId, FilePart file, String token) {
        log.info("🛠 [FAKE VEHICLE] addImage: vehicle={}", vehicleId);
        return Mono.just("/fake-images/" + UUID.randomUUID() + ".jpg");
    }

    @Override
    public Flux<String> getImages(UUID vehicleId, String token) {
        log.info("🛠 [FAKE VEHICLE] getImages: vehicle={}", vehicleId);
        return Flux.empty();
    }

    @Override
    public Mono<Void> deleteImage(String imageId, String token) {
        log.info("🛠 [FAKE VEHICLE] deleteImage: id={}", imageId);
        return Mono.empty();
    }

    @Override
    public Flux<Map<String, Object>> getReferenceData(String resource, String token) {
        log.info("🛠 [FAKE VEHICLE] getReferenceData: resource={}", resource);
        return Flux.empty();
    }

    @Override
    public Mono<Void> assignDriverRemote(UUID vehicleId, UUID driverId, String token) {
        log.info("🛠 [FAKE VEHICLE] assignDriverRemote: vehicle={} driver={}", vehicleId, driverId);
        return Mono.empty();
    }

    private static String str(Map<String, Object> updates, String key) {
        Object v = updates.get(key);
        return v == null ? null : v.toString();
    }

    private static Integer intVal(Map<String, Object> updates, String key) {
        Object v = updates.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return null; }
    }

    private static Double dbl(Map<String, Object> updates, String key) {
        Object v = updates.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (NumberFormatException e) { return null; }
    }
}
