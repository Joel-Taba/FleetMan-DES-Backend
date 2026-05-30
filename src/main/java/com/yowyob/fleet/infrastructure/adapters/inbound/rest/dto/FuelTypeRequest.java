package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import jakarta.validation.constraints.NotBlank;

public record FuelTypeRequest(
    @NotBlank(message = "Le code est obligatoire (ex: DIESEL)") String code,
    @NotBlank(message = "Le libellé est obligatoire (ex: Gasoil)") String label,
    String description
) {}