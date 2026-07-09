package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.resources;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

public record ResourceRequest(
    @Schema(example = "TOYOTA", description = "Identifiant unique et invariable")
    String code,

    @Schema(example = "Toyota Motors", description = "Nom affiché dans l'interface")
    String label,

    @Schema(example = "TOYOTA", description = "Identifiant unique et invariable")
    String name,

    @Schema(example = "Description facultative")
    String description
) {
    public String normalizedCode() {
        String source = (code != null && !code.isBlank()) ? code : name;
        return source == null ? null : source.trim().toUpperCase().replaceAll("\\s+", "_");
    }

    public String normalizedLabel() {
        String source = (label != null && !label.isBlank()) ? label : name;
        return source == null ? null : source.trim();
    }
}