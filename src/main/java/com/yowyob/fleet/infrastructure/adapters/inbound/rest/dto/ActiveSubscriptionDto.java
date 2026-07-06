package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import java.time.LocalDate;
import java.util.UUID;

public record ActiveSubscriptionDto(
        UUID managerId,
        String companyName,
        String email,
        String planName,
        String subscriptionStatus,
        LocalDate subscriptionStart,
        LocalDate subscriptionEnd,
        long daysUntilExpiry
) {}
