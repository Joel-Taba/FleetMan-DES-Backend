package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO pour la mise à jour partielle (PATCH).
 * Seuls les champs non-nulls seront envoyés au service distant.
 */
public record VehiclePatchRequest(
    
    @Schema(description = "Correction de la marque (ex: Toyota)", example = "Toyota")
    String brand,

    @Schema(description = "Correction du modèle (ex: Hilux)", example = "Hilux")
    String model,

    @Schema(description = "Correction de la plaque d'immatriculation", example = "LT-999-NEW")
    String licensePlate,

    @Schema(description = "Correction du numéro de série (VIN)", example = "VIN-CORRECTED-001")
    String vehicleSerialNumber,

    @Schema(description = "Mise à jour de la couleur", example = "Noir")
    String color,

    @Schema(
        description = "Changement de statut opérationnel", 
        example = "MAINTENANCE",
        allowableValues = {"AVAILABLE", "ON_TRIP", "MAINTENANCE", "SOLD"}
    )
    String status
) {}