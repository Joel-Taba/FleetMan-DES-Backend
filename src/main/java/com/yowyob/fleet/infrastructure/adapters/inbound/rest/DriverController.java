package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.domain.model.Driver;
import com.yowyob.fleet.domain.ports.in.AuthUseCase;
import com.yowyob.fleet.domain.ports.in.ManageDriverUseCase;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.DriverRegistrationRequest;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.UpdateDriverRequest;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.DriverResponse;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.RecruitDriverRequest;
import com.yowyob.fleet.infrastructure.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_DRIVERS)
@SecurityRequirement(name = "bearerAuth")
public class DriverController {

    private final ManageDriverUseCase driverUseCase;

    private UUID getUserId(Authentication auth) {
        return ((AuthPort.UserDetail) auth.getPrincipal()).id();
    }

    private String getToken(String header) {
        return header.startsWith("Bearer ") ? header.substring(7) : header;
    }

    @PostMapping(value = "/fleets/{fleetId}/drivers/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Créer un nouveau Chauffeur", description = "Crée le compte Auth + Profil local + Photo de profil.")
    public Mono<Driver> register(
            @PathVariable UUID fleetId,
            @RequestPart("user") Part userPart,
            @RequestPart(value = "file", required = false) Part filePart,
            Authentication auth) {
        return DataBufferUtils.join(userPart.content())
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                })
                .flatMap(json -> {
                    try {
                        DriverRegistrationRequest request = new com.fasterxml.jackson.databind.ObjectMapper()
                                .readValue(json, DriverRegistrationRequest.class);
                        return Mono.just(request);
                    } catch (Exception e) {
                        return Mono.error(new IllegalArgumentException(
                                "Format de données utilisateur invalide dans le champ user : " + e.getMessage()));
                    }
                })
                .flatMap(request -> processFile(filePart)
                        .flatMap(photo -> driverUseCase.registerDriverWithPhoto(fleetId, request, getUserId(auth),
                                photo))
                        .switchIfEmpty(Mono.defer(
                                () -> driverUseCase.registerDriverWithPhoto(fleetId, request, getUserId(auth), null))));
    }

    @GetMapping("/drivers")
    @Operation(summary = "Lister les chauffeurs du service", description = "Filtres optionnels par flotte ou état d'assignation.")
    public Flux<DriverResponse> list(
            @RequestParam(required = false) UUID fleetId,
            @RequestParam(required = false) Boolean isAssigned,
            Authentication auth) {
        return driverUseCase.getDriversEnriched(fleetId, isAssigned, getUserId(auth));
    }

    @GetMapping("/drivers/{userId}")
    @Operation(summary = "Profil détaillé d'un chauffeur")
    public Mono<DriverResponse> getById(@PathVariable UUID userId) {
        return driverUseCase.getDriverEnriched(userId);
    }

    @PutMapping("/drivers/{userId}")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Mettre à jour les informations d'un chauffeur")
    public Mono<Driver> update(
            @PathVariable UUID userId,
            @RequestBody UpdateDriverRequest request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
            Authentication auth) {
        return driverUseCase.updateDriver(userId, request, getUserId(auth), getToken(authHeader));
    }

    @GetMapping("/drivers/search")
    @Operation(summary = "Rechercher un chauffeur par email ou username")
    public Mono<Driver> search(@RequestParam String identifier) {
        return driverUseCase.searchDriver(identifier);
    }

    @PostMapping("/drivers/{userId}/assign-vehicle")
    public Mono<Void> assign(@PathVariable UUID userId, @RequestBody VehicleAssignRequest req,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader, Authentication auth) {
        return driverUseCase.assignVehicle(userId, req.vehicleId(), getUserId(auth), getToken(authHeader));
    }

    @PostMapping("/drivers/{userId}/unassign-vehicle")
    public Mono<Void> unassign(
            @PathVariable UUID userId,
            @RequestParam(required = false) UUID vehicleId,
            Authentication auth) {
        if (vehicleId != null) {
            return driverUseCase.unassignSpecificVehicle(userId, vehicleId, getUserId(auth));
        }
        return driverUseCase.unassignVehicle(userId, getUserId(auth));
    }

    private Mono<AuthUseCase.FileContent> processFile(Part fp) {
        if (fp == null)
            return Mono.empty();
        return DataBufferUtils.join(fp.content()).map(db -> {
            byte[] bytes = new byte[db.readableByteCount()];
            db.read(bytes);
            DataBufferUtils.release(db);
            return new AuthUseCase.FileContent(
                    (fp instanceof FilePart f) ? f.filename() : "driver_pic",
                    fp.headers().getContentType().toString(), bytes);
        });
    }

    public record VehicleAssignRequest(UUID vehicleId) {
    }
}