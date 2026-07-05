package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.domain.ports.in.ManageMaintenancePlanUseCase;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.MaintenanceAlertResponse;
import com.yowyob.fleet.infrastructure.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/preventive-maintenance/alerts")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_PREVENTIVE_ALERTS)
@SecurityRequirement(name = "bearerAuth")
public class MaintenanceAlertController {

    private final ManageMaintenancePlanUseCase useCase;

    private UUID getUserId(Authentication auth) {
        return ((AuthPort.UserDetail) auth.getPrincipal()).id();
    }

    @GetMapping
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Toutes mes alertes actives",
               description = "Retourne toutes les alertes non résolues (UPCOMING, DUE, OVERDUE), triées par urgence.")
    public Flux<MaintenanceAlertResponse> getActive(Authentication auth) {
        return useCase.getActiveAlerts(getUserId(auth)).map(MaintenanceAlertResponse::from);
    }

    @GetMapping("/urgent")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Alertes urgentes",
               description = "Retourne uniquement les alertes DUE et OVERDUE. Utilisé pour le badge rouge du dashboard.")
    public Flux<MaintenanceAlertResponse> getUrgent(Authentication auth) {
        return useCase.getUrgentAlerts(getUserId(auth)).map(MaintenanceAlertResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Détail d'une alerte")
    public Mono<MaintenanceAlertResponse> getById(
            @Parameter(description = "ID de l'alerte") @PathVariable UUID id) {
        return useCase.getAlertById(id).map(MaintenanceAlertResponse::from);
    }

    @GetMapping("/vehicle/{vehicleId}")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Alertes d'un véhicule",
               description = "Historique complet des alertes (actives et résolues) pour un véhicule.")
    public Flux<MaintenanceAlertResponse> getByVehicle(
            @Parameter(description = "ID du véhicule") @PathVariable UUID vehicleId) {
        return useCase.getAlertsByVehicle(vehicleId).map(MaintenanceAlertResponse::from);
    }

    @GetMapping("/fleet/{fleetId}")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Alertes actives d'une flotte")
    public Flux<MaintenanceAlertResponse> getByFleet(
            @Parameter(description = "ID de la flotte") @PathVariable UUID fleetId) {
        return useCase.getAlertsByFleet(fleetId).map(MaintenanceAlertResponse::from);
    }

    @PatchMapping("/{id}/resolve")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Résoudre une alerte",
               description = "Marque l'alerte comme résolue en la liant à la Maintenance effectuée (ID obligatoire).")
    public Mono<MaintenanceAlertResponse> resolve(
            @Parameter(description = "ID de l'alerte") @PathVariable UUID id,
            @Parameter(description = "ID de la Maintenance effectuée")
            @RequestParam UUID maintenanceId) {
        return useCase.resolveAlert(id, maintenanceId).map(MaintenanceAlertResponse::from);
    }

    @PostMapping("/evaluate/vehicle/{vehicleId}")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Évaluer les plans d'un véhicule",
               description = "Force l'évaluation de tous les plans actifs pour un véhicule. Crée ou met à jour les alertes correspondantes.")
    public Flux<MaintenanceAlertResponse> evaluateVehicle(
            @Parameter(description = "ID du véhicule") @PathVariable UUID vehicleId) {
        return useCase.evaluatePlansForVehicle(vehicleId).map(MaintenanceAlertResponse::from);
    }

    @PostMapping("/evaluate/all")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Évaluer tous les plans",
               description = "Force l'évaluation de tous les plans de tous les véhicules du manager. Équivaut à une exécution manuelle du job planifié.")
    public Flux<MaintenanceAlertResponse> evaluateAll(Authentication auth) {
        return useCase.evaluateAllPlans(getUserId(auth)).map(MaintenanceAlertResponse::from);
    }
}
