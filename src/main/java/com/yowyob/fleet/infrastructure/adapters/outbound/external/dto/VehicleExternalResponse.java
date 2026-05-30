package com.yowyob.fleet.infrastructure.adapters.outbound.external.dto;

import java.util.UUID;

/**
 * Mappe exactement le JSON de réponse du Swagger "Vehicle Service".
 * Modif: Dates en String pour gérer le format sans Timezone du service distant.
 */
public record VehicleExternalResponse(
    UUID vehicleId,
    UUID vehicleMakeId,
    UUID vehicleModelId,
    UUID transmissionTypeId,
    UUID manufacturerId,
    UUID vehicleSizeId,
    UUID vehicleTypeId,
    UUID fuelTypeId,
    
    String vehicleSerialNumber,
    String vehicleSerialPhoto, // URL
    String registrationNumber,
    String registrationPhoto, // URL
    String registrationExpiryDate,
    
    Double tankCapacity,
    Integer luggageMaxCapacity,
    Integer totalSeatNumber,
    Double averageFuelConsumptionPerKm,
    Double mileageAtStart,
    Double mileageSinceCommissioning,
    Integer vehicleAgeAtStart,
    
    String brand,
    String createdAt, // <-- Changé de Instant à String
    String updatedAt  // <-- Changé de Instant à String
) {}