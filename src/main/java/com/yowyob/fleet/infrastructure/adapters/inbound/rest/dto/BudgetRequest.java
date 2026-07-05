package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import com.yowyob.fleet.domain.model.Budget;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Requête de création d'un budget mensuel")
public record BudgetRequest(

        @NotNull(message = "La portée du budget est obligatoire.")
        @Schema(description = "Portée du budget (FLEET ou VEHICLE)", example = "FLEET",
                allowableValues = {"FLEET", "VEHICLE"})
        Budget.BudgetScope scope,

        @NotNull(message = "L'entité cible est obligatoire.")
        @Schema(description = "ID de la flotte (si scope=FLEET) ou du véhicule (si scope=VEHICLE)")
        UUID entityId,

        @NotNull(message = "Le mois du budget est obligatoire.")
        @Schema(description = "Mois du budget (format YYYY-MM-DD, normalisé au 1er du mois)",
                example = "2026-06-01")
        LocalDate budgetMonth,

        @NotNull(message = "Le montant est obligatoire.")
        @Positive(message = "Le montant doit être strictement positif.")
        @Schema(description = "Montant plafond en FCFA", example = "500000.00")
        BigDecimal amount,

        @Schema(description = "Notes ou contexte du budget (optionnel)",
                example = "Budget de juin pour la flotte Yaoundé-Centre")
        String notes
) {}
