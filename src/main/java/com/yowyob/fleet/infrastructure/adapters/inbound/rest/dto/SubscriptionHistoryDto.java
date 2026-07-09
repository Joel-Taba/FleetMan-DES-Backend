package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Aligné sur le contrat front SubscriptionHistoryItem.
 */
public record SubscriptionHistoryDto(
        UUID id,
        String username,
        String email,
        String firstName,
        String lastName,
        String companyName,
        Instant requestedAt,
        Instant processedAt,
        String status,
        String planName,
        String rejectionReason,
        String processedBy
) {}
