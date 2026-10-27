package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.domain.ports.in.ManageMaintenanceUseCase;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.MaintenanceRequest;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.MaintenanceResponse;
import com.yowyob.fleet.infrastructure.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/operations/maintenances")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_OPS_MAINTENANCE)
@SecurityRequirement(name = "bearerAuth")
public class MaintenanceController {

    private final ManageMaintenanceUseCase maintenanceUseCase;
    private final com.yowyob.fleet.application.service.ResourceAccessService resourceAccess;

    private UUID getUserId(Authentication auth) {
        return ((AuthPort.UserDetail) auth.getPrincipal()).id();
    }

    // ── CRÉATION ─────────────────────────────────────────────────────────────

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_DRIVER')")
    @Operation(summary = "Déclarer une maintenance",
               description = "Enregistre une intervention technique sur un véhicule. Notifie automatiquement le Fleet Manager.")
    public Mono<MaintenanceResponse> create(
            @Valid @RequestBody MaintenanceRequest request,
            Authentication auth) {

        ManageMaintenanceUseCase.CreateMaintenanceCommand cmd =
                new ManageMaintenanceUseCase.CreateMaintenanceCommand(
                        request.subject(),
                        request.cost(),
                        request.report(),
                        request.longitude(),
                        request.latitude(),
                        request.locationName(),
                        request.vehicleId(),
                        request.driverId()
                );
        UUID actor = getUserId(auth);
        // Un chauffeur ne déclare que pour le véhicule qu'il conduit ; un gestionnaire,
        // pour un véhicule de son périmètre.
        Mono<Void> scope = com.yowyob.fleet.infrastructure.config.security.AuthPrincipal.isFleetDriver(auth)
                ? resourceAccess.assertDriverOperatesVehicle(actor, request.vehicleId())
                : resourceAccess.assertVehicleAccess(actor, request.vehicleId());
        return scope.then(maintenanceUseCase.createMaintenance(cmd)).map(MaintenanceResponse::from);
    }

    // ── LECTURE ───────────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Lister mes maintenances",
               description = "Retourne toutes les maintenances des véhicules gérés par le manager connecté.")
    public Flux<MaintenanceResponse> listAll(Authentication auth) {
        return maintenanceUseCase.getAllByManager(getUserId(auth)).map(MaintenanceResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_DRIVER')")
    @Operation(summary = "Détail d'une maintenance")
    public Mono<MaintenanceResponse> getById(
            @Parameter(description = "ID de la maintenance") @PathVariable UUID id,
            Authentication auth) {
        return resourceAccess.assertMaintenanceAccess(getUserId(auth), id)
                .then(maintenanceUseCase.getById(id)).map(MaintenanceResponse::from);
    }

    @GetMapping("/vehicle/{vehicleId}")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Maintenances d'un véhicule",
               description = "Retourne l'historique complet des maintenances pour un véhicule donné.")
    public Flux<MaintenanceResponse> getByVehicle(
            @Parameter(description = "ID du véhicule") @PathVariable UUID vehicleId,
            Authentication auth) {
        return resourceAccess.assertVehicleAccess(getUserId(auth), vehicleId)
                .thenMany(maintenanceUseCase.getByVehicleId(vehicleId)).map(MaintenanceResponse::from);
    }

    @GetMapping("/driver/{driverId}")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Maintenances d'un chauffeur",
               description = "Retourne toutes les maintenances impliquant un chauffeur donné.")
    public Flux<MaintenanceResponse> getByDriver(
            @Parameter(description = "ID du chauffeur") @PathVariable UUID driverId,
            Authentication auth) {
        return resourceAccess.assertDriverAccess(getUserId(auth), driverId)
                .thenMany(maintenanceUseCase.getByDriverId(driverId)).map(MaintenanceResponse::from);
    }

    @GetMapping("/range")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Maintenances par plage de dates")
    public Flux<MaintenanceResponse> getByDateRange(
            @Parameter(description = "Date de début (ISO 8601)", example = "2026-01-01T00:00:00")
            @RequestParam LocalDateTime start,
            @Parameter(description = "Date de fin (ISO 8601)", example = "2026-12-31T23:59:59")
            @RequestParam LocalDateTime end,
            Authentication auth) {
        return maintenanceUseCase.getByDateRange(start, end, getUserId(auth)).map(MaintenanceResponse::from);
    }

    // ── MISE À JOUR ───────────────────────────────────────────────────────────

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Mettre à jour une maintenance",
               description = "Met à jour le rapport, le coût ou la localisation d'une maintenance existante.")
    public Mono<MaintenanceResponse> update(
            @Parameter(description = "ID de la maintenance") @PathVariable UUID id,
            @Valid @RequestBody MaintenanceRequest request,
            Authentication auth) {

        ManageMaintenanceUseCase.UpdateMaintenanceCommand cmd =
                new ManageMaintenanceUseCase.UpdateMaintenanceCommand(
                        id,
                        request.subject(),
                        request.cost(),
                        request.report(),
                        request.longitude(),
                        request.latitude(),
                        request.locationName()
                );
        return resourceAccess.assertMaintenanceAccess(getUserId(auth), id)
                .then(maintenanceUseCase.update(cmd)).map(MaintenanceResponse::from);
    }

    // ── SUPPRESSION ───────────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Supprimer une maintenance")
    public Mono<Void> delete(
            @Parameter(description = "ID de la maintenance") @PathVariable UUID id,
            Authentication auth) {
        return resourceAccess.assertMaintenanceAccess(getUserId(auth), id)
                .then(maintenanceUseCase.delete(id));
    }
}
