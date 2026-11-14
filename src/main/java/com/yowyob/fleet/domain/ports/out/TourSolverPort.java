package com.yowyob.fleet.domain.ports.out;

import com.yowyob.fleet.domain.model.tour.TourPlan;
import com.yowyob.fleet.domain.ports.in.ManageTourPlanningUseCase.SolveTourCommand;
import reactor.core.publisher.Mono;

/**
 * Port sortant : moteur d'optimisation mathématique / solveur de tournées.
 * Implémenté par l'adaptateur VRP-TW (OR-Tools / algorithme sous contraintes).
 */
public interface TourSolverPort {

    /**
     * Résout le problème VRP-TW avec l'algorithme d'optimisation avancé (OR-Tools).
     */
    Mono<TourPlan> solveOptimized(SolveTourCommand command);

    /**
     * Résout avec l'heuristique gloutonne simple de référence (baseline).
     */
    Mono<TourPlan> solveBaseline(SolveTourCommand command);
}

