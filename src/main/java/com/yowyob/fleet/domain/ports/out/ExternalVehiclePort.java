package com.yowyob.fleet.domain.ports.out;

import com.yowyob.fleet.domain.model.Vehicle;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.VehicleRequest;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

public interface ExternalVehiclePort {

    /** Contexte Kernel optionnel (organisation / agence) pour resource-core. */
    record VehicleRemoteContext(UUID organizationId, UUID agencyId) {
        public static VehicleRemoteContext empty() {
            return new VehicleRemoteContext(null, null);
        }
    }

    Mono<Vehicle> getExternalVehicleInfo(UUID vehicleId, String token);

    default Mono<Vehicle> createRemoteVehicle(
            VehicleRequest request,
            String token,
            String brandLabel,
            String modelLabel,
            String fuelLabel,
            String transLabel,
            String colorLabel) {
        return createRemoteVehicle(request, token, brandLabel, modelLabel, fuelLabel, transLabel, colorLabel,
                VehicleRemoteContext.empty());
    }

    Mono<Vehicle> createRemoteVehicle(
            VehicleRequest request,
            String token,
            String brandLabel,
            String modelLabel,
            String fuelLabel,
            String transLabel,
            String colorLabel,
            VehicleRemoteContext context);

    default Mono<Vehicle> updateRemoteVehicle(
            UUID vehicleId,
            VehicleRequest request,
            String token,
            String brandLabel,
            String modelLabel,
            String fuelLabel,
            String transLabel,
            String colorLabel) {
        return updateRemoteVehicle(vehicleId, request, token, brandLabel, modelLabel, fuelLabel, transLabel, colorLabel,
                VehicleRemoteContext.empty());
    }

    Mono<Vehicle> updateRemoteVehicle(
            UUID vehicleId,
            VehicleRequest request,
            String token,
            String brandLabel,
            String modelLabel,
            String fuelLabel,
            String transLabel,
            String colorLabel,
            VehicleRemoteContext context);

    Mono<Vehicle> patchRemoteVehicle(UUID vehicleId, Map<String, Object> updates, String token);

    Mono<Void> deleteRemoteVehicle(UUID vehicleId, String token);

    Mono<Void> uploadDocument(UUID vehicleId, String docType, FilePart file, String token);

    Mono<Void> deleteDocument(UUID vehicleId, String docType, String token);

    Mono<String> addImage(UUID vehicleId, FilePart file, String token);

    Flux<String> getImages(UUID vehicleId, String token);

    Mono<Void> deleteImage(String imageId, String token);

    Flux<Map<String, Object>> getReferenceData(String resource, String token);

    Mono<Void> assignDriverRemote(UUID vehicleId, UUID driverId, String token);

    default boolean isKernelMode() {
        return false;
    }
}
