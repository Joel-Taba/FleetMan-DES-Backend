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
    private final com.yowyob.fleet.application.service.OperationFleetLookup fleetLookup;
    private final com.yowyob.fleet.application.service.ResourceAccessService resourceAccess;
    private final com.yowyob.fleet.application.service.OperationLookup lookup;

    /** Joint à chaque plein l'état de sa dépense générée (validation du gestionnaire). */
    private Flux<FuelRechargeResponse> withExpenses(Flux<FuelRecharge> recharges) {
        return recharges.collectList().flatMapMany(list -> lookup.autoExpenses(list.stream().map(FuelRecharge::getId).toList())
                .flatMapMany(expenses -> Flux.fromIterable(list)
                        .map(f -> FuelRechargeResponse.from(f, expenses.get(f.getId())))))
                .transform(r -> fleetLookup.attach(r, "fuel_recharges", FuelRechargeResponse::id, FuelRechargeResponse::withFleet));
    }

    private Mono<FuelRechargeResponse> withExpense(Mono<FuelRecharge> recharge) {
        return withExpenses(recharge.flux()).next();
    }

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

        UUID actor = getUserId(auth);
        boolean driver = com.yowyob.fleet.infrastructure.config.security.AuthPrincipal.isFleetDriver(auth);
        // Un chauffeur déclare pour lui-même (le plein lui est attribué, il suit la validation) et
        // seulement pour le véhicule qu'il conduit ; un gestionnaire, pour un véhicule de son périmètre.
        Mono<UUID> driverIdMono = driver
                ? lookup.localUser(actor)
                : Mono.justOrEmpty(request.driverId());
        Mono<Void> scope = driver
                ? resourceAccess.assertDriverOperatesVehicle(actor, request.vehicleId())
                : resourceAccess.assertVehicleAccess(actor, request.vehicleId());
        return scope.then(driverIdMono.map(java.util.Optional::of).defaultIfEmpty(java.util.Optional.empty()))
                .flatMap(driverId -> fuelRechargeUseCase.createFuelRecharge(
                        new ManageFuelRechargeUseCase.CreateFuelRechargeCommand(
                                request.quantity(),
                                request.price(),
                                request.longitude(),
                                request.latitude(),
                                normalizeStationName(request.stationName()),
                                request.vehicleId(),
                                driverId.orElse(null),
                                request.odometerKm(),
                                stationLabel(request.stationName()),
                                request.receiptUrl(),
                                request.receiptName(),
                                request.receiptMime(),
                                actor)))
                .flatMap(saved -> withExpense(Mono.just(saved)));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('FLEET_DRIVER')")
    @Operation(summary = "Mes pleins (chauffeur)",
               description = "Pleins attribués au chauffeur connecté, du plus récent au plus ancien, avec l'état de validation de la dépense.")
    public Flux<FuelRechargeResponse> mine(Authentication auth) {
        return withExpenses(lookup.localUser(getUserId(auth))
                .flatMapMany(fuelRechargeUseCase::getByDriverId)
                .sort(java.util.Comparator.comparing(FuelRecharge::getRechargeDateTime,
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder()))));
    }

    // ── LECTURE ───────────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Lister mes recharges",
               description = "Retourne toutes les recharges des véhicules gérés par le manager connecté.")
    public Flux<FuelRechargeResponse> listAll(Authentication auth) {
        return withExpenses(fuelRechargeUseCase.getAllByManager(getUserId(auth)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_DRIVER')")
    @Operation(summary = "Détail d'une recharge")
    public Mono<FuelRechargeResponse> getById(
            @Parameter(description = "ID de la recharge") @PathVariable UUID id,
            Authentication auth) {
        return resourceAccess.assertFuelRechargeAccess(getUserId(auth), id)
                .then(withExpense(fuelRechargeUseCase.getById(id)));
    }

    @GetMapping("/vehicle/{vehicleId}")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Recharges d'un véhicule",
               description = "Retourne l'historique complet des recharges pour un véhicule donné.")
    public Flux<FuelRechargeResponse> getByVehicle(
            @Parameter(description = "ID du véhicule") @PathVariable UUID vehicleId,
            Authentication auth) {
        return resourceAccess.assertVehicleAccess(getUserId(auth), vehicleId)
                .thenMany(withExpenses(fuelRechargeUseCase.getByVehicleId(vehicleId)));
    }

    @GetMapping("/driver/{driverId}")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Recharges d'un chauffeur")
    public Flux<FuelRechargeResponse> getByDriver(
            @Parameter(description = "ID du chauffeur") @PathVariable UUID driverId,
            Authentication auth) {
        return resourceAccess.assertDriverAccess(getUserId(auth), driverId)
                .thenMany(withExpenses(fuelRechargeUseCase.getByDriverId(driverId)));
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
        return withExpenses(fuelRechargeUseCase.getByDateRange(start, end, getUserId(auth)));
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
                        request.driverId(),
                        request.odometerKm(),
                        stationLabel(request.stationName())
                );
        return resourceAccess.assertFuelRechargeAccess(getUserId(auth), id)
                .then(withExpense(fuelRechargeUseCase.update(cmd)));
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

    /** Nom saisi conservé tel quel quand il ne se réduit pas à l'enseigne (« Total Bastos »). */
    private String stationLabel(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim();
        try {
            FuelRecharge.StationName.valueOf(value.toUpperCase());
            return null;
        } catch (IllegalArgumentException ex) {
            return value.length() > 255 ? value.substring(0, 255) : value;
        }
    }

    private FuelRecharge.StationName normalizeStationName(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return FuelRecharge.StationName.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            // « Total Bastos » → TOTAL : l'enseigne reconnue dans le nom saisi.
            String upper = raw.trim().toUpperCase();
            for (FuelRecharge.StationName brand : FuelRecharge.StationName.values()) {
                if (brand != FuelRecharge.StationName.OTHER && upper.contains(brand.name())) return brand;
            }
            return FuelRecharge.StationName.OTHER;
        }
    }
}
