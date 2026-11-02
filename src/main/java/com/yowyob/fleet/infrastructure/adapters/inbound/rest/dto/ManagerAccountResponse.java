package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Gestionnaire (employé de l'entreprise) vu par son administrateur. */
@Schema(description = "Gestionnaire de flotte et état de son compte")
public record ManagerAccountResponse(
        UUID id,
        String username,
        String email,
        String phone,
        String firstName,
        String lastName,
        List<String> roles,
        String photoUrl,
        String companyName,
        boolean isActive,
        Instant lastLoginAt,
        @Schema(description = "ACTIVE, SUSPENDED (motif) ou CLOSED (clôturé, historique conservé)")
        String accountStatus,
        String suspensionReason,
        Instant statusChangedAt,
        long fleetCount
) {}
