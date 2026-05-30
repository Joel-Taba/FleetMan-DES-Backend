package com.yowyob.fleet.infrastructure.adapters.outbound.external;

import com.yowyob.fleet.domain.model.Vehicle;
import com.yowyob.fleet.domain.ports.out.ExternalVehiclePort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.VehicleRequest;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.VehicleRegistrationRequest;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.client.VehicleApiClient;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.dto.VehicleExternalResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class VehicleApiAdapter implements ExternalVehiclePort {

    private final VehicleApiClient apiClient;
    private final WebClient.Builder webClientBuilder;

    @Value("${application.external.vehicle-service-url}")
    private String serviceUrl;

    private String ensureBearer(String token) {
        return (token != null && token.startsWith("Bearer ")) ? token : "Bearer " + token;
    }

    @Override
    public Mono<Vehicle> getExternalVehicleInfo(UUID vehicleId, String token) {
        return apiClient.getById(vehicleId, ensureBearer(token)).map(this::mapToDomain).onErrorResume(e -> Mono.empty());
    }

    @Override
    public Mono<Vehicle> createRemoteVehicle(VehicleRequest req, String token, String brandLabel, String modelLabel, String fuelLabel, String transLabel, String colorLabel) {
        return apiClient.createSimplified(
            translateToRemote(req, brandLabel, modelLabel, fuelLabel, transLabel, colorLabel), 
            ensureBearer(token)
        ).map(this::mapToDomain);
    }
   @Override
    public Mono<Vehicle> updateRemoteVehicle(UUID vehicleId, VehicleRequest req, String token, String brandLabel, String modelLabel, String fuelLabel, String transLabel, String colorLabel) {
        return apiClient.updateFull(
            vehicleId, 
            translateToRemote(req, brandLabel, modelLabel, fuelLabel, transLabel, colorLabel), 
            ensureBearer(token)
        ).map(this::mapToDomain);
    }

    @Override
    public Mono<Vehicle> patchRemoteVehicle(UUID vehicleId, Map<String, Object> updates, String token) {
        Map<String, Object> translated = new HashMap<>();
        updates.forEach((k, v) -> {
            switch (k) {
                case "brand" -> translated.put("makeName", v);
                case "model" -> translated.put("modelName", v);
                case "licensePlate" -> translated.put("registrationNumber", v);
                default -> translated.put(k, v);
            }
        });
        return apiClient.updatePartial(vehicleId, translated, ensureBearer(token)).map(this::mapToDomain);
    }

    @Override
    public Mono<Void> deleteRemoteVehicle(UUID vehicleId, String token) {
        return apiClient.delete(vehicleId, ensureBearer(token));
    }

    @Override
    public Mono<Void> uploadDocument(UUID vehicleId, String docType, FilePart file, String token) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", file);
        return webClientBuilder.build().put().uri(serviceUrl + "/vehicles/" + vehicleId + "/documents/" + docType)
                .header("Authorization", ensureBearer(token)).contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(builder.build()).retrieve().toBodilessEntity().then();
    }

    @Override
    public Mono<Void> deleteDocument(UUID vehicleId, String docType, String token) {
        return docType.equals("serial") ? apiClient.deleteSerialDoc(vehicleId, ensureBearer(token)) : apiClient.deleteRegistrationDoc(vehicleId, ensureBearer(token));
    }

    @Override
    public Mono<String> addImage(UUID vehicleId, FilePart file, String token) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", file);
        return webClientBuilder.build().post().uri(serviceUrl + "/vehicles/" + vehicleId + "/images")
                .header("Authorization", ensureBearer(token)).contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(builder.build()).retrieve().bodyToMono(Map.class)
                .map(resp -> (String) resp.getOrDefault("imagePath", ""));
    }

    @Override
    public Flux<String> getImages(UUID vehicleId, String token) {
        return apiClient.getImages(vehicleId, ensureBearer(token)).map(map -> (String) map.get("imagePath"));
    }

    @Override
    public Mono<Void> deleteImage(String imageId, String token) {
        return apiClient.deleteImage(imageId, ensureBearer(token));
    }

    @Override
    public Flux<Map<String, Object>> getReferenceData(String resource, String token) {
        return apiClient.getLookupList(resource, ensureBearer(token));
    }

    @Override
    public Mono<Void> assignDriverRemote(UUID vehicleId, UUID driverId, String token) { return Mono.empty(); }


    private Vehicle mapToDomain(VehicleExternalResponse ext) {
        // FIX : Ajout du 24ème argument (null pour operationalParameters)
        return new Vehicle(
            ext.vehicleId(), null, null, null, null,
            ext.registrationNumber(), ext.vehicleSerialNumber(), ext.brand(), "Model",
            null, "Transmission", "Fuel", ext.tankCapacity(), ext.totalSeatNumber(),
            ext.averageFuelConsumptionPerKm(), null, "AVAILABLE", ext.vehicleSerialPhoto(), 
            ext.vehicleSerialPhoto(), ext.registrationPhoto(), null, null, null, null, null
        );
    }

     private VehicleRegistrationRequest translateToRemote(VehicleRequest req, String brandLabel, String modelLabel, String fuelLabel, String transLabel, String colorLabel) {
        return new VehicleRegistrationRequest(
            brandLabel,           // makeName
            modelLabel,           // modelName
            transLabel,           // transmissionType
            "Local Manufacturer", // manufacturerName (ou passer tuple.getT2().getLabel())
            "Medium",             // sizeName (ou passer tuple.getT5().getLabel())
            "Standard",           // typeName (ou passer tuple.getT6().getLabel())
            fuelLabel,            // fuelTypeName
            req.vehicleSerialNumber(), 
            req.photoUrl(), 
            null,
            req.licensePlate(), 
            null, 
            req.tankCapacity(), 
            null, 
            req.totalSeatNumber(),
            req.averageFuelConsumption(), 
            null, null, null, 
            req.vehicleTypeId(), 
            brandLabel,
            false, false, false, false, false, false, false, false, false, false, false, false, false
        );
    }

    
}