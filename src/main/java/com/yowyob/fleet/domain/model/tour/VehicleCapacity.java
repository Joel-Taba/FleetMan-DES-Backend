package com.yowyob.fleet.domain.model.tour;

import java.util.UUID;

/**
 * Caractéristiques capacitaires d'un véhicule disponible pour la tournée.
 * Périmètre G4 : 4 véhicules répartis sur les antennes.
 */
public record VehicleCapacity(
        UUID vehicleId,
        String licensePlate,
        UUID assignedDriverId,
        UUID homeDepotId,
        double maxWeightKg,
        double maxVolumeM3,
        int shiftStartMinutes,
        int shiftEndMinutes
) {
    public VehicleCapacity {
        if (vehicleId == null || homeDepotId == null) {
            throw new IllegalArgumentException("Le véhicule et son dépôt de rattachement sont obligatoires.");
        }
        if (maxWeightKg <= 0 || maxVolumeM3 <= 0) {
            throw new IllegalArgumentException("Les capacités maximales doivent être strictement positives.");
        }
    }
}

