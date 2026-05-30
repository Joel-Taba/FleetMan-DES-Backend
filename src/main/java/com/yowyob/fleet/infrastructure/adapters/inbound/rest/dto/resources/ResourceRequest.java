package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.resources;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

public record ResourceRequest(
    @NotBlank(message = "Le code technique est obligatoire")
    @Schema(example = "TOYOTA", description = "Identifiant unique et invariable")
    String code,

    @NotBlank(message = "Le libellé est obligatoire")
    @Schema(example = "Toyota Motors", description = "Nom affiché dans l'interface")
    String label,

    @Schema(example = "Description facultative")
    String description
) {}