package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Payload front Manager — création chauffeur simplifiée (sans username/password).
 */
public record ManagerDriverCreateRequest(
        @NotNull UUID fleetId,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank String licenceNumber,
        String email,
        String phone
) {}
