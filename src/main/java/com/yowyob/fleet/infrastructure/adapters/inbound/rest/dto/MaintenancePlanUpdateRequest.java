package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;

@Schema(description = "Requête de mise à jour d'un plan de maintenance préventive")
public record MaintenancePlanUpdateRequest(
        @Schema(description = "Nouveau libellé (optionnel)") String label,
        @Schema(description = "Nouvelle description (optionnel)") String description,
        @Positive @Schema(description = "Nouvel intervalle km (optionnel)") Integer intervalKm,
        @Positive @Schema(description = "Nouvel intervalle jours (optionnel)") Integer intervalDays,
        @Positive @Schema(description = "Nouveau seuil préalerte km (optionnel)") Integer preAlertKm,
        @Positive @Schema(description = "Nouveau seuil préalerte jours (optionnel)") Integer preAlertDays,
        @Schema(description = "Activer/désactiver le plan (optionnel)") Boolean active
) {}
