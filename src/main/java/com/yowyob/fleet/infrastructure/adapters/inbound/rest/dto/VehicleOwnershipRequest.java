package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import java.util.UUID;

public record VehicleOwnershipRequest(
    UUID vehicleId,
    String usageRole, // "OWNER", "DRIVER", "LOGISTICS", "FLEET"
    boolean isPrimary,
    String validFrom,
    UUID partyId // Ajout explicite pour désigner le chauffeur
) {}