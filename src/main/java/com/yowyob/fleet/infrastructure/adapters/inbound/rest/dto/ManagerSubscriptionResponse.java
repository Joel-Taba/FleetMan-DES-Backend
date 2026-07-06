package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ManagerSubscriptionResponse(
        UUID managerId,
        UUID planId,
        String planName,
        String subscriptionStatus,
        LocalDate subscriptionStart,
        LocalDate subscriptionEnd,
        int graceDays,
        long daysUntilExpiry,
        boolean inGracePeriod,
        boolean accessAllowed,
        int maxFleets,
        int maxVehicles,
        int maxDrivers,
        int currentFleets,
        int currentVehicles,
        int currentDrivers,
        List<PlanFeatureDto> features
) {
    public record PlanFeatureDto(String key, String label, boolean enabled) {}
}
