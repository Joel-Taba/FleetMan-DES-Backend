package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import com.yowyob.fleet.domain.model.Incident;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

@Schema(description = "Requête de mise à jour partielle d'un incident (tous les champs sont optionnels)")
public record IncidentUpdateRequest(

        @Schema(description = "Description mise à jour", example = "Accident impliquant un camion sur la RN1.")
        String description,

        @Schema(description = "Niveau de gravité mis à jour", example = "CRITICAL",
                allowableValues = {"LOW", "MEDIUM", "HIGH", "CRITICAL"})
        Incident.Severity severity,

        @PositiveOrZero(message = "Le coût ne peut pas être négatif.")
        @Schema(description = "Coût réel mis à jour en FCFA", example = "250000.00")
        BigDecimal cost,

        @Schema(description = "Rapport de résolution ou d'investigation", example = "Véhicule remorqué au garage. Réparation estimée à 3 jours.")
        String report,

        @Schema(description = "Nom du témoin", example = "Marie Nguema")
        String witnessName,

        @Schema(description = "Contact du témoin", example = "+237699887766")
        String witnessContact,

        @Schema(description = "Numéro de procès-verbal de police", example = "PV-2026-00123")
        String policeReportNumber,

        @Schema(description = "Numéro de déclaration d'assurance", example = "ASS-2026-00456")
        String insuranceClaimNumber,

        @Schema(description = "Longitude GPS mise à jour", example = "11.5021")
        Double longitude,

        @Schema(description = "Latitude GPS mise à jour", example = "3.8480")
        Double latitude
) {}
