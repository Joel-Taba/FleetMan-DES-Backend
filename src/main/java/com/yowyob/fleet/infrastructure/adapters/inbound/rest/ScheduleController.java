package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.domain.ports.in.ManageScheduleUseCase;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.PageResponse;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.ScheduleRequest;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.ScheduleResponse;
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
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/schedules")
@RequiredArgsConstructor
@Tag(name = "15. Planning | Plannings de service")
@SecurityRequirement(name = "bearerAuth")
public class ScheduleController {

    private final ManageScheduleUseCase scheduleUseCase;

    private UUID getUserId(Authentication auth) {
        return ((AuthPort.UserDetail) auth.getPrincipal()).id();
    }

    // ── CRÉATION ─────────────────────────────────────────────────────────────

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Créer un planning",
               description = "Crée un nouveau planning de service en statut DRAFT.")
    public Mono<ScheduleResponse> create(
            @Valid @RequestBody ScheduleRequest request,
            Authentication auth) {

        ManageScheduleUseCase.CreateScheduleCommand cmd =
                new ManageScheduleUseCase.CreateScheduleCommand(
                        request.fleetId(),
                        getUserId(auth),
                        request.title(),
                        request.periodType(),
                        request.startDate(),
                        request.endDate(),
                        request.notes()
                );
        return scheduleUseCase.createSchedule(cmd).map(ScheduleResponse::from);
    }

    // ── LECTURE ───────────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Lister mes plannings",
               description = "Retourne tous les plannings du manager connecté, avec pagination.")
    public Mono<PageResponse<ScheduleResponse>> listAll(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return PageResponse.of(
                scheduleUseCase.getAllByManager(getUserId(auth))
                        .map(ScheduleResponse::from),
                page, size
        );
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Détail d'un planning")
    public Mono<ScheduleResponse> getById(
            @Parameter(description = "ID du planning") @PathVariable UUID id) {
        return scheduleUseCase.getById(id).map(ScheduleResponse::from);
    }

    @GetMapping("/fleet/{fleetId}")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Plannings d'une flotte")
    public Mono<PageResponse<ScheduleResponse>> getByFleet(
            @PathVariable UUID fleetId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return PageResponse.of(
                scheduleUseCase.getAllByFleet(fleetId).map(ScheduleResponse::from),
                page, size
        );
    }

    @GetMapping("/range")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Plannings par période",
               description = "Filtre les plannings dont la période chevauche la plage fournie.")
    public Mono<PageResponse<ScheduleResponse>> getByPeriod(
            Authentication auth,
            @Parameter(example = "2026-06-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @Parameter(example = "2026-06-30")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return PageResponse.of(
                scheduleUseCase.getByPeriod(getUserId(auth), start, end)
                        .map(ScheduleResponse::from),
                page, size
        );
    }

    // ── ACTIONS MÉTIER ────────────────────────────────────────────────────────

    @PutMapping("/{id}/publish")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Publier un planning",
               description = "Passe le planning de DRAFT à PUBLISHED. Le rend visible aux conducteurs.")
    public Mono<ScheduleResponse> publish(@PathVariable UUID id) {
        return scheduleUseCase.publish(id).map(ScheduleResponse::from);
    }

    @PatchMapping("/{id}/publish")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Publier un planning (alias PATCH Manager UI)")
    public Mono<ScheduleResponse> publishPatch(@PathVariable UUID id) {
        return publish(id);
    }

    @PutMapping("/{id}/archive")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Archiver un planning",
               description = "Archive le planning. Action irréversible.")
    public Mono<ScheduleResponse> archive(@PathVariable UUID id) {
        return scheduleUseCase.archive(id).map(ScheduleResponse::from);
    }

    @PatchMapping("/{id}/archive")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Archiver un planning (alias PATCH Manager UI)")
    public Mono<ScheduleResponse> archivePatch(@PathVariable UUID id) {
        return archive(id);
    }

    // ── MISE À JOUR ───────────────────────────────────────────────────────────

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Modifier un planning",
               description = "Met à jour le titre et les notes. Interdit sur un planning archivé.")
    public Mono<ScheduleResponse> update(
            @PathVariable UUID id,
            @RequestBody ScheduleRequest request) {

        ManageScheduleUseCase.UpdateScheduleCommand cmd =
                new ManageScheduleUseCase.UpdateScheduleCommand(
                        id, request.title(), request.notes()
                );
        return scheduleUseCase.update(cmd).map(ScheduleResponse::from);
    }

    // ── SUPPRESSION ───────────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Supprimer un planning")
    public Mono<Void> delete(@PathVariable UUID id) {
        return scheduleUseCase.delete(id);
    }
}
