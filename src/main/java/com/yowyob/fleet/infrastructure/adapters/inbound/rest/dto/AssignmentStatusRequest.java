package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import com.yowyob.fleet.domain.model.Assignment;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Schema(description = "Requête de changement de statut d'une affectation")
public record AssignmentStatusRequest(

        @NotNull(message = "Le statut est obligatoire")
        @Schema(description = "Nouveau statut",
                allowableValues = {"IN_PROGRESS","COMPLETED","CANCELLED","NO_SHOW"})
        Assignment.Status status,

        @Schema(description = "Kilométrage réel (obligatoire si statut = COMPLETED)",
                example = "42.3")
        BigDecimal actualKm
) {}
