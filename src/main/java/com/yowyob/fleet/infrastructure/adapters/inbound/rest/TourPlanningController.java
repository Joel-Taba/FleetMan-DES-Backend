package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.domain.model.Mission;
import com.yowyob.fleet.domain.model.tour.TourComparison;
import com.yowyob.fleet.domain.model.tour.TourPlan;
import com.yowyob.fleet.domain.ports.in.ManageTourPlanningUseCase;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.MissionResponse;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.SolveTourRequest;
import com.yowyob.fleet.infrastructure.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/tours")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_TOURS)
@SecurityRequirement(name = "bearerAuth")
public class TourPlanningController {

    private final ManageTourPlanningUseCase tourPlanningUseCase;

    private UUID getUserId(Authentication auth) {
        if (auth.getPrincipal() instanceof AuthPort.UserDetail userDetail) {
            return userDetail.id();
        }
        return UUID.fromString(auth.getName());
    }

    @PostMapping("/solve")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Résoudre une tournée de livraisons (VRP-TW)",
               description = "Résout le problème multi-dépôts (2 antennes), multi-véhicules (4 véhicules) et multi-livraisons (20 colis). " +
                             "Respecte les capacités en poids et volume, les horaires et le retour au dépôt d'attache.")
    public Mono<TourPlan> solveTour(
            @Valid @RequestBody SolveTourRequest request,
            Authentication auth) {
        UUID managerId = getUserId(auth);

        ManageTourPlanningUseCase.SolveTourCommand cmd = new ManageTourPlanningUseCase.SolveTourCommand(
                managerId,
                request.planningDate() != null ? request.planningDate() : LocalDate.now(),
                request.depots(),
                request.vehicles(),
                request.demands(),
                request.allowPenalizedLateArrival()
        );

        return tourPlanningUseCase.solveTour(cmd);
    }

    @PostMapping("/compare")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Comparer l'optimisation à l'affectation de référence (Baseline)",
               description = "Compare la solution du solveur VRP-TW à une affectation gloutonne simple : distance totale, taux de ponctualité, véhicules économisés et temps de calcul.")
    public Mono<TourComparison> compareWithBaseline(
            @Valid @RequestBody SolveTourRequest request,
            Authentication auth) {
        UUID managerId = getUserId(auth);

        ManageTourPlanningUseCase.SolveTourCommand cmd = new ManageTourPlanningUseCase.SolveTourCommand(
                managerId,
                request.planningDate() != null ? request.planningDate() : LocalDate.now(),
                request.depots(),
                request.vehicles(),
                request.demands(),
                request.allowPenalizedLateArrival()
        );

        return tourPlanningUseCase.compareWithBaseline(cmd);
    }

    @PostMapping("/validate")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Valider et exécuter le plan de tournée",
               description = "Valide le plan optimisé et instancie automatiquement les missions réelles correspondantes dans Fleetman.")
    public Flux<MissionResponse> validateAndExecute(
            @RequestBody TourPlan tourPlan,
            @RequestParam(required = false) LocalDate missionDate,
            Authentication auth) {
        LocalDate date = missionDate != null ? missionDate : LocalDate.now();
        return tourPlanningUseCase.validateAndExecuteTour(tourPlan, date, null)
                .map(MissionResponse::fromDomain);
    }
}

