package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

@Schema(description = "Requête de mise à jour d'un budget mensuel")
public record BudgetUpdateRequest(

        @Positive(message = "Le montant doit être strictement positif.")
        @Schema(description = "Nouveau montant plafond en FCFA (optionnel)", example = "750000.00")
        BigDecimal amount,

        @Schema(description = "Nouvelles notes (optionnel)", example = "Budget révisé suite à l'ajout de 3 véhicules")
        String notes
) {}
