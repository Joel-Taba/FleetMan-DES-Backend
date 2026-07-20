package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import java.util.List;

public record SuperAdminDashboardStatsResponse(
        long activeAdmins,
        long activeManagers,
        long totalFleets,
        long managedVehicles,
        long totalDrivers,
        String period,
        List<SignupTrendPoint> signupTrend,
        List<UserTypeSlice> userDistribution
) {
    public record SignupTrendPoint(String label, long count) {}

    public record UserTypeSlice(String name, long value, String color) {}
}
