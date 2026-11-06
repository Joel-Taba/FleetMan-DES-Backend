package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.domain.model.Maintenance;
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
    private final com.yowyob.fleet.application.service.OperationFleetLookup fleetLookup;
    private final com.yowyob.fleet.application.service.ResourceAccessService resourceAccess;
    private final com.yowyob.fleet.application.service.OperationLookup lookup;

    /** Joint à chaque maintenance l'état de sa dépense générée. */
    private Flux<MaintenanceResponse> withExpenses(Flux<Maintenance> maintenances) {
        return maintenances.collectList().flatMapMany(list -> lookup.autoExpenses(list.stream().map(Maintenance::getId).toList())
                .flatMapMany(expenses -> Flux.fromIterable(list)
                        .map(m -> MaintenanceResponse.from(m, expenses.get(m.getId())))))
                .transform(f -> fleetLookup.attach(f, "maintenances", MaintenanceResponse::id, MaintenanceResponse::withFleet));
    }

    private Mono<MaintenanceResponse> withExpense(Mono<Maintenance> maintenance) {
        return withExpenses(maintenance.flux()).next();
    }

    /** Avancement d'une maintenance par le gestionnaire. */
    public record TransitionRequest(
            @jakarta.validation.constraints.NotBlank(message = "Indiquez l'action.")
            @io.swagger.v3.oas.annotations.media.Schema(allowableValues = {"PLAN", "START", "COMPLETE", "CANCEL"})
            String action,
            LocalDateTime plannedAt,
            String garage,
            @jakarta.validation.constraints.PositiveOrZero(message = "Le coût ne peut pas être négatif.")
            java.math.BigDecimal cost,
            String report,
            String reason
    ) {}

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

        UUID actor = getUserId(auth);
        boolean driver = com.yowyob.fleet.infrastructure.config.security.AuthPrincipal.isFleetDriver(auth);
        Maintenance.Status status = null;
        if (!driver && request.status() != null && !request.status().isBlank()) {
            try {
                status = Maintenance.Status.valueOf(request.status().trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                return Mono.error(com.yowyob.fleet.domain.exception.MaintenanceWorkflowException
                        .invalidTransition("REQUESTED", request.status()));
            }
        }
        Maintenance.Status initial = status;
        // Un chauffeur ne fait qu'une demande (statut REQUESTED), pour lui-même et pour le véhicule
        // qu'il conduit ; un gestionnaire saisit pour un véhicule de son périmètre.
        Mono<UUID> driverIdMono = driver ? lookup.localUser(actor) : Mono.justOrEmpty(request.driverId());
        Mono<Void> scope = driver
                ? resourceAccess.assertDriverOperatesVehicle(actor, request.vehicleId())
                : resourceAccess.assertVehicleAccess(actor, request.vehicleId());
        return scope.then(driverIdMono.map(java.util.Optional::of).defaultIfEmpty(java.util.Optional.empty()))
                .flatMap(driverId -> maintenanceUseCase.createMaintenance(new ManageMaintenanceUseCase.CreateMaintenanceCommand(
                        request.subject(),
                        request.cost(),
                        request.report(),
                        request.longitude(),
                        request.latitude(),
                        request.locationName(),
                        request.vehicleId(),
                        driverId.orElse(null),
                        driver ? Maintenance.Status.REQUESTED : initial,
                        request.maintenanceType() != null ? request.maintenanceType().trim().toUpperCase() : null,
                        Boolean.TRUE.equals(request.urgent()),
                        request.plannedAt(),
                        actor)))
                .flatMap(saved -> withExpense(Mono.just(saved)));
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN')")
    @Operation(summary = "Faire avancer une maintenance",
               description = "PLAN (date prévue + garage), START (véhicule à l'atelier), COMPLETE (coût et rapport définitifs), "
                       + "CANCEL (motif obligatoire). Le chauffeur demandeur et ceux du véhicule sont notifiés.")
    public Mono<MaintenanceResponse> transition(
            @Parameter(description = "ID de la maintenance") @PathVariable UUID id,
            @Valid @RequestBody TransitionRequest request,
            Authentication auth) {
        UUID actor = getUserId(auth);
        if (com.yowyob.fleet.infrastructure.config.security.AuthPrincipal.isFleetDriver(auth)
                && !com.yowyob.fleet.infrastructure.config.security.AuthPrincipal.isFleetManager(auth)) {
            return Mono.error(com.yowyob.fleet.domain.exception.MaintenanceWorkflowException.driverCannotEdit());
        }
        return maintenanceUseCase.getById(id)
                .flatMap(m -> resourceAccess.assertVehicleAccess(actor, m.getVehicleId()))
                .then(withExpense(maintenanceUseCase.transition(new ManageMaintenanceUseCase.TransitionCommand(
                        id, actor, request.action(), request.plannedAt(), request.garage(),
                        request.cost(), request.report(), request.reason()))));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('FLEET_DRIVER')")
    @Operation(summary = "Mes maintenances (chauffeur)",
               description = "Demandes du chauffeur, maintenances où il est cité et celles de son véhicule (ouvertes ou des 30 derniers jours).")
    public Flux<MaintenanceResponse> mine(Authentication auth) {
        return withExpenses(maintenanceUseCase.getForDriver(getUserId(auth)));
    }

    // ── LECTURE ───────────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Lister mes maintenances",
               description = "Retourne toutes les maintenances des véhicules gérés par le manager connecté.")
    public Flux<MaintenanceResponse> listAll(Authentication auth) {
        return withExpenses(maintenanceUseCase.getAllByManager(getUserId(auth)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_DRIVER')")
    @Operation(summary = "Détail d'une maintenance")
    public Mono<MaintenanceResponse> getById(
            @Parameter(description = "ID de la maintenance") @PathVariable UUID id,
            Authentication auth) {
        return resourceAccess.assertMaintenanceAccess(getUserId(auth), id)
                .then(withExpense(maintenanceUseCase.getById(id)));
    }

    @GetMapping("/vehicle/{vehicleId}")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Maintenances d'un véhicule",
               description = "Retourne l'historique complet des maintenances pour un véhicule donné.")
    public Flux<MaintenanceResponse> getByVehicle(
            @Parameter(description = "ID du véhicule") @PathVariable UUID vehicleId,
            Authentication auth) {
        return resourceAccess.assertVehicleAccess(getUserId(auth), vehicleId)
                .thenMany(withExpenses(maintenanceUseCase.getByVehicleId(vehicleId)));
    }

    @GetMapping("/driver/{driverId}")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Maintenances d'un chauffeur",
               description = "Retourne toutes les maintenances impliquant un chauffeur donné.")
    public Flux<MaintenanceResponse> getByDriver(
            @Parameter(description = "ID du chauffeur") @PathVariable UUID driverId,
            Authentication auth) {
        return resourceAccess.assertDriverAccess(getUserId(auth), driverId)
                .thenMany(withExpenses(maintenanceUseCase.getByDriverId(driverId)));
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
        return withExpenses(maintenanceUseCase.getByDateRange(start, end, getUserId(auth)));
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
                .then(withExpense(maintenanceUseCase.update(cmd)));
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
