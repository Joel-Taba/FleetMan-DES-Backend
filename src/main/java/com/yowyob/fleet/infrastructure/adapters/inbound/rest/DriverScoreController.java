package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.domain.model.DriverScore;
import com.yowyob.fleet.domain.ports.in.ComputeDriverScoreUseCase;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.DriverScoreResponse;
import com.yowyob.fleet.infrastructure.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/scoring")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_SCORING)
@SecurityRequirement(name = "bearerAuth")
public class DriverScoreController {

    private final ComputeDriverScoreUseCase scoringUseCase;

    private UUID getUserId(Authentication auth) {
        return ((AuthPort.UserDetail) auth.getPrincipal()).id();
    }

    // ── Lecture par chauffeur ─────────────────────────────────────────────────

    @GetMapping("/drivers/{driverId}/latest")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Dernier score d'un chauffeur",
               description = "Retourne le score le plus récent d'un chauffeur. Déclenche un calcul automatique si aucun score n'existe encore pour la période courante.")
    public Mono<DriverScoreResponse> getLatestScore(
            @Parameter(description = "ID du chauffeur") @PathVariable UUID driverId,
            @Parameter(description = "Période (WEEKLY ou MONTHLY)", example = "MONTHLY")
            @RequestParam(defaultValue = "MONTHLY") DriverScore.PeriodType periodType) {
        return scoringUseCase.getLatestScore(driverId, periodType).map(DriverScoreResponse::from);
    }

    @GetMapping("/scores/{id}")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Détail d'un score par ID")
    public Mono<DriverScoreResponse> getById(
            @Parameter(description = "ID du score") @PathVariable UUID id) {
        return scoringUseCase.getScoreById(id).map(DriverScoreResponse::from);
    }

    @GetMapping("/drivers/{driverId}/history")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Historique des scores d'un chauffeur",
               description = "Retourne l'évolution du score sur une plage de dates. Idéal pour afficher un graphique de tendance.")
    public Flux<DriverScoreResponse> getHistory(
            @Parameter(description = "ID du chauffeur") @PathVariable UUID driverId,
            @Parameter(description = "Période (WEEKLY ou MONTHLY)", example = "MONTHLY")
            @RequestParam(defaultValue = "MONTHLY") DriverScore.PeriodType periodType,
            @Parameter(description = "Date de début", example = "2026-01-01")
            @RequestParam LocalDate from,
            @Parameter(description = "Date de fin", example = "2026-06-30")
            @RequestParam LocalDate to) {
        return scoringUseCase.getScoreHistory(driverId, periodType, from, to)
                .map(DriverScoreResponse::from);
    }

    // ── Lecture par flotte ────────────────────────────────────────────────────

    @GetMapping("/fleets/{fleetId}/scores")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Scores de tous les chauffeurs d'une flotte",
               description = "Retourne le score de la période courante pour tous les chauffeurs de la flotte, triés par score décroissant.")
    public Flux<DriverScoreResponse> getFleetScores(
            @Parameter(description = "ID de la flotte") @PathVariable UUID fleetId,
            @Parameter(description = "Période (WEEKLY ou MONTHLY)", example = "MONTHLY")
            @RequestParam(defaultValue = "MONTHLY") DriverScore.PeriodType periodType) {
        return scoringUseCase.getFleetScores(fleetId, periodType).map(DriverScoreResponse::from);
    }

    @GetMapping("/fleets/{fleetId}/top")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Classement des meilleurs chauffeurs",
               description = "Retourne les N meilleurs chauffeurs d'une flotte selon leur score de la période courante.")
    public Flux<DriverScoreResponse> getTopDrivers(
            @Parameter(description = "ID de la flotte") @PathVariable UUID fleetId,
            @Parameter(description = "Période", example = "MONTHLY")
            @RequestParam(defaultValue = "MONTHLY") DriverScore.PeriodType periodType,
            @Parameter(description = "Nombre de chauffeurs à retourner (1-100)", example = "5")
            @RequestParam(defaultValue = "5") int limit) {
        return scoringUseCase.getTopDrivers(fleetId, periodType, limit).map(DriverScoreResponse::from);
    }

    @GetMapping("/fleets/{fleetId}/bottom")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Chauffeurs les plus à risque",
               description = "Retourne les N chauffeurs avec les scores les plus bas. Idéal pour cibler les actions correctives.")
    public Flux<DriverScoreResponse> getBottomDrivers(
            @Parameter(description = "ID de la flotte") @PathVariable UUID fleetId,
            @Parameter(description = "Période", example = "MONTHLY")
            @RequestParam(defaultValue = "MONTHLY") DriverScore.PeriodType periodType,
            @Parameter(description = "Nombre de chauffeurs à retourner (1-100)", example = "5")
            @RequestParam(defaultValue = "5") int limit) {
        return scoringUseCase.getBottomDrivers(fleetId, periodType, limit).map(DriverScoreResponse::from);
    }

    @GetMapping("/fleets/{fleetId}/summary")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Résumé des scores d'une flotte",
               description = "Distribution des badges, score moyen/min/max. Utilisé pour le dashboard Manager.")
    public Mono<ComputeDriverScoreUseCase.FleetScoreSummaryDto> getFleetSummary(
            @Parameter(description = "ID de la flotte") @PathVariable UUID fleetId,
            @Parameter(description = "Période", example = "MONTHLY")
            @RequestParam(defaultValue = "MONTHLY") DriverScore.PeriodType periodType) {
        return scoringUseCase.getFleetScoreSummary(fleetId, periodType);
    }

    // ── Calcul à la demande ───────────────────────────────────────────────────

    @PostMapping("/drivers/{driverId}/calculate")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Calculer le score d'un chauffeur",
               description = "Force le recalcul du score d'un chauffeur pour une période spécifique. " +
                             "Remplace le score existant si présent.")
    public Mono<DriverScoreResponse> calculateScore(
            @Parameter(description = "ID du chauffeur") @PathVariable UUID driverId,
            @Parameter(description = "Période", example = "MONTHLY")
            @RequestParam(defaultValue = "MONTHLY") DriverScore.PeriodType periodType,
            @Parameter(description = "Début de la période (YYYY-MM-DD, défaut : début du mois courant)")
            @RequestParam(required = false) LocalDate periodStart) {

        LocalDate start = periodStart != null ? periodStart
                : (periodType == DriverScore.PeriodType.MONTHLY
                        ? LocalDate.now().withDayOfMonth(1)
                        : LocalDate.now().with(java.time.DayOfWeek.MONDAY));

        return scoringUseCase.calculateScore(driverId, periodType, start)
                .map(DriverScoreResponse::from);
    }

    @PostMapping("/fleets/{fleetId}/calculate")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Calculer les scores de toute une flotte",
               description = "Déclenche le recalcul des scores pour tous les chauffeurs actifs de la flotte.")
    public Flux<DriverScoreResponse> calculateFleetScores(
            @Parameter(description = "ID de la flotte") @PathVariable UUID fleetId,
            @Parameter(description = "Période", example = "MONTHLY")
            @RequestParam(defaultValue = "MONTHLY") DriverScore.PeriodType periodType,
            @Parameter(description = "Début de la période (YYYY-MM-DD)")
            @RequestParam(required = false) LocalDate periodStart) {

        LocalDate start = periodStart != null ? periodStart
                : (periodType == DriverScore.PeriodType.MONTHLY
                        ? LocalDate.now().withDayOfMonth(1)
                        : LocalDate.now().with(java.time.DayOfWeek.MONDAY));

        return scoringUseCase.calculateFleetScores(fleetId, periodType, start)
                .map(DriverScoreResponse::from);
    }
}
