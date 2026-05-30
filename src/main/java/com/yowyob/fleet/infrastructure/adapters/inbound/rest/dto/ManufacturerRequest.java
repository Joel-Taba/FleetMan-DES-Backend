package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import jakarta.validation.constraints.NotBlank;

public record ManufacturerRequest(
    @NotBlank(message = "Le code est obligatoire (ex: TOYOTA)") String code,
    @NotBlank(message = "Le libellé est obligatoire (ex: Toyota Motors)") String label,
    String description
) {}