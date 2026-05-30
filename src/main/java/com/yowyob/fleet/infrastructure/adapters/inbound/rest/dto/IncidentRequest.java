package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import com.yowyob.fleet.domain.model.Incident;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Requête de déclaration d'un incident terrain")
public record IncidentRequest(

        @NotNull(message = "Le type d'incident est obligatoire.")
        @Schema(description = "Type d'incident", example = "BREAKDOWN",
                allowableValues = {"ACCIDENT", "BREAKDOWN", "THEFT", "VANDALISM", "TRAFFIC_VIOLATION", "OTHER"})
        Incident.Type type,

        @Schema(description = "Description détaillée de l'incident", example = "Panne moteur sur la RN1 à 50km de Yaoundé.")
        String description,

        @Schema(description = "Niveau de gravité (défaut : MEDIUM)", example = "HIGH",
                allowableValues = {"LOW", "MEDIUM", "HIGH", "CRITICAL"})
        Incident.Severity severity,

        @PositiveOrZero(message = "Le coût ne peut pas être négatif.")
        @Schema(description = "Coût estimé de l'incident en FCFA (optionnel)", example = "150000.00")
        BigDecimal cost,

        @NotNull(message = "Le véhicule est obligatoire.")
        @Schema(description = "ID du véhicule impliqué", example = "123e4567-e89b-12d3-a456-426614174000")
        UUID vehicleId,

        @Schema(description = "ID du chauffeur impliqué (optionnel)", example = "456e7890-e89b-12d3-a456-426614174001")
        UUID driverId,

        @Schema(description = "Longitude GPS du lieu de l'incident (optionnel)", example = "11.5021")
        Double longitude,

        @Schema(description = "Latitude GPS du lieu de l'incident (optionnel)", example = "3.8480")
        Double latitude,

        @Schema(description = "Nom du témoin (optionnel)", example = "Jean Dupont")
        String witnessName,

        @Schema(description = "Contact du témoin (optionnel)", example = "+237612345678")
        String witnessContact,

        @Schema(description = "Identifiant de la personne signalant l'incident", example = "Chauffeur Paul Biya")
        String reportedBy
) {}
