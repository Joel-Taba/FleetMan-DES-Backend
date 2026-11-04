package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import java.util.UUID;

public record AssignmentResourceUpdateRequest(
        UUID vehicleId,
        UUID driverId,
        java.time.LocalDateTime startDatetime,
        java.time.LocalDateTime endDatetime,
        /** Obligatoire si le planning est publié : communiqué au chauffeur. */
        String reason,
        String workRuleOverride
) {}
