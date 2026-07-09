package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import java.util.UUID;

/**
 * Payload front Manager — mise à jour partielle d'un chauffeur.
 */
public record ManagerDriverUpdateRequest(
        String firstName,
        String lastName,
        String email,
        String phone,
        String licenceNumber,
        String status,
        String photoUrl,
        UUID fleetId
) {}
