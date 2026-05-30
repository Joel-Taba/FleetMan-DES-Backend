package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record VehicleRegistrationRequest(
    // --- Body pour POST /vehicles/simplified ---
    
    @NotBlank(message = "La marque est obligatoire")
    @Schema(example = "Hyundai")
    String makeName,

    @NotBlank(message = "Le modèle est obligatoire")
    @Schema(example = "Tucson N-Line")
    String modelName,

    @Schema(example = "DCT-7", description = "Type de transmission")
    String transmissionType,

    @Schema(example = "Hyundai Motor Group")
    String manufacturerName,

    @Schema(example = "SUV Compact")
    String sizeName,

    @Schema(example = "Personnel")
    String typeName,

    @NotBlank(message = "Le type de carburant est obligatoire")
    @Schema(example = "Hybride Essence")
    String fuelTypeName,

    @Schema(example = "KMH-TEST-8293-X92", description = "Numéro de série (VIN)")
    String vehicleSerialNumber,

    // URLs des photos (Si déjà hébergées, sinon uploadées après via les endpoints dédiés)
    String vehicleSerialPhoto, 
    String registrationPhoto,

    @NotBlank(message = "L'immatriculation est obligatoire")
    @Schema(example = "CM-TEST-882-AB")
    String registrationNumber,

    @Schema(example = "2028-06-15T12:00:00")
    String registrationExpiryDate,

    // Données techniques
    Double tankCapacity,
    Integer luggageMaxCapacity,
    Integer totalSeatNumber,
    Double averageFuelConsumptionPerKm,
    Double mileageAtStart,
    Double mileageSinceCommissioning,
    Integer vehicleAgeAtStart,
    
    // --- Champs Locaux (Non envoyés au distant) ---
    @NotNull(message = "La catégorie locale est obligatoire")
    UUID vehicleTypeId,
    
    // redondance pour compatibilité si nécessaire
    String brand ,
     // Champs financiers (supposés Double)
    Boolean stateTax,       // Était Double
    Boolean tollCharge,     // Était Double
    Boolean driverAllowance,// Était Double
    Boolean carParking,     // Était Double

    // Options et services (supposés Boolean)
    Boolean comfortable,
    Boolean petsAllow,
    Boolean wifi,
    Boolean soft, // Suspension soft ?
    Boolean screen,
    Boolean alarm,
    Boolean pickupAndDrop,
    Boolean internet,
    Boolean airConditioned
) {}