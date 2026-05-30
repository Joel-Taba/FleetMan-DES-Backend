package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

public record GlobalStatsResponse(
        Long totalFleetManagers,
        Long totalFleets,
        Long totalVehicles,
        Long totalDrivers,
        String systemStatus
) {}