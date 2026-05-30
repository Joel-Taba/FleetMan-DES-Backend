package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Requête de création ou mise à jour d'une maintenance véhicule")
public record MaintenanceRequest(

        @NotBlank(message = "L'objet de la maintenance est obligatoire.")
        @Schema(description = "Objet / titre de l'intervention", example = "Vidange moteur + filtre à huile")
        String subject,

        @PositiveOrZero(message = "Le coût ne peut pas être négatif.")
        @Schema(description = "Coût de l'intervention en FCFA (optionnel)", example = "45000.00")
        BigDecimal cost,

        @Schema(description = "Rapport détaillé de l'intervention (optionnel)", example = "Remplacement filtre à huile, vidange complète.")
        String report,

        @NotNull(message = "Le véhicule est obligatoire.")
        @Schema(description = "ID du véhicule concerné", example = "123e4567-e89b-12d3-a456-426614174000")
        UUID vehicleId,

        @Schema(description = "ID du chauffeur impliqué (optionnel)", example = "456e7890-e89b-12d3-a456-426614174001")
        UUID driverId,

        @Schema(description = "Longitude GPS du lieu d'intervention (optionnel)", example = "11.5021")
        Double longitude,

        @Schema(description = "Latitude GPS du lieu d'intervention (optionnel)", example = "3.8480")
        Double latitude,

        @Schema(description = "Nom du lieu d'intervention (optionnel)", example = "Garage Central Yaoundé")
        String locationName
) {}
