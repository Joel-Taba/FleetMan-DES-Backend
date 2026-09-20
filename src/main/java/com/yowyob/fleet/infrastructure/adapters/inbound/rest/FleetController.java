package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.domain.model.Driver;
import com.yowyob.fleet.domain.model.Vehicle;
import com.yowyob.fleet.domain.ports.in.ManageFleetUseCase;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.*;
import com.yowyob.fleet.infrastructure.config.OpenApiConfig;
import com.yowyob.fleet.infrastructure.config.security.AuthPrincipal;
import com.yowyob.fleet.infrastructure.mappers.FleetMapper;
import io.swagger.v3.oas.annotations.Operation;
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
@RequestMapping("/api/v1/fleets")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
public class FleetController {

    private final ManageFleetUseCase fleetUseCase;
    private final FleetMapper mapper;

    /**
     * Helper pour extraire l'ID du Fleet Manager connecté
     */
    private UUID getManagerId(Authentication auth) {
        return AuthPrincipal.userId(auth);
    }

    // ========================================================================
    // --- 10a. FLEETS | ADMINISTRATION (CRUD & STATS) ---
    // ========================================================================

    @Tag(name = OpenApiConfig.TAG_FLEETS)
    @GetMapping
    @Operation(summary = "Lister les flottes", description = "Manager : ses flottes. Admin : toutes les flottes.")
    public Flux<FleetResponse> getMyFleets(Authentication auth) {
        boolean isAdmin = AuthPrincipal.isFleetAdmin(auth);
        return fleetUseCase.getFleets(getManagerId(auth), isAdmin)
                .map(mapper::toResponse);
    }

    @Tag(name = OpenApiConfig.TAG_FLEETS)
    @GetMapping("/{id}")
    @Operation(summary = "Détails d'une flotte")
    public Mono<FleetResponse> getById(@PathVariable UUID id, Authentication auth) {
        boolean isAdmin = AuthPrincipal.isFleetAdmin(auth);
        return fleetUseCase.getFleetById(id, getManagerId(auth), isAdmin)
                .map(mapper::toResponse);
    }

    @Tag(name = OpenApiConfig.TAG_FLEETS)
    @GetMapping("/{id}/stats")
    @Operation(summary = "KPIs de la flotte", description = "Km totaux, distribution des statuts véhicules et nombre de chauffeurs.")
    public Mono<FleetStatsResponse> getStats(@PathVariable UUID id, Authentication auth) {
        boolean isAdmin = AuthPrincipal.isFleetAdmin(auth);
        return fleetUseCase.getFleetStatistics(id, getManagerId(auth), isAdmin);
    }

    // Création / modification / suppression de flotte : désormais réservées à
    // l'administrateur (voir AdminFleetController) — le gestionnaire ne fait
    // plus que consulter le détail des flottes qui lui ont été assignées.

    // ========================================================================
    // --- 10b. FLEETS | MES VÉHICULES (GESTION DU PARC) ---
    // ========================================================================

    @Tag(name =OpenApiConfig.TAG_FLEETS_VHC)
    @GetMapping("/{id}/vehicles")
    @Operation(summary = "Lister les véhicules de la flotte")
    public Flux<Vehicle> getVehicles(@PathVariable UUID id, Authentication auth) {
        return fleetUseCase.getFleetVehicles(id, getManagerId(auth));
    }

    @Tag(name =OpenApiConfig.TAG_FLEETS_VHC)
    @PostMapping("/{id}/vehicles")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Assigner un véhicule existant", description = "Lie un véhicule à cette flotte et synchronise le Geofencing.")
    public Mono<Void> assignVehicle(@PathVariable UUID id, @Valid @RequestBody FleetAssignVehicleRequest request, Authentication auth) {
        return fleetUseCase.assignVehicle(id, request.vehicleId(), getManagerId(auth));
    }

    @Tag(name =OpenApiConfig.TAG_FLEETS_VHC)
    @DeleteMapping("/{id}/vehicles/{vehicleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Retirer un véhicule de la flotte")
    public Mono<Void> detachVehicle(@PathVariable UUID id, @PathVariable UUID vehicleId, Authentication auth) {
        return fleetUseCase.detachVehicle(id, vehicleId, getManagerId(auth));
    }

    // ========================================================================
    // --- 10c. FLEETS | MES CHAUFFEURS (HUMAN RESOURCES) ---
    // ========================================================================

    @Tag(name =OpenApiConfig.TAG_FLEETS_DRV)
    @GetMapping("/{id}/drivers")
    @Operation(summary = "Lister les chauffeurs de la flotte")
    public Flux<Driver> getDrivers(@PathVariable UUID id, Authentication auth) {
        return fleetUseCase.getFleetDrivers(id, getManagerId(auth));
    }

    @Tag(name =OpenApiConfig.TAG_FLEETS_DRV)
    @PostMapping("/{id}/drivers")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Recruter un chauffeur existant", description = "Recherche par email/username et l'ajoute à la flotte.")
    public Mono<Void> recruit(@PathVariable UUID id, @Valid @RequestBody RecruitDriverRequest request, Authentication auth) {
        return fleetUseCase.recruitDriver(id, request.identifier(), getManagerId(auth));
    }

    @Tag(name =OpenApiConfig.TAG_FLEETS_DRV)
    @PostMapping("/{id}/drivers/register")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Créer et intégrer un nouveau chauffeur", description = "Crée le compte Auth + Profil local dans cette flotte.")
    public Mono<Driver> registerInFleet(@PathVariable UUID id, @Valid @RequestBody DriverRegistrationRequest request, Authentication auth) {
        return fleetUseCase.registerDriverInFleet(id, request, getManagerId(auth));
    }

    @Tag(name =OpenApiConfig.TAG_FLEETS_DRV)
    @DeleteMapping("/{id}/drivers/{driverId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Retirer un chauffeur de la flotte")
    public Mono<Void> detachDriver(@PathVariable UUID id, @PathVariable UUID driverId, Authentication auth) {
        return fleetUseCase.detachDriver(id, driverId, getManagerId(auth));
    }
}