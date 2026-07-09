package com.yowyob.fleet.infrastructure.config.security;

/**
 * Codes de permission granulaires FleetMan (alignés roles-core Kernel).
 * Utilisables via {@code @PreAuthorize("hasAuthority('VEHICLE_CREATE')")}.
 */
public final class FleetPermissions {

    private FleetPermissions() {}

    public static final String VEHICLE_CREATE = "VEHICLE_CREATE";
    public static final String VEHICLE_READ = "VEHICLE_READ";
    public static final String VEHICLE_UPDATE = "VEHICLE_UPDATE";
    public static final String VEHICLE_DELETE = "VEHICLE_DELETE";
    public static final String DRIVER_CREATE = "DRIVER_CREATE";
    public static final String DRIVER_READ = "DRIVER_READ";
    public static final String DRIVER_MANAGE = "DRIVER_MANAGE";
    public static final String FLEET_MANAGE = "FLEET_MANAGE";
    public static final String TRIP_START = "TRIP_START";
    public static final String TRIP_END = "TRIP_END";
    public static final String DOCUMENT_MANAGE = "DOCUMENT_MANAGE";
    public static final String SUBSCRIPTION_MANAGE = "SUBSCRIPTION_MANAGE";
    public static final String KPI_READ = "KPI_READ";
}
