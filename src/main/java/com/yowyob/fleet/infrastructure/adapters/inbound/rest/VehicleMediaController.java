package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.domain.model.Vehicle;
import com.yowyob.fleet.domain.ports.in.ManageVehicleMediaUseCase;
import com.yowyob.fleet.infrastructure.config.OpenApiConfig;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vehicles/{vehicleId}/media")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_VHC_MEDIA, description = "Gestion unifiée des médias")
@SecurityRequirement(name = "bearerAuth")
public class VehicleMediaController {

    private final ManageVehicleMediaUseCase mediaUseCase;

    private String extractToken(Authentication auth) {
        return "Bearer " + auth.getCredentials().toString();
    }

    @PutMapping(value = "/vin", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Uploader photo VIN")
    public Mono<Vehicle> uploadVin(@PathVariable UUID vehicleId, @RequestPart("file") FilePart file, Authentication auth) {
        return mediaUseCase.uploadVinPhoto(vehicleId, file, extractToken(auth));
    }

    @PutMapping(value = "/registration", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Uploader photo Carte Grise")
    public Mono<Vehicle> uploadReg(@PathVariable UUID vehicleId, @RequestPart("file") FilePart file, Authentication auth) {
        return mediaUseCase.uploadRegistrationPhoto(vehicleId, file, extractToken(auth));
    }

    @PostMapping(value = "/gallery", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ajouter photo galerie")
    public Mono<Vehicle> addImage(@PathVariable UUID vehicleId, @RequestPart("file") FilePart file, Authentication auth) {
        return mediaUseCase.addIllustrationImage(vehicleId, file, extractToken(auth));
    }

    @DeleteMapping("/gallery/{imageId}")
    @Operation(summary = "Supprimer photo galerie")
    public Mono<Vehicle> deleteImage(@PathVariable UUID vehicleId, @PathVariable UUID imageId, Authentication auth) {
        return mediaUseCase.deleteIllustrationImage(vehicleId, imageId, extractToken(auth));
    }
}