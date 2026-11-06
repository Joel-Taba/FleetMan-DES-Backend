package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.application.service.AccessScopeService;
import com.yowyob.fleet.domain.model.Incident;
import com.yowyob.fleet.domain.ports.in.ManageIncidentUseCase;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.IncidentRequest;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.IncidentResponse;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.IncidentStatusRequest;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.IncidentUpdateRequest;
import com.yowyob.fleet.infrastructure.config.OpenApiConfig;
import com.yowyob.fleet.infrastructure.config.security.AuthPrincipal;
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
@RequestMapping("/api/v1/operations/incidents")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_OPS_INCIDENTS)
@SecurityRequirement(name = "bearerAuth")
public class IncidentController {

    private final ManageIncidentUseCase incidentUseCase;
    private final com.yowyob.fleet.application.service.OperationFleetLookup fleetLookup;
    private final AccessScopeService accessScope;
    private final com.yowyob.fleet.application.service.ResourceAccessService resourceAccess;
    private final com.yowyob.fleet.application.service.IncidentCaseService caseService;

    /** Listes : résumé du dossier (photos, lieu, trajet, dernière étape), plus récemment déclarés d'abord. */
    private Flux<IncidentResponse> enriched(Flux<Incident> source) {
        return fleetLookup.attach(caseService.enrich(source).map(e -> IncidentResponse.from(e.incident()).withSummary(e.summary())),
                "incidents", IncidentResponse::id, IncidentResponse::withFleet);
    }

    private UUID getUserId(Authentication auth) {
        return ((AuthPort.UserDetail) auth.getPrincipal()).id();
    }

    private Mono<Void> requireIncidentAccess(UUID incidentId, Authentication auth) {
        return incidentUseCase.getById(incidentId)
                .flatMap(inc -> accessScope.assertVehicleAccess(getUserId(auth), inc.getVehicleId()));
    }

    // ── CRÉATION ─────────────────────────────────────────────────────────────

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_DRIVER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Déclarer un incident",
               description = "Enregistre un incident terrain. Publie une alerte prioritaire si la sévérité est HIGH ou CRITICAL.")
    public Mono<IncidentResponse> create(
            @Valid @RequestBody IncidentRequest request,
            Authentication auth) {
        // Un chauffeur déclare toujours pour lui-même : il est le chauffeur concerné, il suit le dossier.
        boolean driver = AuthPrincipal.isFleetDriver(auth);
        Mono<java.util.Optional<UUID>> concernedDriver = driver
                ? accessScope.resolveLocalUserId(getUserId(auth)).map(java.util.Optional::of)
                : Mono.just(java.util.Optional.ofNullable(request.driverId()));
        return concernedDriver.flatMap(driverId -> create(request, driverId.orElse(null), driver, auth));
    }

    private Mono<IncidentResponse> create(IncidentRequest request, UUID driverId, boolean driver, Authentication auth) {
        ManageIncidentUseCase.CreateIncidentCommand cmd =
                new ManageIncidentUseCase.CreateIncidentCommand(
                        request.type(),
                        request.description(),
                        request.severity(),
                        request.occurredAt(),
                        request.vehicleDrivable(),
                        request.longitude(),
                        request.latitude(),
                        request.witnessName(),
                        request.witnessContact(),
                        request.reportedBy(),
                        request.vehicleId(),
                        driverId,
                        getUserId(auth),
                        new com.yowyob.fleet.application.service.IncidentCaseService.CaseDetails(
                                request.locationLabel(), request.estimatedCost(), request.injuries(), request.injuriesCount(),
                                request.thirdPartyInvolved(), request.thirdPartyName(), request.thirdPartyPlate(),
                                request.thirdPartyInsurer(), request.thirdPartyContact(), null, null, null, null, null, null, null,
                                null, null, null)
                );
        // Un chauffeur ne déclare que pour le véhicule qu'il conduit (il pouvait viser
        // n'importe quel véhicule, y compris d'une autre entreprise).
        Mono<Void> scope = driver
                ? resourceAccess.assertDriverOperatesVehicle(getUserId(auth), request.vehicleId())
                : resourceAccess.assertVehicleAccess(getUserId(auth), request.vehicleId())
                        .then(driverId != null ? resourceAccess.assertDriverAccess(getUserId(auth), driverId) : Mono.<Void>empty());
        return scope.then(incidentUseCase.createIncident(cmd)).map(IncidentResponse::from);
    }

    // ── LECTURE ───────────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Lister mes incidents",
               description = "Retourne tous les incidents des véhicules gérés par le manager connecté.")
    public Flux<IncidentResponse> listAll(Authentication auth) {
        return enriched(incidentUseCase.getAllByManager(getUserId(auth)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_DRIVER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Détail d'un incident")
    public Mono<IncidentResponse> getById(
            @Parameter(description = "ID de l'incident") @PathVariable UUID id,
            Authentication auth) {
        if (AuthPrincipal.isFleetDriver(auth)) {
            UUID caller = getUserId(auth);
            return incidentUseCase.getById(id)
                    .filter(inc -> caller.equals(inc.getDriverId())
                            || (inc.getReportedBy() != null && caller.toString().equals(inc.getReportedBy())))
                    .switchIfEmpty(Mono.error(new org.springframework.security.access.AccessDeniedException(
                            "Incident hors périmètre.")))
                    .map(IncidentResponse::from);
        }
        return requireIncidentAccess(id, auth)
                .then(incidentUseCase.getById(id))
                .map(IncidentResponse::from);
    }

    @GetMapping("/vehicle/{vehicleId}")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Incidents d'un véhicule")
    public Flux<IncidentResponse> getByVehicle(
            @Parameter(description = "ID du véhicule") @PathVariable UUID vehicleId,
            Authentication auth) {
        return accessScope.assertVehicleAccess(getUserId(auth), vehicleId)
                .thenMany(enriched(incidentUseCase.getByVehicleId(vehicleId)));
    }

    @GetMapping("/driver/{driverId}")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Incidents d'un chauffeur")
    public Flux<IncidentResponse> getByDriver(
            @Parameter(description = "ID du chauffeur") @PathVariable UUID driverId,
            Authentication auth) {
        return resourceAccess.assertDriverAccess(getUserId(auth), driverId)
                .thenMany(enriched(incidentUseCase.getByDriverId(driverId)));
    }

    @GetMapping("/open")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Incidents ouverts",
               description = "Retourne uniquement les incidents en statut REPORTED ou UNDER_INVESTIGATION. Utile pour le tableau de bord.")
    public Flux<IncidentResponse> getOpen(Authentication auth) {
        return enriched(incidentUseCase.getOpenIncidents(getUserId(auth)));
    }

    @GetMapping("/filter")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Filtrer les incidents",
               description = "Filtre par type, sévérité ou statut. Fournir un seul paramètre à la fois.")
    public Flux<IncidentResponse> filter(
            @Parameter(description = "Filtrer par type", example = "ACCIDENT")
            @RequestParam(required = false) Incident.Type type,
            @Parameter(description = "Filtrer par sévérité", example = "HIGH")
            @RequestParam(required = false) Incident.Severity severity,
            @Parameter(description = "Filtrer par statut", example = "RESOLVED")
            @RequestParam(required = false) Incident.Status status,
            Authentication auth) {

        UUID managerId = getUserId(auth);

        if (type != null)     return enriched(incidentUseCase.getByType(type, managerId));
        if (severity != null) return enriched(incidentUseCase.getBySeverity(severity, managerId));
        if (status != null)   return enriched(incidentUseCase.getByStatus(status, managerId));

        return enriched(incidentUseCase.getAllByManager(managerId));
    }

    @GetMapping("/range")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Incidents par plage de dates")
    public Flux<IncidentResponse> getByDateRange(
            @Parameter(description = "Date de début (ISO 8601)", example = "2026-01-01T00:00:00")
            @RequestParam LocalDateTime start,
            @Parameter(description = "Date de fin (ISO 8601)", example = "2026-12-31T23:59:59")
            @RequestParam LocalDateTime end,
            Authentication auth) {
        return enriched(incidentUseCase.getByDateRange(start, end, getUserId(auth)));
    }

    // ── KPIs ──────────────────────────────────────────────────────────────────

    @GetMapping("/vehicle/{vehicleId}/cost")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Coût total des incidents d'un véhicule",
               description = "Retourne la somme des coûts de tous les incidents pour un véhicule donné.")
    public Mono<BigDecimal> getTotalCostByVehicle(
            @Parameter(description = "ID du véhicule") @PathVariable UUID vehicleId,
            Authentication auth) {
        return accessScope.assertVehicleAccess(getUserId(auth), vehicleId)
                .then(incidentUseCase.getTotalCostByVehicleId(vehicleId));
    }

    // ── MISE À JOUR ───────────────────────────────────────────────────────────

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Mettre à jour un incident",
               description = "Met à jour les informations d'un incident (description, coût, témoins, numéros officiels). Interdit sur un incident CLOSED.")
    public Mono<IncidentResponse> update(
            @Parameter(description = "ID de l'incident") @PathVariable UUID id,
            @Valid @RequestBody IncidentUpdateRequest request,
            Authentication auth) {

        return requireIncidentAccess(id, auth)
                .then(incidentUseCase.update(new ManageIncidentUseCase.UpdateIncidentCommand(
                        id,
                        request.description(),
                        request.severity(),
                        request.report(),
                        request.witnessName(),
                        request.witnessContact(),
                        request.policeReportNumber(),
                        request.insuranceClaimNumber(),
                        request.longitude(),
                        request.latitude()
                )))
                .map(IncidentResponse::from);
    }

}
