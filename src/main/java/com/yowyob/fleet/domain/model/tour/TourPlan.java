package com.yowyob.fleet.domain.model.tour;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Plan de tournée complet résolu par le solveur d'optimisation.
 * Contient les métriques de performance et l'explication en cas d'infaisabilité.
 */
public record TourPlan(
        UUID id,
        UUID managerId,
        String solverType,          // "OR_TOOLS_VRP_TW" ou "GREEDY_BASELINE"
        boolean feasible,
        String infeasibilityReason, // Si feasible = false, explications claires des contraintes violées
        List<VehicleTour> vehicleTours,
        int totalVehiclesUsed,
        int totalDeliveriesServed,
        double totalDistanceKm,
        int totalDurationMinutes,
        double onTimeRatePercentage,
        long computationTimeMs,
        LocalDateTime solvedAt
) {
    public static TourPlan infeasible(UUID managerId, String solverType, String reason, long computationTimeMs) {
        return new TourPlan(
                UUID.randomUUID(),
                managerId,
                solverType,
                false,
                reason,
                List.of(),
                0,
                0,
                0.0,
                0,
                0.0,
                computationTimeMs,
                LocalDateTime.now()
        );
    }
}

