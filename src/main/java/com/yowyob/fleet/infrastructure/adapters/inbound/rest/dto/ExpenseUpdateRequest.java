package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

@Schema(description = "Requête de mise à jour d'une dépense manuelle en attente")
public record ExpenseUpdateRequest(

        @Positive(message = "Le montant doit être strictement positif.")
        @Schema(description = "Nouveau montant en FCFA (optionnel)", example = "30000.00")
        BigDecimal amount,

        @Schema(description = "Nouvelle description (optionnelle)", example = "Amende de stationnement - RN1")
        String description
) {}
