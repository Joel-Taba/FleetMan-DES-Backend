package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import java.util.Map;
import java.util.UUID;

public record FleetStatsResponse(
        UUID fleetId,
        Long totalDrivers,
        Double totalKmTraveled,
        Map<String, Long> vehicleStatusDistribution
) {}