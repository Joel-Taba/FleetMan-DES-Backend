package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Aligné sur le contrat front PendingSubscription.
 */
public record PendingSubscriptionDto(
        UUID id,
        String username,
        String email,
        String firstName,
        String lastName,
        String companyName,
        Instant createdAt,
        String phone,
        UUID requestedPlanId
) {}
