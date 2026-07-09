package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import java.util.UUID;

public record AssignmentResourceUpdateRequest(
        UUID vehicleId,
        UUID driverId
) {}
