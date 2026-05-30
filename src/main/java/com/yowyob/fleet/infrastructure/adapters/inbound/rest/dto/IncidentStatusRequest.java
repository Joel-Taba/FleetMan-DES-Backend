package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import com.yowyob.fleet.domain.model.Incident;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Requête de mise à jour du statut d'un incident")
public record IncidentStatusRequest(

        @NotNull(message = "Le nouveau statut est obligatoire.")
        @Schema(description = "Nouveau statut dans le cycle de vie de l'incident", example = "UNDER_INVESTIGATION",
                allowableValues = {"REPORTED", "UNDER_INVESTIGATION", "RESOLVED", "CLOSED"})
        Incident.Status status
) {}
