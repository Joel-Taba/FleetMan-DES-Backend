package com.yowyob.fleet.domain.model.tour;

import java.util.List;
import java.util.UUID;

/**
 * Tournée affectée à un véhicule spécifique.
 * Respecte le départ et le retour au dépôt de rattachement.
 */
public record VehicleTour(
        UUID vehicleId,
        String licensePlate,
        UUID driverId,
        UUID homeDepotId,
        List<TourStop> stops,
        double totalDistanceKm,
        int totalDurationMinutes,
        double totalWeightKg,
        double totalVolumeM3,
        int onTimeDeliveriesCount,
        int delayedDeliveriesCount
) {
    public int getDeliveriesCount() {
        return stops != null ? stops.size() : 0;
    }
}

