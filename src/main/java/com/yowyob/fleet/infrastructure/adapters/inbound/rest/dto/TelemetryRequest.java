package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record TelemetryRequest(
    @NotNull
    @Schema(description = "Latitude GPS", example = "3.8480")
    Double lat,

    @NotNull
    @Schema(description = "Longitude GPS", example = "11.5021")
    Double lng,

    @Schema(description = "Vitesse instantanée en km/h", example = "45.5")
    Double speed
) {}