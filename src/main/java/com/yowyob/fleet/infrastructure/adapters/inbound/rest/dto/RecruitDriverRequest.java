package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record RecruitDriverRequest(
    @NotBlank(message = "L'identifiant est obligatoire")
    @Schema(description = "Email, Nom d'utilisateur ou Téléphone du chauffeur", example = "chauffeur@yowyob.com")
    String identifier
) {}