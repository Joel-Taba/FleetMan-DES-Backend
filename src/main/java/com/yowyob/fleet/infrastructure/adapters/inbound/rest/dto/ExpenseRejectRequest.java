package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Requête de rejet d'une dépense manuelle")
public record ExpenseRejectRequest(

        @NotBlank(message = "Le motif de rejet est obligatoire.")
        @Schema(description = "Motif du rejet (obligatoire)", example = "Dépense non justifiée — aucun justificatif fourni.")
        String rejectionReason
) {}
