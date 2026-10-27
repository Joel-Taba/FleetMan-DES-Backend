package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VehicleTypeRequest(
    @NotBlank(message = "Le code technique est obligatoire (ex: HEAVY_TRUCK)")
    String code,
    
    @NotBlank(message = "Le libellé est obligatoire (ex: Poids Lourd)")
    String label,
    
    String description,

    /** Catégorie de permis exigée (A, B, C, D, E) ; déduite du code si absente. */
    @Pattern(regexp = "^[A-Ea-e]?$", message = "Catégorie de permis invalide (A, B, C, D ou E)")
    String requiredLicenseCategory
) {
    public VehicleTypeRequest(String code, String label, String description) {
        this(code, label, description, null);
    }
}
