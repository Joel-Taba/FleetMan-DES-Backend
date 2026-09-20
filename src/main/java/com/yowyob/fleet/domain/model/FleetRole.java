package com.yowyob.fleet.domain.model;

/**
 * Les 4 rôles FleetMan — unique source de vérité côté métier.
 * Correspond aux codes Kernel {@code ROLE_FLEET_*}.
 */
public enum FleetRole {
    FLEET_DRIVER,
    FLEET_MANAGER,
    FLEET_ADMIN,
    FLEET_SUPER_ADMIN;

    public boolean isDriver() {
        return this == FLEET_DRIVER;
    }

    public boolean isManager() {
        return this == FLEET_MANAGER;
    }

    /** Admin plateforme (admin ou super-admin), pas le gestionnaire de flotte. */
    public boolean isPlatformAdmin() {
        return this == FLEET_ADMIN || this == FLEET_SUPER_ADMIN;
    }

    public boolean isSuperAdmin() {
        return this == FLEET_SUPER_ADMIN;
    }

    public static String[] codes() {
        FleetRole[] values = values();
        String[] codes = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            codes[i] = values[i].name();
        }
        return codes;
    }
}
