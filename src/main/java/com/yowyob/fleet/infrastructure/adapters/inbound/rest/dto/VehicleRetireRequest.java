package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Sortie définitive du parc (vente, réforme, vol…) : le véhicule et son historique sont conservés.")
public record VehicleRetireRequest(
    @Schema(description = "Date de sortie (aujourd'hui ou passée)", example = "2026-10-29")
    LocalDate retiredAt,

    @Schema(allowableValues = {"SOLD", "SCRAPPED", "STOLEN", "TOTAL_LOSS", "RETURNED", "OTHER"})
    String retirementType,

    @Schema(description = "Motif / précisions (obligatoire pour OTHER)")
    String reason,

    @Schema(description = "Prix de cession (vente uniquement)")
    BigDecimal salePrice
) {}
