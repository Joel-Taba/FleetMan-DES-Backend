package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank(message = "L'identifiant (email) est obligatoire") 
    String identifier,
    
    @NotBlank(message = "Le mot de passe est obligatoire") 
    String password
) {}