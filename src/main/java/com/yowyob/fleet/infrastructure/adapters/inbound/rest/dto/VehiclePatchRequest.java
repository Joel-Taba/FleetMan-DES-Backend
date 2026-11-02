package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * DTO pour la mise à jour partielle (PATCH) : seuls les champs non nuls sont appliqués.
 * La sortie du parc passe par POST /vehicles/{id}/retire, le statut « en trajet » par les trajets.
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

    UUID vehicleTypeId,
    Integer manufacturingYear,

    @Schema(allowableValues = {"DIESEL", "GASOLINE", "HYBRID", "ELECTRIC", "LPG"})
    String fuelType,

    @Schema(allowableValues = {"MANUAL", "AUTOMATIC"})
    String transmissionType,

    Double tankCapacity,
    Integer totalSeatNumber,
    Double averageFuelConsumption,

    @Schema(
        description = "Changement de statut",
        example = "MAINTENANCE",
        allowableValues = {"AVAILABLE", "MAINTENANCE", "IMMOBILIZED"}
    )
    String status,

    @Schema(description = "Motif du statut (obligatoire pour IMMOBILIZED)", example = "Assurance expirée")
    String statusReason
) {}
