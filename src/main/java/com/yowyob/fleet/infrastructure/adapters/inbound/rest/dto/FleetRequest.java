package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record FleetRequest(
        @NotBlank(message = "Le nom de la flotte est obligatoire") @Schema(description = "Nom commercial de la flotte (ex: Zone Douala Nord)", example = "Douala Express") String name,

        @Schema(description = "Numéro du Dispatching / Contact d'urgence pour cette flotte", example = "+237699000000") String phoneNumber,

        @Schema(description = "ID du gestionnaire à assigner (optionnel, si absent le créateur est utilisé)") UUID managerId) {
}