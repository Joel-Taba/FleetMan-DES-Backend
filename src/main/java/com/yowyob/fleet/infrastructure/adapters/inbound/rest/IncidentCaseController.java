package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.application.service.IncidentCaseService;
import com.yowyob.fleet.application.service.ResourceAccessService;
import com.yowyob.fleet.domain.model.Incident;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.IncidentResponse;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.IncidentCaseEntity;
import com.yowyob.fleet.infrastructure.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Dossier d'un incident : consultation, instruction, étapes, pièces, coûts, immobilisation. */
@RestController
@RequestMapping("/api/v1/operations/incidents")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_OPS_INCIDENTS)
@SecurityRequirement(name = "bearerAuth")
public class IncidentCaseController {

    private static final String ANY_ROLE = "hasAnyRole('FLEET_DRIVER', 'FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')";
    private static final String STAFF = "hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')";

    private final IncidentCaseService service;
    private final ResourceAccessService access;

    public record CaseResponse(IncidentResponse incident, IncidentCaseEntity details, String tripCode, String handlerName,
                               String reportedByName, String vehicleStatus, boolean vehicleImmobilized, boolean vehicleDrivable,
                               List<IncidentCaseService.Attachment> attachments, List<IncidentCaseService.TimelineEvent> timeline,
                               List<IncidentCaseService.CostLine> costs, BigDecimal approvedCosts, BigDecimal pendingCosts,
                               BigDecimal netCost, List<String> closureGaps, List<Map<String, Object>> handlers) {

        static CaseResponse from(IncidentCaseService.CaseView v) {
            return new CaseResponse(IncidentResponse.from(v.incident()), v.details(), v.tripCode(), v.handlerName(), v.reportedByName(),
                    v.vehicleStatus(), v.incident().isVehicleImmobilized(), v.incident().isVehicleDrivable(), v.attachments(), v.timeline(),
                    v.costs(), v.approvedCosts(), v.pendingCosts(), v.netCost(), v.closureGaps(), v.handlers());
        }
    }

    public record TransitionRequest(@NotNull Incident.Status status, UUID handlerId, String note, String report, String rootCause,
                                    String correctiveAction, String responsibility) {
    }

    public record ImmobilizationRequest(@NotNull Boolean immobilized, @NotBlank(message = "Indiquez le motif.") String reason) {
    }

    public record RepairOrderRequest(String subject) {
    }

    private static UUID userId(Authentication auth) {
        return ((AuthPort.UserDetail) auth.getPrincipal()).id();
    }

    private Mono<Void> scope(UUID id, Authentication auth) {
        return access.assertIncidentAccess(userId(auth), id);
    }

    @GetMapping("/mine")
    @PreAuthorize(ANY_ROLE)
    @Operation(summary = "Mes incidents (chauffeur concerné ou déclarant)")
    public Flux<IncidentResponse> mine(Authentication auth) {
        return service.mine(userId(auth)).map(IncidentResponse::from);
    }

    @GetMapping("/{id}/case")
    @PreAuthorize(ANY_ROLE)
    @Operation(summary = "Dossier complet : instruction, historique, pièces, coûts, ce qui manque pour clôturer")
    public Mono<CaseResponse> view(@PathVariable UUID id, Authentication auth) {
        return scope(id, auth).then(service.view(id)).map(CaseResponse::from);
    }

    @PatchMapping("/{id}/case")
    @PreAuthorize(STAFF)
    @Operation(summary = "Instruire le dossier (tiers, assurance, remboursement, cause…)")
    public Mono<CaseResponse> update(@PathVariable UUID id, @RequestBody IncidentCaseService.CaseDetails details, Authentication auth) {
        return scope(id, auth).then(service.update(userId(auth), id, details)).map(CaseResponse::from);
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize(STAFF)
    @Operation(summary = "Passer à l'étape suivante",
               description = "REPORTED → ACKNOWLEDGED → UNDER_INVESTIGATION → RESOLVED → CLOSED ; RESOLVED → UNDER_INVESTIGATION "
                       + "pour rouvrir ; CANCELLED avec motif tant que le dossier n'est pas résolu. La clôture exige un dossier complet.")
    public Mono<CaseResponse> transition(@PathVariable UUID id, @Valid @RequestBody TransitionRequest r, Authentication auth) {
        return scope(id, auth)
                .then(service.transition(userId(auth), id, new IncidentCaseService.TransitionCommand(r.status(), r.handlerId(), r.note(),
                        r.report(), r.rootCause(), r.correctiveAction(), r.responsibility())))
                .map(CaseResponse::from);
    }

    @PostMapping("/{id}/attachments")
    @PreAuthorize(ANY_ROLE)
    @Operation(summary = "Joindre une photo ou une pièce")
    public Mono<CaseResponse> attach(@PathVariable UUID id, @RequestBody IncidentCaseService.AttachmentCommand cmd, Authentication auth) {
        return scope(id, auth).then(service.addAttachment(userId(auth), id, cmd)).map(CaseResponse::from);
    }

    @PostMapping("/{id}/costs")
    @PreAuthorize(STAFF)
    @Operation(summary = "Ajouter un coût réel", description = "Crée une dépense en attente d'approbation, justificatif obligatoire.")
    public Mono<CaseResponse> addCost(@PathVariable UUID id, @RequestBody IncidentCaseService.CostCommand cmd, Authentication auth) {
        return scope(id, auth).then(service.addCost(userId(auth), id, cmd)).map(CaseResponse::from);
    }

    @PatchMapping("/{id}/immobilization")
    @PreAuthorize(STAFF)
    @Operation(summary = "Immobiliser le véhicule ou le libérer, avec un motif")
    public Mono<CaseResponse> immobilization(@PathVariable UUID id, @Valid @RequestBody ImmobilizationRequest r, Authentication auth) {
        return scope(id, auth).then(service.setImmobilization(userId(auth), id, r.immobilized(), r.reason())).map(CaseResponse::from);
    }

    @PostMapping("/{id}/repair-order")
    @PreAuthorize(STAFF)
    @Operation(summary = "Ouvrir l'ordre de réparation dans les maintenances et le rattacher au dossier")
    public Mono<CaseResponse> repairOrder(@PathVariable UUID id, @RequestBody(required = false) RepairOrderRequest r, Authentication auth) {
        return scope(id, auth).then(service.createRepairOrder(userId(auth), id, r != null ? r.subject() : null)).map(CaseResponse::from);
    }
}
