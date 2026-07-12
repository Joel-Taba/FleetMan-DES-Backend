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
            List<String> roles) {
    }

    public static final UUID SUPER_ADMIN_ID = UUID.fromString("a0000001-0000-4000-8000-000000000001");
    public static final UUID ADMIN_ID = UUID.fromString("a0000002-0000-4000-8000-000000000002");
    public static final UUID MANAGER_ID = UUID.fromString("a0000003-0000-4000-8000-000000000003");
    public static final UUID DRIVER_ID = UUID.fromString("a0000004-0000-4000-8000-000000000004");

    // Nehemie seed accounts (matching seed_data_custom.sql UUIDs)
    public static final UUID NEHEMIE_ID = UUID.fromString("311c6d0d-77ca-4b08-8e65-8bdf8dcb60a2");
    public static final UUID MARC_ID = UUID.fromString("2c5dae3d-82d5-46d3-cbd3-3c4d5e6f7a8b");
    public static final UUID PAUL_ID = UUID.fromString("4d3c4b5a-6b7c-8d9e-0f1a-2b3c4d5e6f7a");
    public static final UUID SAMUEL_ID = UUID.fromString("8f5b8e9d-0c1b-2a3b-4c5d-6e7f8a9b0c1d");
    public static final UUID FRANK_ID = UUID.fromString("de7432fd-f4ad-472b-993f-18f1f17129aa");

    public static final UUID NEHEMIE_ADMIN_ID = UUID.fromString("a0000000-0000-4000-8000-000000000101");
    public static final UUID TURING_MANAGER_ID = UUID.fromString("a0000000-0000-4000-8000-000000000102");

    private static final List<Account> ALL = List.of(
            new Account(SUPER_ADMIN_ID, "superadmin", "superadmin@fleetman.cm", "Jean", "Super",
                    List.of("FLEET_SUPER_ADMIN", "FLEET_ADMIN")),
            new Account(ADMIN_ID, "fleetadmin", "admin@fleetman.cm", "Marie", "Admin", List.of("FLEET_ADMIN")),
            new Account(MANAGER_ID, "fleetmanager", "manager@fleetman.cm", "Paul", "Manager", List.of("FLEET_MANAGER")),
            new Account(DRIVER_ID, "fleetdriver", "driver@fleetman.cm", "André", "Mbarga", List.of("FLEET_DRIVER")),
            // Nehemie test accounts
            new Account(NEHEMIE_ID, "Nehemie", "nehemie@fleetman.cm", "Nehemie", "Nkolo", List.of("FLEET_MANAGER")),
            new Account(NEHEMIE_ADMIN_ID, "NehemieGmail", "ewane@gmail.com", "Nehemie", "Admin",
                    List.of("FLEET_ADMIN")),
            new Account(TURING_MANAGER_ID, "TuringManager", "turing@gmail.com", "Alan", "Turing",
                    List.of("FLEET_MANAGER")),
            new Account(MARC_ID, "MarcDriver", "marc.driver@fleetman.cm", "Marc", "Eyango", List.of("FLEET_DRIVER")),
            new Account(PAUL_ID, "PaulDriver", "paul.driver@fleetman.cm", "Paul", "Essomba", List.of("FLEET_DRIVER")),
            new Account(SAMUEL_ID, "SamuelDriver", "samuel.driver@fleetman.cm", "Samuel", "Tchakounte",
                    List.of("FLEET_DRIVER")),
            new Account(FRANK_ID, "Frank", "Frank@gmail.com", "Frank", "Driver", List.of("FLEET_DRIVER")));

    private static final Map<String, Account> BY_IDENTIFIER = buildIndex();

    private DemoTestAccounts() {
    }

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

    public static List<Account> getAll() {
        return ALL;
    }

    public static boolean isDemoPassword(String password) {
        return DEMO_PASSWORD.equals(password) || "Fleetman2026!".equals(password) || "Nehemie@123".equals(password)
                || "Frank@123".equals(password)
                || "Fank@123".equals(password);
    }
}
