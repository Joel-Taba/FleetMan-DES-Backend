package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.domain.ports.in.ManageMaintenancePlanUseCase;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.*;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/preventive-maintenance/plans")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_PREVENTIVE_PLANS)
@SecurityRequirement(name = "bearerAuth")
public class MaintenancePlanController {

    private final ManageMaintenancePlanUseCase useCase;

    private UUID getUserId(Authentication auth) {
        return ((AuthPort.UserDetail) auth.getPrincipal()).id();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Créer un plan de maintenance préventive",
               description = "Crée un plan kilométrique et/ou temporel pour une flotte (tous véhicules) ou un véhicule spécifique. Génère immédiatement les alertes pour les véhicules déjà en zone de préalerte.")
    public Mono<MaintenancePlanResponse> create(
            @Valid @RequestBody MaintenancePlanRequest request,
            Authentication auth) {

        ManageMaintenancePlanUseCase.CreatePlanCommand cmd =
                new ManageMaintenancePlanUseCase.CreatePlanCommand(
                        request.maintenanceType(), request.scope(),
                        request.fleetId(), request.vehicleId(),
                        getUserId(auth),
                        request.label(), request.description(),
                        request.intervalKm(), request.intervalDays(),
                        request.preAlertKm(), request.preAlertDays()
                );
        return useCase.createPlan(cmd).map(MaintenancePlanResponse::from);
    }

    @GetMapping
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Lister mes plans",
               description = "Retourne tous les plans de maintenance (flotte et véhicules) du manager connecté.")
    public Flux<MaintenancePlanResponse> listAll(Authentication auth) {
        return useCase.getPlansByManager(getUserId(auth)).map(MaintenancePlanResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Détail d'un plan")
    public Mono<MaintenancePlanResponse> getById(
            @Parameter(description = "ID du plan") @PathVariable UUID id) {
        return useCase.getPlanById(id).map(MaintenancePlanResponse::from);
    }

    @GetMapping("/fleet/{fleetId}")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Plans d'une flotte",
               description = "Retourne tous les plans actifs et inactifs d'une flotte.")
    public Flux<MaintenancePlanResponse> getByFleet(
            @Parameter(description = "ID de la flotte") @PathVariable UUID fleetId) {
        return useCase.getPlansByFleet(fleetId).map(MaintenancePlanResponse::from);
    }

    @GetMapping("/vehicle/{vehicleId}")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Plans spécifiques d'un véhicule",
               description = "Retourne les plans de scope VEHICLE pour un véhicule donné (surcharges individuelles).")
    public Flux<MaintenancePlanResponse> getByVehicle(
            @Parameter(description = "ID du véhicule") @PathVariable UUID vehicleId) {
        return useCase.getPlansByVehicle(vehicleId).map(MaintenancePlanResponse::from);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Mettre à jour un plan")
    public Mono<MaintenancePlanResponse> update(
            @Parameter(description = "ID du plan") @PathVariable UUID id,
            @RequestBody MaintenancePlanUpdateRequest request) {

        ManageMaintenancePlanUseCase.UpdatePlanCommand cmd =
                new ManageMaintenancePlanUseCase.UpdatePlanCommand(
                        id, request.label(), request.description(),
                        request.intervalKm(), request.intervalDays(),
                        request.preAlertKm(), request.preAlertDays(),
                        request.active()
                );
        return useCase.updatePlan(cmd).map(MaintenancePlanResponse::from);
    }

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Activer / désactiver un plan",
               description = "Active ou désactive un plan sans le supprimer. Les alertes existantes ne sont pas affectées.")
    public Mono<MaintenancePlanResponse> toggle(
            @Parameter(description = "ID du plan") @PathVariable UUID id,
            @Parameter(description = "true = activer, false = désactiver")
            @RequestParam boolean active) {
        return useCase.togglePlan(id, active).map(MaintenancePlanResponse::from);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Supprimer un plan")
    public Mono<Void> delete(
            @Parameter(description = "ID du plan") @PathVariable UUID id) {
        return useCase.deletePlan(id);
    }
}
