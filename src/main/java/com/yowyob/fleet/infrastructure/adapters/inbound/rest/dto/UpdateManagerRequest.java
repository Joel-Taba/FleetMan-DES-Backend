package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateManagerRequest(
    @NotBlank(message = "Le nom de l'entreprise est obligatoire")
    String companyName
) {}