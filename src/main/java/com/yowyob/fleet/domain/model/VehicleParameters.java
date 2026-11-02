package com.yowyob.fleet.domain.model;

import java.time.LocalDate;
import java.util.UUID;

public class VehicleParameters {
    
    public record Financial(
        String insuranceNumber,
        LocalDate insuranceExpiryDate,
        LocalDate registrationDate,
        LocalDate purchaseDate,
        Integer depreciationRate,
        Float costPerKm
    ) {}

    public record Maintenance(
        LocalDate lastMaintenanceDate,
        LocalDate nextMaintenanceDue,
        String engineStatus, // 
        Integer batteryHealth,
        String maintenanceStatus
    ) {}

    public record Operational(
        Boolean status,
        Float currentSpeed,
        String fuelLevel,
        Float mileage,
        Float odometerReading,
        Float bearing,
        java.time.Instant timestamp,
        Location currentLocation
    ) {}

    public record Location(Double latitude, Double longitude) {}

    /** Cycle de vie : motif du statut courant et, pour un véhicule retiré du parc, sa sortie. */
    public record Lifecycle(
        String statusReason,
        java.time.Instant statusChangedAt,
        LocalDate retiredAt,
        String retirementType,
        String retirementReason,
        java.math.BigDecimal salePrice
    ) {}
}