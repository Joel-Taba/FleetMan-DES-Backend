package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.domain.model.FuelRecharge;
import com.yowyob.fleet.domain.ports.in.ManageFuelRechargeUseCase;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.FuelRechargeRequest;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.FuelRechargeResponse;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/operations/fuel-recharges")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_OPS_FUEL)
@SecurityRequirement(name = "bearerAuth")
public class FuelRechargeController {

    private final ManageFuelRechargeUseCase fuelRechargeUseCase;
    private final com.yowyob.fleet.application.service.ResourceAccessService resourceAccess;

    private UUID getUserId(Authentication auth) {
        return ((AuthPort.UserDetail) auth.getPrincipal()).id();
    }

    // ── CRÉATION ─────────────────────────────────────────────────────────────

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('FLEET_DRIVER', 'FLEET_MANAGER')")
    @Operation(summary = "Enregistrer une recharge de carburant",
               description = "Enregistre un plein de carburant et met à jour automatiquement le niveau de carburant du véhicule.")
    public Mono<FuelRechargeResponse> create(
            @Valid @RequestBody FuelRechargeRequest request,
            Authentication auth) {

        ManageFuelRechargeUseCase.CreateFuelRechargeCommand cmd =
                new ManageFuelRechargeUseCase.CreateFuelRechargeCommand(
                        request.quantity(),
                        request.price(),
                        request.longitude(),
                        request.latitude(),
                        normalizeStationName(request.stationName()),
                        request.vehicleId(),
                        request.driverId()
                );
        UUID actor = getUserId(auth);
        // Un chauffeur ne déclare que pour le véhicule qu'il conduit ; un gestionnaire,
        // pour un véhicule de son périmètre.
        Mono<Void> scope = com.yowyob.fleet.infrastructure.config.security.AuthPrincipal.isFleetDriver(auth)
                ? resourceAccess.assertDriverOperatesVehicle(actor, request.vehicleId())
                : resourceAccess.assertVehicleAccess(actor, request.vehicleId());
        return scope.then(fuelRechargeUseCase.createFuelRecharge(cmd)).map(FuelRechargeResponse::from);
    }

    // ── LECTURE ───────────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Lister mes recharges",
               description = "Retourne toutes les recharges des véhicules gérés par le manager connecté.")
    public Flux<FuelRechargeResponse> listAll(Authentication auth) {
        return fuelRechargeUseCase.getAllByManager(getUserId(auth)).map(FuelRechargeResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_DRIVER')")
    @Operation(summary = "Détail d'une recharge")
    public Mono<FuelRechargeResponse> getById(
            @Parameter(description = "ID de la recharge") @PathVariable UUID id,
            Authentication auth) {
        return resourceAccess.assertFuelRechargeAccess(getUserId(auth), id)
                .then(fuelRechargeUseCase.getById(id)).map(FuelRechargeResponse::from);
    }

    @GetMapping("/vehicle/{vehicleId}")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Recharges d'un véhicule",
               description = "Retourne l'historique complet des recharges pour un véhicule donné.")
    public Flux<FuelRechargeResponse> getByVehicle(
            @Parameter(description = "ID du véhicule") @PathVariable UUID vehicleId,
            Authentication auth) {
        return resourceAccess.assertVehicleAccess(getUserId(auth), vehicleId)
                .thenMany(fuelRechargeUseCase.getByVehicleId(vehicleId)).map(FuelRechargeResponse::from);
    }

    @GetMapping("/driver/{driverId}")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Recharges d'un chauffeur")
    public Flux<FuelRechargeResponse> getByDriver(
            @Parameter(description = "ID du chauffeur") @PathVariable UUID driverId,
            Authentication auth) {
        return resourceAccess.assertDriverAccess(getUserId(auth), driverId)
                .thenMany(fuelRechargeUseCase.getByDriverId(driverId)).map(FuelRechargeResponse::from);
    }

    @GetMapping("/range")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Recharges par plage de dates")
    public Flux<FuelRechargeResponse> getByDateRange(
            @Parameter(description = "Date de début (ISO 8601)", example = "2026-01-01T00:00:00")
            @RequestParam LocalDateTime start,
            @Parameter(description = "Date de fin (ISO 8601)", example = "2026-12-31T23:59:59")
            @RequestParam LocalDateTime end,
            Authentication auth) {
        return fuelRechargeUseCase.getByDateRange(start, end, getUserId(auth)).map(FuelRechargeResponse::from);
    }

    // ── KPIs ──────────────────────────────────────────────────────────────────

    @GetMapping("/vehicle/{vehicleId}/stats")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Statistiques carburant d'un véhicule",
               description = "Retourne la consommation totale (litres) et le coût total des recharges pour un véhicule.")
    public Mono<FuelStatsResponse> getVehicleFuelStats(
            @Parameter(description = "ID du véhicule") @PathVariable UUID vehicleId,
            Authentication auth) {

        return resourceAccess.assertVehicleAccess(getUserId(auth), vehicleId).then(Mono.zip(
                fuelRechargeUseCase.getTotalQuantityByVehicleId(vehicleId),
                fuelRechargeUseCase.getTotalCostByVehicleId(vehicleId)
        )).map(tuple -> new FuelStatsResponse(vehicleId, tuple.getT1(), tuple.getT2()));
    }

    // ── MISE À JOUR ───────────────────────────────────────────────────────────

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Mettre à jour une recharge")
    public Mono<FuelRechargeResponse> update(
            @Parameter(description = "ID de la recharge") @PathVariable UUID id,
            @Valid @RequestBody FuelRechargeRequest request,
            Authentication auth) {

        ManageFuelRechargeUseCase.UpdateFuelRechargeCommand cmd =
                new ManageFuelRechargeUseCase.UpdateFuelRechargeCommand(
                        id,
                        request.quantity(),
                        request.price(),
                        request.longitude(),
                        request.latitude(),
                        normalizeStationName(request.stationName()),
                        request.driverId()
                );
        return resourceAccess.assertFuelRechargeAccess(getUserId(auth), id)
                .then(fuelRechargeUseCase.update(cmd)).map(FuelRechargeResponse::from);
    }

    // ── SUPPRESSION ───────────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Supprimer une recharge")
    public Mono<Void> delete(
            @Parameter(description = "ID de la recharge") @PathVariable UUID id,
            Authentication auth) {
        return resourceAccess.assertFuelRechargeAccess(getUserId(auth), id)
                .then(fuelRechargeUseCase.delete(id));
    }

    // ── DTO interne ───────────────────────────────────────────────────────────

    record FuelStatsResponse(
            UUID vehicleId,
            BigDecimal totalQuantityLiters,
            BigDecimal totalCost
    ) {}

    private FuelRecharge.StationName normalizeStationName(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return FuelRecharge.StationName.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return FuelRecharge.StationName.OTHER;
        }
    }
}
