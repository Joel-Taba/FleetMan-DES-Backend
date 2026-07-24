package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record FleetStatsResponse(
        UUID fleetId,
        Long totalDrivers,
        Long totalVehicles,
        Double totalKmTraveled,
        Map<String, Long> vehicleStatusDistribution,
        BigDecimal budgetAllocated,
        BigDecimal budgetConsumed
) {}