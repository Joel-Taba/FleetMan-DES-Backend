package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import com.yowyob.fleet.domain.model.Expense;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Requête de création d'une dépense manuelle (FINE, TOLL, OTHER uniquement)")
public record ExpenseRequest(

        @NotNull(message = "Le type de dépense est obligatoire.")
        @Schema(description = "Type de dépense manuelle", example = "FINE",
                allowableValues = {"FINE", "TOLL", "OTHER"})
        Expense.ExpenseType expenseType,

        @NotNull(message = "Le montant est obligatoire.")
        @Positive(message = "Le montant doit être strictement positif.")
        @Schema(description = "Montant de la dépense en FCFA", example = "25000.00")
        BigDecimal amount,

        @Schema(description = "Description de la dépense", example = "Amende de stationnement - RN1 Yaoundé")
        String description,

        @Schema(description = "Date et heure de la dépense (optionnel — défaut : maintenant)",
                example = "2026-06-01T14:30:00")
        LocalDateTime expenseDate,

        @NotNull(message = "Le véhicule est obligatoire.")
        @Schema(description = "ID du véhicule concerné")
        UUID vehicleId,

        @Schema(description = "ID du chauffeur impliqué (optionnel)")
        UUID driverId
) {}
