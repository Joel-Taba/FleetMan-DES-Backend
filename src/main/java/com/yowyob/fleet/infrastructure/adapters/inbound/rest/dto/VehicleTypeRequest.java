package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import jakarta.validation.constraints.NotBlank;

public record VehicleTypeRequest(
    @NotBlank(message = "Le code technique est obligatoire (ex: HEAVY_TRUCK)")
    String code,
    
    @NotBlank(message = "Le libellé est obligatoire (ex: Poids Lourd)")
    String label,
    
    String description
) {}