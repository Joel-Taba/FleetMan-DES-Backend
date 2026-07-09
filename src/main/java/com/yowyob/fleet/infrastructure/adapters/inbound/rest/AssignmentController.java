package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.domain.ports.in.ManageAssignmentUseCase;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.AssignmentRequest;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.AssignmentResourceUpdateRequest;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.AssignmentResponse;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.AssignmentStatusRequest;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/assignments")
@RequiredArgsConstructor
@Tag(name = "16. Planning | Affectations")
@SecurityRequirement(name = "bearerAuth")
public class AssignmentController {

    private final ManageAssignmentUseCase assignmentUseCase;

    private UUID getUserId(Authentication auth) {
        return ((AuthPort.UserDetail) auth.getPrincipal()).id();
    }

    // ── CRÉATION ─────────────────────────────────────────────────────────────

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Créer une affectation",
               description = "Affecte un conducteur à un véhicule sur une plage horaire. "
                       + "Vérifie automatiquement les conflits avant création.")
    public Mono<AssignmentResponse> create(
            @Valid @RequestBody AssignmentRequest request) {

        ManageAssignmentUseCase.CreateAssignmentCommand cmd =
                new ManageAssignmentUseCase.CreateAssignmentCommand(
                        request.scheduleId(),
                        request.fleetId(),
                        request.vehicleId(),
                        request.driverId(),
                        request.missionId(),
                        request.startDatetime(),
                        request.endDatetime(),
                        request.startLocation(),
                        request.endLocation(),
                        request.estimatedKm(),
                        request.notes()
                );
        return assignmentUseCase.createAssignment(cmd).map(AssignmentResponse::from);
    }

    // ── LECTURE ───────────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Lister mes affectations",
               description = "Retourne toutes les affectations du manager connecté, avec pagination.")
    public Mono<PageResponse<AssignmentResponse>> listAll(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return PageResponse.of(
                assignmentUseCase.getAllByManager(getUserId(auth))
                        .map(AssignmentResponse::from),
                page, size
        );
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER','FLEET_DRIVER')")
    @Operation(summary = "Détail d'une affectation")
    public Mono<AssignmentResponse> getById(@PathVariable UUID id) {
        return assignmentUseCase.getById(id).map(AssignmentResponse::from);
    }

    @GetMapping("/schedule/{scheduleId}")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Affectations d'un planning")
    public Mono<PageResponse<AssignmentResponse>> getBySchedule(
            @PathVariable UUID scheduleId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        return PageResponse.of(
                assignmentUseCase.getAllBySchedule(scheduleId)
                        .map(AssignmentResponse::from),
                page, size
        );
    }

    @GetMapping("/vehicle/{vehicleId}")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Affectations d'un véhicule")
    public Mono<PageResponse<AssignmentResponse>> getByVehicle(
            @PathVariable UUID vehicleId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return PageResponse.of(
                assignmentUseCase.getByVehicle(vehicleId).map(AssignmentResponse::from),
                page, size
        );
    }

    @GetMapping("/driver/{driverId}")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER','FLEET_DRIVER')")
    @Operation(summary = "Affectations d'un conducteur")
    public Mono<PageResponse<AssignmentResponse>> getByDriver(
            @PathVariable UUID driverId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return PageResponse.of(
                assignmentUseCase.getByDriver(driverId).map(AssignmentResponse::from),
                page, size
        );
    }

    @GetMapping("/driver/{driverId}/today")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER','FLEET_DRIVER')")
    @Operation(summary = "Affectations du conducteur aujourd'hui",
               description = "Retourne les affectations du conducteur pour la journée en cours. "
                       + "Utilisé par l'application mobile du conducteur.")
    public Mono<PageResponse<AssignmentResponse>> getByDriverToday(
            @PathVariable UUID driverId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        return PageResponse.of(
                assignmentUseCase.getByDriverToday(driverId).map(AssignmentResponse::from),
                page, size
        );
    }

    @GetMapping("/range")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Affectations par plage de dates")
    public Mono<PageResponse<AssignmentResponse>> getByDateRange(
            Authentication auth,
            @Parameter(example = "2026-06-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @Parameter(example = "2026-06-30")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        return PageResponse.of(
                assignmentUseCase.getByDateRange(getUserId(auth), start, end)
                        .map(AssignmentResponse::from),
                page, size
        );
    }

    @GetMapping("/conflicts")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Détecter les conflits d'affectation",
               description = "Retourne les affectations actives qui se chevauchent "
                       + "pour le même véhicule ou conducteur.")
    public Mono<PageResponse<AssignmentResponse>> getConflicts(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return PageResponse.of(
                assignmentUseCase.getConflicts(getUserId(auth)).map(AssignmentResponse::from),
                page, size
        );
    }

    // ── DISPONIBILITÉ ─────────────────────────────────────────────────────────

    @GetMapping("/vehicle/{vehicleId}/availability")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Vérifier la disponibilité d'un véhicule",
               description = "Retourne true si le véhicule est disponible sur la plage horaire.")
    public Mono<Boolean> checkVehicleAvailability(
            @PathVariable UUID vehicleId,
            @Parameter(example = "2026-06-02T08:00:00")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @Parameter(example = "2026-06-02T18:00:00")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {

        return assignmentUseCase.checkVehicleAvailability(vehicleId, start, end, null);
    }

    @GetMapping("/driver/{driverId}/availability")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Vérifier la disponibilité d'un conducteur")
    public Mono<Boolean> checkDriverAvailability(
            @PathVariable UUID driverId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {

        return assignmentUseCase.checkDriverAvailability(driverId, start, end, null);
    }

    // ── CHANGEMENT DE STATUT ──────────────────────────────────────────────────

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER','FLEET_DRIVER')")
    @Operation(summary = "Changer le statut d'une affectation",
               description = "Fait avancer l'affectation dans son cycle de vie : "
                       + "PENDING → IN_PROGRESS → COMPLETED. "
                       + "Le kilométrage réel est requis pour COMPLETED.")
    public Mono<AssignmentResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody AssignmentStatusRequest request) {

        return assignmentUseCase.updateStatus(id, request.status(), request.actualKm())
                .map(AssignmentResponse::from);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Réassigner véhicule/conducteur (Manager UI)")
    public Mono<AssignmentResponse> updateResources(
            @PathVariable UUID id,
            @RequestBody AssignmentResourceUpdateRequest request) {
        return assignmentUseCase.updateResources(id, request.vehicleId(), request.driverId())
                .map(AssignmentResponse::from);
    }

    // ── SUPPRESSION ───────────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Supprimer une affectation")
    public Mono<Void> delete(@PathVariable UUID id) {
        return assignmentUseCase.delete(id);
    }
}
