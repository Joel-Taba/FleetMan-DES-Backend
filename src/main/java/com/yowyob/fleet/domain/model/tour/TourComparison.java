package com.yowyob.fleet.domain.model.tour;

/**
 * Rapport comparatif entre le Solveur de Tournées (VRP-TW) et l'affectation simple (baseline gloutonne).
 * Exigence explicite du cahier des charges G4 §3.7 & barème R12.
 */
public record TourComparison(
        TourPlan optimizedPlan,
        TourPlan baselinePlan,
        double distanceGainKm,
        double distanceGainPercentage,
        double onTimeGainPercentage,
        int vehiclesSaved,
        long computationOverheadMs
) {
    public static TourComparison compare(TourPlan optimized, TourPlan baseline) {
        double distGain = baseline.totalDistanceKm() - optimized.totalDistanceKm();
        double distGainPct = baseline.totalDistanceKm() > 0
                ? (distGain / baseline.totalDistanceKm()) * 100.0
                : 0.0;
        double onTimeGain = optimized.onTimeRatePercentage() - baseline.onTimeRatePercentage();
        int vhcSaved = baseline.totalVehiclesUsed() - optimized.totalVehiclesUsed();
        long compTime = optimized.computationTimeMs() - baseline.computationTimeMs();

        return new TourComparison(
                optimized,
                baseline,
                distGain,
                distGainPct,
                onTimeGain,
                vhcSaved,
                compTime
        );
    }
}

