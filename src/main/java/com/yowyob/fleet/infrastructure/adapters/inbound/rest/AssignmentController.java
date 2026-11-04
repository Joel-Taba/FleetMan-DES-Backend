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
import reactor.core.publisher.Flux;
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
    private final com.yowyob.fleet.application.service.WorkRulesService workRules;
    private final com.yowyob.fleet.application.service.PlanningToolsService planningTools;

    private UUID getUserId(Authentication auth) {
        return ((AuthPort.UserDetail) auth.getPrincipal()).id();
    }

    private Mono<AssignmentResponse> withTrips(com.yowyob.fleet.domain.model.Assignment a) {
        return Mono.zip(assignmentUseCase.countLinkedTrips(a.getId()), workRules.extras(a.getId()))
                .map(t -> AssignmentResponse.from(a).withTripCount(t.getT1()).withTracking(
                        t.getT2().acknowledgedAt(), t.getT2().changeReason(), t.getT2().changedAt(),
                        t.getT2().workRuleOverride(), t.getT2().cancelReason()));
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
                        request.notes(),
                        request.workRuleOverride(),
                        request.changeReason()
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
                .then(assignmentUseCase.updateStatus(id, request.status(), request.actualKm(), request.reason()))
                .flatMap(this::withTrips);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Modifier un poste à venir : véhicule, chauffeur, horaires",
               description = "Planning publié : motif obligatoire, communiqué au chauffeur. Un dépassement des règles "
                       + "de temps de travail exige un motif (workRuleOverride).")
    public Mono<AssignmentResponse> updateResources(
            @PathVariable UUID id,
            @RequestBody AssignmentResourceUpdateRequest request,
            Authentication auth) {
        UUID actor = getUserId(auth);
        return resourceAccess.assertAssignmentManagement(actor, id)
                .then(resourceAccess.assertVehicleAccess(actor, request.vehicleId()))
                .then(resourceAccess.assertDriverAccess(actor, request.driverId()))
                .then(assignmentUseCase.updateShift(id, new ManageAssignmentUseCase.ShiftChange(
                        request.vehicleId(), request.driverId(), request.startDatetime(), request.endDatetime(),
                        request.reason(), request.workRuleOverride())))
                .flatMap(this::withTrips);
    }

    // ── SUPPRESSION ───────────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Supprimer une affectation")
    public Mono<Void> delete(@PathVariable UUID id, @RequestParam(required = false) String reason, Authentication auth) {
        return resourceAccess.assertAssignmentManagement(getUserId(auth), id)
                .then(assignmentUseCase.delete(id, reason));
    }

    // ── TEMPS DE TRAVAIL, SÉRIES, ACCUSÉ DE RÉCEPTION ────────────────────────

    public record WorkCheckResponse(java.util.List<com.yowyob.fleet.domain.model.WorkTimeRules.Violation> violations,
                                    com.yowyob.fleet.domain.model.WorkTimeRules.Limits limits) {}

    @GetMapping("/work-check")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Vérifier les règles de temps de travail avant d'enregistrer un poste")
    public Mono<WorkCheckResponse> workCheck(
            @RequestParam UUID fleetId,
            @RequestParam UUID driverId,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime start,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime end,
            @RequestParam(required = false) UUID excludeId,
            Authentication auth) {
        return accessScope.assertFleetAccess(getUserId(auth), fleetId)
                .then(resourceAccess.assertDriverAccess(getUserId(auth), driverId))
                .then(Mono.zip(workRules.check(fleetId, driverId, start, end, excludeId), workRules.limitsOfFleet(fleetId)))
                .map(t -> new WorkCheckResponse(t.getT1(), t.getT2()));
    }

    public record BulkRequest(UUID scheduleId, @jakarta.validation.constraints.NotNull UUID fleetId,
                              @jakarta.validation.constraints.NotNull UUID vehicleId,
                              @jakarta.validation.constraints.NotNull UUID driverId,
                              @jakarta.validation.constraints.NotEmpty java.util.List<java.time.LocalDate> dates,
                              @jakarta.validation.constraints.NotNull java.time.LocalTime startTime,
                              @jakarta.validation.constraints.NotNull java.time.LocalTime endTime,
                              String notes, String workRuleOverride, String changeReason) {}

    @PostMapping("/bulk")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Créer le même poste sur plusieurs jours",
               description = "Tout ou rien : si un jour est refusé (conflit, absence, temps de travail…), aucun poste n'est créé. "
                       + "Une heure de fin inférieure à l'heure de début désigne un poste de nuit.")
    public Mono<java.util.List<AssignmentResponse>> bulk(@Valid @RequestBody BulkRequest request, Authentication auth) {
        UUID actor = getUserId(auth);
        return accessScope.assertFleetAccess(actor, request.fleetId())
                .then(resourceAccess.assertVehicleAccess(actor, request.vehicleId()))
                .then(resourceAccess.assertDriverAccess(actor, request.driverId()))
                .then(request.scheduleId() != null ? resourceAccess.assertScheduleAccess(actor, request.scheduleId()) : Mono.<Void>empty())
                .then(planningTools.bulkCreate(new com.yowyob.fleet.application.service.PlanningToolsService.BulkCommand(
                        request.scheduleId(), request.fleetId(), request.vehicleId(), request.driverId(), request.dates(),
                        request.startTime(), request.endTime(), request.notes(), request.workRuleOverride(), request.changeReason())))
                .flatMapMany(Flux::fromIterable)
                .concatMap(this::withTrips)
                .collectList();
    }

    @PostMapping("/{id}/acknowledge")
    @PreAuthorize("hasRole('FLEET_DRIVER')")
    @Operation(summary = "Le chauffeur accuse réception de son poste")
    public Mono<AssignmentResponse> acknowledge(@PathVariable UUID id, Authentication auth) {
        UUID actor = getUserId(auth);
        return assignmentUseCase.getById(id)
                .flatMap(a -> accessScope.resolveLocalUserId(actor).flatMap(localId -> localId.equals(a.getDriverId())
                        ? workRules.acknowledge(localId, null, id).thenReturn(a)
                        : Mono.<com.yowyob.fleet.domain.model.Assignment>error(com.yowyob.fleet.domain.exception.PlanningException.notYourShift())))
                .flatMap(this::withTrips);
    }
}
