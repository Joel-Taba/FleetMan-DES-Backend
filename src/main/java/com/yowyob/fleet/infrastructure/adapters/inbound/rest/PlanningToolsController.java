package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.application.service.CompanyScopeService;
import com.yowyob.fleet.application.service.PlanningToolsService;
import com.yowyob.fleet.application.service.ResourceAccessService;
import com.yowyob.fleet.application.service.WorkRulesService;
import com.yowyob.fleet.domain.model.WorkTimeRules;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.ScheduleResponse;
import com.yowyob.fleet.infrastructure.config.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** Modèles de postes, règles de temps de travail, duplication et accusé de réception des plannings. */
@RestController
@RequiredArgsConstructor
@Tag(name = "16. Planning | Affectations")
@SecurityRequirement(name = "bearerAuth")
public class PlanningToolsController {

    private final PlanningToolsService tools;
    private final WorkRulesService workRules;
    private final CompanyScopeService companyScope;
    private final ResourceAccessService resourceAccess;
    private final com.yowyob.fleet.application.service.AccessScopeService accessScope;

    public record TemplateRequest(String name, LocalTime startTime, LocalTime endTime) {}

    public record WorkRulesRequest(double maxShiftHours, double minRestHours, double maxWeeklyHours, int maxConsecutiveDays) {}

    public record DuplicateRequest(LocalDate startDate, String title) {}

    public record DuplicateResponse(ScheduleResponse schedule, int copied, List<PlanningToolsService.SkippedShift> skipped) {}

    private Mono<UUID> company(Authentication auth) {
        return companyScope.resolveCompanyId(AuthPrincipal.userId(auth));
    }

    @GetMapping("/api/v1/planning/shift-templates")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN')")
    @Operation(summary = "Modèles de postes de l'entreprise (matin, après-midi, nuit…)")
    public Flux<PlanningToolsService.ShiftTemplate> templates(Authentication auth) {
        return company(auth).flatMapMany(tools::templates);
    }

    @PostMapping("/api/v1/planning/shift-templates")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN')")
    @Operation(summary = "Ajouter un modèle de poste")
    public Mono<PlanningToolsService.ShiftTemplate> createTemplate(@RequestBody TemplateRequest req, Authentication auth) {
        return company(auth).flatMap(c -> tools.saveTemplate(c, null, req.name(), req.startTime(), req.endTime()));
    }

    @PutMapping("/api/v1/planning/shift-templates/{id}")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN')")
    @Operation(summary = "Modifier un modèle de poste")
    public Mono<PlanningToolsService.ShiftTemplate> updateTemplate(@PathVariable UUID id, @RequestBody TemplateRequest req, Authentication auth) {
        return company(auth).flatMap(c -> tools.saveTemplate(c, id, req.name(), req.startTime(), req.endTime()));
    }

    @DeleteMapping("/api/v1/planning/shift-templates/{id}")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN')")
    @Operation(summary = "Supprimer un modèle de poste")
    public Mono<Void> deleteTemplate(@PathVariable UUID id, Authentication auth) {
        return company(auth).flatMap(c -> tools.deleteTemplate(c, id));
    }

    @GetMapping("/api/v1/organization/work-rules")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN')")
    @Operation(summary = "Règles de temps de travail de l'entreprise")
    public Mono<WorkTimeRules.Limits> workRulesOf(Authentication auth) {
        return company(auth).flatMap(workRules::limitsOfCompany);
    }

    @PutMapping("/api/v1/organization/work-rules")
    @PreAuthorize("hasRole('FLEET_ADMIN')")
    @Operation(summary = "Modifier les règles de temps de travail (administrateur)")
    public Mono<WorkTimeRules.Limits> updateWorkRules(@RequestBody WorkRulesRequest req, Authentication auth) {
        return companyScope.requireOwnCompany(AuthPrincipal.userId(auth))
                .flatMap(c -> workRules.updateLimits(c, new WorkTimeRules.Limits(req.maxShiftHours(), req.minRestHours(),
                        req.maxWeeklyHours(), req.maxConsecutiveDays())));
    }

    @PostMapping("/api/v1/schedules/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Dupliquer un planning sur une autre période (brouillon)",
               description = "Les postes qui ne passent plus les règles sont listés avec leur motif au lieu d'être copiés.")
    public Mono<DuplicateResponse> duplicate(@PathVariable UUID id, @RequestBody DuplicateRequest req, Authentication auth) {
        UUID actor = AuthPrincipal.userId(auth);
        return resourceAccess.assertScheduleAccess(actor, id)
                .then(accessScope.resolveLocalUserId(actor))
                .flatMap(manager -> tools.duplicate(id, manager, req.startDate(), req.title()))
                .map(r -> new DuplicateResponse(ScheduleResponse.from(r.schedule()), r.copied(), r.skipped()));
    }

    @PostMapping("/api/v1/schedules/{id}/acknowledge")
    @PreAuthorize("hasRole('FLEET_DRIVER')")
    @Operation(summary = "Le chauffeur accuse réception de ses postes de ce planning")
    public Mono<java.util.Map<String, Long>> acknowledgeSchedule(@PathVariable UUID id, Authentication auth) {
        return accessScope.resolveLocalUserId(AuthPrincipal.userId(auth))
                .flatMap(driver -> workRules.acknowledge(driver, id, null))
                .map(n -> java.util.Map.of("acknowledged", n));
    }
}
