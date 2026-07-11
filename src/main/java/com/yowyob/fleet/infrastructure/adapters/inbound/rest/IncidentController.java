package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.domain.model.Incident;
import com.yowyob.fleet.domain.ports.in.ManageIncidentUseCase;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.IncidentRequest;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.IncidentResponse;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.IncidentStatusRequest;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.IncidentUpdateRequest;
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
@RequestMapping("/api/v1/operations/incidents")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_OPS_INCIDENTS)
@SecurityRequirement(name = "bearerAuth")
public class IncidentController {

    private final ManageIncidentUseCase incidentUseCase;

    private UUID getUserId(Authentication auth) {
        return ((AuthPort.UserDetail) auth.getPrincipal()).id();
    }

    // ── CRÉATION ─────────────────────────────────────────────────────────────

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_DRIVER')")
    @Operation(summary = "Déclarer un incident", description = "Enregistre un incident terrain. Publie une alerte prioritaire si la sévérité est HIGH ou CRITICAL.")
    public Mono<IncidentResponse> create(
            @Valid @RequestBody IncidentRequest request,
            Authentication auth) {

        ManageIncidentUseCase.CreateIncidentCommand cmd = new ManageIncidentUseCase.CreateIncidentCommand(
                request.type(),
                request.description(),
                request.severity(),
                request.cost(),
                request.longitude(),
                request.latitude(),
                request.witnessName(),
                request.witnessContact(),
                request.reportedBy(),
                request.vehicleId(),
                request.driverId());
        return incidentUseCase.createIncident(cmd).map(IncidentResponse::from);
    }

    // ── LECTURE ───────────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Lister mes incidents", description = "Retourne tous les incidents des véhicules gérés par le manager connecté.")
    public Flux<IncidentResponse> listAll(Authentication auth) {
        return incidentUseCase.getAllByManager(getUserId(auth)).map(IncidentResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_DRIVER')")
    @Operation(summary = "Détail d'un incident")
    public Mono<IncidentResponse> getById(
            @Parameter(description = "ID de l'incident") @PathVariable UUID id) {
        return incidentUseCase.getById(id).map(IncidentResponse::from);
    }

    @GetMapping("/vehicle/{vehicleId}")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Incidents d'un véhicule")
    public Flux<IncidentResponse> getByVehicle(
            @Parameter(description = "ID du véhicule") @PathVariable UUID vehicleId) {
        return incidentUseCase.getByVehicleId(vehicleId).map(IncidentResponse::from);
    }

    @GetMapping("/driver/{driverId}")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_DRIVER')")
    @Operation(summary = "Incidents d'un chauffeur")
    public Flux<IncidentResponse> getByDriver(
            @Parameter(description = "ID du chauffeur") @PathVariable UUID driverId) {
        return incidentUseCase.getByDriverId(driverId).map(IncidentResponse::from);
    }

    @GetMapping("/open")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Incidents ouverts", description = "Retourne uniquement les incidents en statut REPORTED ou UNDER_INVESTIGATION. Utile pour le tableau de bord.")
    public Flux<IncidentResponse> getOpen(Authentication auth) {
        return incidentUseCase.getOpenIncidents(getUserId(auth)).map(IncidentResponse::from);
    }

    @GetMapping("/filter")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Filtrer les incidents", description = "Filtre par type, sévérité ou statut. Fournir un seul paramètre à la fois.")
    public Flux<IncidentResponse> filter(
            @Parameter(description = "Filtrer par type", example = "ACCIDENT") @RequestParam(required = false) Incident.Type type,
            @Parameter(description = "Filtrer par sévérité", example = "HIGH") @RequestParam(required = false) Incident.Severity severity,
            @Parameter(description = "Filtrer par statut", example = "RESOLVED") @RequestParam(required = false) Incident.Status status,
            Authentication auth) {

        UUID managerId = getUserId(auth);

        if (type != null)
            return incidentUseCase.getByType(type, managerId).map(IncidentResponse::from);
        if (severity != null)
            return incidentUseCase.getBySeverity(severity, managerId).map(IncidentResponse::from);
        if (status != null)
            return incidentUseCase.getByStatus(status, managerId).map(IncidentResponse::from);

        return incidentUseCase.getAllByManager(managerId).map(IncidentResponse::from);
    }

    @GetMapping("/range")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Incidents par plage de dates")
    public Flux<IncidentResponse> getByDateRange(
            @Parameter(description = "Date de début (ISO 8601)", example = "2026-01-01T00:00:00") @RequestParam LocalDateTime start,
            @Parameter(description = "Date de fin (ISO 8601)", example = "2026-12-31T23:59:59") @RequestParam LocalDateTime end,
            Authentication auth) {
        return incidentUseCase.getByDateRange(start, end, getUserId(auth)).map(IncidentResponse::from);
    }

    // ── KPIs ──────────────────────────────────────────────────────────────────

    @GetMapping("/vehicle/{vehicleId}/cost")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Coût total des incidents d'un véhicule", description = "Retourne la somme des coûts de tous les incidents pour un véhicule donné.")
    public Mono<BigDecimal> getTotalCostByVehicle(
            @Parameter(description = "ID du véhicule") @PathVariable UUID vehicleId) {
        return incidentUseCase.getTotalCostByVehicleId(vehicleId);
    }

    // ── MISE À JOUR ───────────────────────────────────────────────────────────

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Mettre à jour un incident", description = "Met à jour les informations d'un incident (description, coût, témoins, numéros officiels). Interdit sur un incident CLOSED.")
    public Mono<IncidentResponse> update(
            @Parameter(description = "ID de l'incident") @PathVariable UUID id,
            @Valid @RequestBody IncidentUpdateRequest request) {

        ManageIncidentUseCase.UpdateIncidentCommand cmd = new ManageIncidentUseCase.UpdateIncidentCommand(
                id,
                request.description(),
                request.severity(),
                request.cost(),
                request.report(),
                request.witnessName(),
                request.witnessContact(),
                request.policeReportNumber(),
                request.insuranceClaimNumber(),
                request.longitude(),
                request.latitude());
        return incidentUseCase.update(cmd).map(IncidentResponse::from);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Changer le statut d'un incident", description = "Fait avancer l'incident dans son cycle de vie : REPORTED → UNDER_INVESTIGATION → RESOLVED → CLOSED.")
    public Mono<IncidentResponse> updateStatus(
            @Parameter(description = "ID de l'incident") @PathVariable UUID id,
            @Valid @RequestBody IncidentStatusRequest request) {
        return incidentUseCase.updateStatus(id, request.status()).map(IncidentResponse::from);
    }

    // ── SUPPRESSION ───────────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Supprimer un incident")
    public Mono<Void> delete(
            @Parameter(description = "ID de l'incident") @PathVariable UUID id) {
        return incidentUseCase.delete(id);
    }
}
