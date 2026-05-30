package com.yowyob.fleet.infrastructure.adapters.outbound.external.client;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.VehicleOwnershipRequest;

import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.VehicleRegistrationRequest;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.dto.VehicleExternalResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader; 
import org.springframework.web.service.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.Map;
import java.util.UUID;

@HttpExchange("/vehicles")
public interface VehicleApiClient {

    @GetExchange("/{id}")
    Mono<VehicleExternalResponse> getById(@PathVariable UUID id, @RequestHeader("Authorization") String token);

    @PostExchange("/simplified")
    Mono<VehicleExternalResponse> createSimplified(
        @RequestBody VehicleRegistrationRequest request, 
        @RequestHeader("Authorization") String token
    );

    @PutExchange("/{id}")
    Mono<VehicleExternalResponse> updateFull(
        @PathVariable UUID id, 
        @RequestBody VehicleRegistrationRequest request,
        @RequestHeader("Authorization") String token
    );

    @PatchExchange("/{id}")
    Mono<VehicleExternalResponse> updatePartial(
        @PathVariable UUID id, 
        @RequestBody Map<String, Object> updates,
        @RequestHeader("Authorization") String token
    );

    @DeleteExchange("/{id}")
    Mono<Void> delete(@PathVariable UUID id, @RequestHeader("Authorization") String token);

    // Documents
    @PutExchange("/{id}/documents/serial") // CORRECTION: Spec dit PUT pour upload
    Mono<Void> uploadSerialDoc(@PathVariable UUID id, @RequestBody Object multipartData, @RequestHeader("Authorization") String token); // Note: Le type Object sera géré par l'adapter

    @DeleteExchange("/{id}/documents/serial")
    Mono<Void> deleteSerialDoc(@PathVariable UUID id, @RequestHeader("Authorization") String token);

    @PutExchange("/{id}/documents/registration") // Correction PUT selon spec
    Mono<Void> uploadRegistrationDoc(@PathVariable UUID id, @RequestBody Object multipartData, @RequestHeader("Authorization") String token);

    @DeleteExchange("/{id}/documents/registration")
    Mono<Void> deleteRegistrationDoc(@PathVariable UUID id, @RequestHeader("Authorization") String token);

    // Images
    @PostExchange("/{id}/images") // POST pour ajout image
    Mono<Map<String, Object>> addImage(@PathVariable UUID id, @RequestBody Object multipartData, @RequestHeader("Authorization") String token);

    @GetExchange("/{id}/images")
    Flux<Map<String, Object>> getImages(@PathVariable UUID id, @RequestHeader("Authorization") String token);

    @DeleteExchange("/images/{imageId}")
    Mono<Void> deleteImage(@PathVariable String imageId, @RequestHeader("Authorization") String token);

    @GetExchange("/lookup/{resource}")
    Flux<Map<String, Object>> getLookupList(@PathVariable String resource, @RequestHeader("Authorization") String token);

    @PostExchange("/ownerships")
    Mono<Void> createOwnership(@RequestBody VehicleOwnershipRequest request, @RequestHeader("Authorization") String token);
}