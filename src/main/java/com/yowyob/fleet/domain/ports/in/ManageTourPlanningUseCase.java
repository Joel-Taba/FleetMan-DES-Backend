package com.yowyob.fleet.domain.ports.in;

import com.yowyob.fleet.domain.model.Mission;
import com.yowyob.fleet.domain.model.tour.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Port d'entrée : cas d'utilisation pour la planification et l'optimisation des tournées de livraison.
 * Conforme au cahier des charges G4 §3.7 (Multi-dépôts, fenêtres temporelles, capacités, comparaison).
 */
public interface ManageTourPlanningUseCase {

    // ── Commandes ─────────────────────────────────────────────────────────────

    record SolveTourCommand(
            UUID managerId,
            LocalDate planningDate,
            List<Depot> depots,
            List<VehicleCapacity> vehicles,
            List<DeliveryDemand> demands,
            boolean allowPenalizedLateArrival // true = retards pénalisés, false = fenêtres strictes
    ) {}

    // ── Use Cases ─────────────────────────────────────────────────────────────

    /**
     * Résout le problème VRP-TW multi-dépôts et retourne le plan de tournée optimisé.
     */
    Mono<TourPlan> solveTour(SolveTourCommand command);

    /**
     * Compare l'optimisation à une affectation gloutonne simple (baseline) sur le même jeu de données.
     */
    Mono<TourComparison> compareWithBaseline(SolveTourCommand command);

    /**
     * Valide un plan de tournée optimisé et génère automatiquement les Missions Fleetman associées en base.
     */
    Flux<Mission> validateAndExecuteTour(TourPlan tourPlan, LocalDate missionDate, UUID tenantId);
}

