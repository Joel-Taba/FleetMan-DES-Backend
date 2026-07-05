package com.yowyob.fleet.infrastructure.config.bootstrap;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Comptes de démonstration locaux (mode auth fake).
 * UUID fixes pour permettre le seed SQL des tables fleet.*.
 */
public final class DemoTestAccounts {

    public static final String DEMO_PASSWORD = "FleetMan2026!";

    public record Account(
            UUID id,
            String username,
            String email,
            String firstName,
            String lastName,
            List<String> roles
    ) {}

    public static final UUID SUPER_ADMIN_ID = UUID.fromString("a0000001-0000-4000-8000-000000000001");
    public static final UUID ADMIN_ID       = UUID.fromString("a0000002-0000-4000-8000-000000000002");
    public static final UUID MANAGER_ID     = UUID.fromString("a0000003-0000-4000-8000-000000000003");
    public static final UUID DRIVER_ID      = UUID.fromString("a0000004-0000-4000-8000-000000000004");

    private static final List<Account> ALL = List.of(
            new Account(SUPER_ADMIN_ID, "superadmin", "superadmin@fleetman.cm", "Jean", "Super", List.of("FLEET_SUPER_ADMIN", "FLEET_ADMIN")),
            new Account(ADMIN_ID, "fleetadmin", "admin@fleetman.cm", "Marie", "Admin", List.of("FLEET_ADMIN")),
            new Account(MANAGER_ID, "fleetmanager", "manager@fleetman.cm", "Paul", "Manager", List.of("FLEET_MANAGER")),
            new Account(DRIVER_ID, "fleetdriver", "driver@fleetman.cm", "André", "Mbarga", List.of("FLEET_DRIVER"))
    );

    private static final Map<String, Account> BY_IDENTIFIER = buildIndex();

    private DemoTestAccounts() {}

    private static Map<String, Account> buildIndex() {
        var map = new java.util.HashMap<String, Account>();
        for (Account a : ALL) {
            map.put(normalize(a.email()), a);
            map.put(normalize(a.username()), a);
        }
        return Map.copyOf(map);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    public static Optional<Account> findByIdentifier(String identifier) {
        return Optional.ofNullable(BY_IDENTIFIER.get(normalize(identifier)));
    }

    public static Optional<Account> findById(UUID id) {
        return ALL.stream().filter(a -> a.id().equals(id)).findFirst();
    }

    public static boolean isDemoPassword(String password) {
        return DEMO_PASSWORD.equals(password);
    }
}
