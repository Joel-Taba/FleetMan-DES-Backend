package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.application.service.AccessScopeService;
import com.yowyob.fleet.application.service.ResourceAccessService;
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
    private final AccessScopeService accessScope;
    private final ResourceAccessService resourceAccess;

    private UUID getUserId(Authentication auth) {
        return ((AuthPort.UserDetail) auth.getPrincipal()).id();
    }

    private Mono<AssignmentResponse> withTrips(com.yowyob.fleet.domain.model.Assignment a) {
        return assignmentUseCase.countLinkedTrips(a.getId()).map(AssignmentResponse.from(a)::withTripCount);
    }

    /** Un programme encore en brouillon n'est pas montré au chauffeur. */
    private Mono<Boolean> visibleTo(Authentication auth, com.yowyob.fleet.domain.model.Assignment a) {
        boolean driver = auth.getAuthorities().stream().anyMatch(g -> "ROLE_FLEET_DRIVER".equals(g.getAuthority()));
        return driver ? assignmentUseCase.isVisibleToDriver(a) : Mono.just(true);
    }

    // ── CRÉATION ─────────────────────────────────────────────────────────────

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Créer une affectation",
               description = "Poste : réserve un binôme chauffeur + véhicule sur un créneau. Les trajets faits pendant "
                       + "le poste lui sont rattachés ; il démarre au premier départ et se termine à son heure de fin.")
    public Mono<AssignmentResponse> create(
            @Valid @RequestBody AssignmentRequest request,
            Authentication auth) {

        ManageAssignmentUseCase.CreateAssignmentCommand cmd =
                new ManageAssignmentUseCase.CreateAssignmentCommand(
                        request.scheduleId(),
                        request.fleetId(),
                        request.vehicleId(),
                        request.driverId(),
                        request.startDatetime(),
                        request.endDatetime(),
                        request.notes()
                );
        UUID actor = getUserId(auth);
        Mono<Void> scope = (request.fleetId() != null
                ? accessScope.assertFleetAccess(actor, request.fleetId())
                : Mono.<Void>empty())
                .then(resourceAccess.assertVehicleAccess(actor, request.vehicleId()))
                .then(resourceAccess.assertDriverAccess(actor, request.driverId()))
                .then(request.scheduleId() != null
                        ? resourceAccess.assertScheduleAccess(actor, request.scheduleId())
                        : Mono.<Void>empty());
        return scope.then(assignmentUseCase.createAssignment(cmd)).flatMap(this::withTrips);
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
                        .concatMap(this::withTrips),
                page, size
        );
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER','FLEET_DRIVER')")
    @Operation(summary = "Détail d'une affectation")
    public Mono<AssignmentResponse> getById(@PathVariable UUID id, Authentication auth) {
        return resourceAccess.assertAssignmentAccess(getUserId(auth), id)
                .then(assignmentUseCase.getById(id)).flatMap(this::withTrips);
    }

    @GetMapping("/schedule/{scheduleId}")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Affectations d'un planning")
    public Mono<PageResponse<AssignmentResponse>> getBySchedule(
            @PathVariable UUID scheduleId,
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        return PageResponse.of(
                resourceAccess.assertScheduleAccess(getUserId(auth), scheduleId)
                        .thenMany(assignmentUseCase.getAllBySchedule(scheduleId))
                        .concatMap(this::withTrips),
                page, size
        );
    }

    @GetMapping("/vehicle/{vehicleId}")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Affectations d'un véhicule")
    public Mono<PageResponse<AssignmentResponse>> getByVehicle(
            @PathVariable UUID vehicleId,
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return PageResponse.of(
                resourceAccess.assertVehicleAccess(getUserId(auth), vehicleId)
                        .thenMany(assignmentUseCase.getByVehicle(vehicleId)).concatMap(this::withTrips),
                page, size
        );
    }

    @GetMapping("/driver/{driverId}")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER','FLEET_DRIVER')")
    @Operation(summary = "Affectations d'un conducteur")
    public Mono<PageResponse<AssignmentResponse>> getByDriver(
            @PathVariable UUID driverId,
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return PageResponse.of(
                resourceAccess.assertDriverAccess(getUserId(auth), driverId)
                        .thenMany(assignmentUseCase.getByDriver(driverId))
                        .filterWhen(a -> visibleTo(auth, a)).concatMap(this::withTrips),
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
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        return PageResponse.of(
                resourceAccess.assertDriverAccess(getUserId(auth), driverId)
                        .thenMany(assignmentUseCase.getByDriverToday(driverId))
                        .filterWhen(a -> visibleTo(auth, a)).concatMap(this::withTrips),
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
                        .concatMap(this::withTrips),
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
                assignmentUseCase.getConflicts(getUserId(auth)).concatMap(this::withTrips),
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
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            Authentication auth) {

        return resourceAccess.assertVehicleAccess(getUserId(auth), vehicleId)
                .then(assignmentUseCase.checkVehicleAvailability(vehicleId, start, end, null));
    }

    @GetMapping("/driver/{driverId}/availability")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Vérifier la disponibilité d'un conducteur")
    public Mono<Boolean> checkDriverAvailability(
            @PathVariable UUID driverId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            Authentication auth) {

        return resourceAccess.assertDriverAccess(getUserId(auth), driverId)
                .then(assignmentUseCase.checkDriverAvailability(driverId, start, end, null));
    }

    // ── CHANGEMENT DE STATUT ──────────────────────────────────────────────────

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Marquer un poste absent (NO_SHOW) ou l'annuler",
               description = "Le poste démarre au premier départ d'un trajet et se termine automatiquement à son "
                       + "heure de fin (km = somme des trajets). Seuls NO_SHOW et CANCELLED sont manuels.")
    public Mono<AssignmentResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody AssignmentStatusRequest request,
            Authentication auth) {

        return resourceAccess.assertAssignmentAccess(getUserId(auth), id)
                .then(assignmentUseCase.updateStatus(id, request.status(), request.actualKm()))
                .flatMap(this::withTrips);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Réassigner véhicule/conducteur (Manager UI)")
    public Mono<AssignmentResponse> updateResources(
            @PathVariable UUID id,
            @RequestBody AssignmentResourceUpdateRequest request,
            Authentication auth) {
        UUID actor = getUserId(auth);
        return resourceAccess.assertAssignmentManagement(actor, id)
                .then(resourceAccess.assertVehicleAccess(actor, request.vehicleId()))
                .then(resourceAccess.assertDriverAccess(actor, request.driverId()))
                .then(assignmentUseCase.updateResources(id, request.vehicleId(), request.driverId()))
                .flatMap(this::withTrips);
    }

    // ── SUPPRESSION ───────────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Supprimer une affectation")
    public Mono<Void> delete(@PathVariable UUID id, Authentication auth) {
        return resourceAccess.assertAssignmentManagement(getUserId(auth), id)
                .then(assignmentUseCase.delete(id));
    }
}
