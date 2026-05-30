package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record StartTripRequest(
    @Schema(description = "ID du véhicule. Optionnel si le chauffeur a déjà un véhicule assigné.", example = "123e4567-e89b-12d3-a456-426614174000")
    UUID vehicleId
) {}