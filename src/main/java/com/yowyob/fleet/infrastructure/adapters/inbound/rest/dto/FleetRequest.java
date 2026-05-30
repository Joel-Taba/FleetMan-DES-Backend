package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record FleetRequest(
    @NotBlank(message = "Le nom de la flotte est obligatoire") 
    @Schema(description = "Nom commercial de la flotte (ex: Zone Douala Nord)", example = "Douala Express")
    String name,
    
    @Schema(description = "Numéro du Dispatching / Contact d'urgence pour cette flotte", example = "+237699000000")
    String phoneNumber
) {}