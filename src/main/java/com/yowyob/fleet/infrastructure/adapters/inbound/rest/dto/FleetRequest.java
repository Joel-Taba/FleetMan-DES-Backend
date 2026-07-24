package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record FleetRequest(
    @NotBlank(message = "Le nom de la flotte est obligatoire")
    @Schema(description = "Nom commercial de la flotte (ex: Zone Douala Nord)", example = "Douala Express")
    String name,

    @Pattern(
        regexp = "^\\+?[0-9]{8,15}$",
        message = "Le numéro de téléphone ne doit contenir que des chiffres (avec un + optionnel en préfixe)."
    )
    @Schema(description = "Numéro du Dispatching / Contact d'urgence pour cette flotte", example = "+237699000000")
    String phoneNumber,

    @DecimalMin(value = "0", inclusive = true, message = "Le budget mensuel doit être positif.")
    @Schema(description = "Budget mensuel alloué à la flotte (FCFA)", example = "500000")
    BigDecimal monthlyBudget
) {}