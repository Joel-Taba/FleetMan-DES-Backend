package com.yowyob.fleet.domain.model.tour;

import java.util.UUID;

/**
 * Arrêt ordonné au sein d'une tournée véhicule.
 */
public record TourStop(
        int sequenceOrder,
        UUID deliveryDemandId,
        String parcelId,
        double latitude,
        double longitude,
        int arrivalTimeMinutes,
        int departureTimeMinutes,
        int targetWindowStartMinutes,
        int targetWindowEndMinutes,
        int delayMinutes,           // Retard constaté par rapport à la fenêtre
        double cumulativeWeightKg,
        double cumulativeVolumeM3,
        double distanceFromPreviousKm
) {
    public boolean isOnTime() {
        return delayMinutes <= 0;
    }
}

