package com.yowyob.fleet.domain.ports.in;

import com.yowyob.fleet.domain.model.DriverScore;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Port d'entrée : cas d'utilisation pour le Scoring Conducteur.
 * Invoqué par DriverScoreController.
 */
public interface ComputeDriverScoreUseCase {

    // ── Lecture ───────────────────────────────────────────────────────────────

    /**
     * Récupère le score le plus récent d'un chauffeur pour un type de période.
     * Déclenche un calcul à la demande si aucun score n'existe.
     */
    Mono<DriverScore> getLatestScore(UUID driverId, DriverScore.PeriodType periodType);

    /**
     * Récupère le score d'un chauffeur pour une période spécifique.
     */
    Mono<DriverScore> getScoreById(UUID scoreId);

    /**
     * Historique des scores d'un chauffeur sur une plage de dates.
     */
    Flux<DriverScore> getScoreHistory(UUID driverId,
                                       DriverScore.PeriodType periodType,
                                       LocalDate from,
                                       LocalDate to);

    /**
     * Tous les scores du mois/semaine courant pour tous les chauffeurs d'une flotte.
     */
    Flux<DriverScore> getFleetScores(UUID fleetId, DriverScore.PeriodType periodType);

    /**
     * Classement des N meilleurs chauffeurs d'une flotte.
     * Triés par score décroissant.
     */
    Flux<DriverScore> getTopDrivers(UUID fleetId,
                                     DriverScore.PeriodType periodType,
                                     int limit);

    /**
     * Classement des N chauffeurs les plus à risque (score le plus bas).
     */
    Flux<DriverScore> getBottomDrivers(UUID fleetId,
                                        DriverScore.PeriodType periodType,
                                        int limit);

    // ── Calcul à la demande ───────────────────────────────────────────────────

    /**
     * Calcule et persiste le score d'un chauffeur pour une période donnée.
     * Remplace le score existant si un calcul existe déjà pour ce chauffeur/période.
     */
    Mono<DriverScore> calculateScore(UUID driverId,
                                      DriverScore.PeriodType periodType,
                                      LocalDate periodStart);

    /**
     * Calcule les scores de tous les chauffeurs d'une flotte pour une période.
     * Utilisé par le job planifié.
     */
    Flux<DriverScore> calculateFleetScores(UUID fleetId,
                                            DriverScore.PeriodType periodType,
                                            LocalDate periodStart);

    // ── Résumé flotte ─────────────────────────────────────────────────────────

    /**
     * Résumé de la distribution des scores dans une flotte.
     */
    Mono<FleetScoreSummaryDto> getFleetScoreSummary(UUID fleetId,
                                                     DriverScore.PeriodType periodType);

    record FleetScoreSummaryDto(
            UUID fleetId,
            DriverScore.PeriodType periodType,
            int totalDrivers,
            java.math.BigDecimal avgScore,
            java.math.BigDecimal minScore,
            java.math.BigDecimal maxScore,
            long excellenceCount,
            long goodCount,
            long satisfactoryCount,
            long warningCount,
            long insufficientCount
    ) {}
}
