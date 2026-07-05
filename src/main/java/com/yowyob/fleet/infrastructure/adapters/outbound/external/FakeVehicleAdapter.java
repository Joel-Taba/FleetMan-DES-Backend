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

/**
 * Simulateur du service externe Véhicule.
 * Activé par : application.external.vehicle-mode=fake
 */
@Slf4j
public class FakeVehicleAdapter implements ExternalVehiclePort {

    @Override
    public Mono<Vehicle> getExternalVehicleInfo(UUID vehicleId, String token) {
        log.info("🛠 [FAKE VEHICLE] getExternalVehicleInfo: id={}", vehicleId);
        return Mono.empty();
    }

    @Override
    public Mono<Vehicle> createRemoteVehicle(VehicleRequest request, String token,
            String brandLabel, String modelLabel, String fuelLabel,
            String transLabel, String colorLabel) {
        log.info("🛠 [FAKE VEHICLE] createRemoteVehicle: plate={}", request.licensePlate());
        return Mono.empty();
    }

    @Override
    public Mono<Vehicle> updateRemoteVehicle(UUID vehicleId, VehicleRequest request, String token,
            String brandLabel, String modelLabel, String fuelLabel,
            String transLabel, String colorLabel) {
        log.info("🛠 [FAKE VEHICLE] updateRemoteVehicle: id={}", vehicleId);
        return Mono.empty();
    }

    @Override
    public Mono<Vehicle> patchRemoteVehicle(UUID vehicleId, Map<String, Object> updates, String token) {
        log.info("🛠 [FAKE VEHICLE] patchRemoteVehicle: id={}", vehicleId);
        return Mono.empty();
    }

    @Override
    public Mono<Void> deleteRemoteVehicle(UUID vehicleId, String token) {
        log.info("🛠 [FAKE VEHICLE] deleteRemoteVehicle: id={}", vehicleId);
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
}
