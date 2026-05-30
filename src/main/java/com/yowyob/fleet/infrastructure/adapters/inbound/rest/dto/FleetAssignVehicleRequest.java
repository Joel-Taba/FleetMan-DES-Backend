package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * DTO pour l'assignation d'un véhicule existant à une flotte.
 */
public record FleetAssignVehicleRequest(
    @NotNull(message = "L'ID du véhicule est obligatoire")
    @Schema(description = "Identifiant unique du véhicule à intégrer à la flotte", example = "550e8400-e29b-41d4-a716-446655440000")
    UUID vehicleId
) {}