package com.yowyob.fleet.infrastructure.config.bootstrap;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Comptes de démonstration — UUID alignés sur le Kernel RT-Comops (voir KERNEL_SETUP_FLEETMAN.md).
 * Le chauffeur demo reste local (pas encore provisionné dans le Kernel).
 */
public final class DemoTestAccounts {

    public static final String DEMO_PASSWORD = "FleetMan2026!";

    public record Account(
            UUID id,
            String username,
            String email,
            String firstName,
            String lastName,
            List<String> roles,
            UUID kernelId
    ) {}

    /** Owner Kernel : joeltaba4@gmail.com */
    public static final UUID SUPER_ADMIN_ID = UUID.fromString("2c9a43d2-8406-4860-b33b-f7ba989885ba");
    /** admin@fleetman.cm */
    public static final UUID ADMIN_ID       = UUID.fromString("96b87460-6179-483d-a6d5-9cbcacd9d06d");
    /** manager1@fleetman.cm */
    public static final UUID MANAGER_ID     = UUID.fromString("e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb");
    /** driver@fleetman.cm — Kernel UUID (2026-07-05) */
    public static final UUID DRIVER_ID      = UUID.fromString("35944e04-43c1-4eba-8acf-13f72a3ca5be");

    private static final List<Account> ALL = List.of(
            new Account(SUPER_ADMIN_ID, "joeltaba4", "joeltaba4@gmail.com", "Joel", "Taba",
                    List.of("FLEET_SUPER_ADMIN", "FLEET_ADMIN"), SUPER_ADMIN_ID),
            new Account(ADMIN_ID, "adminfleet", "admin@fleetman.cm", "Marie", "Admin",
                    List.of("FLEET_ADMIN"), ADMIN_ID),
            new Account(MANAGER_ID, "manager.dupont", "manager1@fleetman.cm", "Jean", "Dupont",
                    List.of("FLEET_MANAGER"), MANAGER_ID),
            new Account(DRIVER_ID, "fleetdriver", "driver@fleetman.cm", "André", "Mbarga",
                    List.of("FLEET_DRIVER"), DRIVER_ID)
    );

    /** Alias email/username pour compatibilité IDENTIFIANTS_TEST.md (mode mock front). */
    private static final Map<String, Account> BY_IDENTIFIER = buildIndex();

    private DemoTestAccounts() {}

    private static Map<String, Account> buildIndex() {
        var map = new java.util.HashMap<String, Account>();
        for (Account a : ALL) {
            map.put(normalize(a.email()), a);
            map.put(normalize(a.username()), a);
        }
        // Alias historiques front mock
        map.put("superadmin@fleetman.cm", findAccount(SUPER_ADMIN_ID));
        map.put("superadmin", findAccount(SUPER_ADMIN_ID));
        map.put("fleetadmin", findAccount(ADMIN_ID));
        map.put("manager@fleetman.cm", findAccount(MANAGER_ID));
        map.put("fleetmanager", findAccount(MANAGER_ID));
        map.put("manager", findAccount(MANAGER_ID));
        map.put("driver", findAccount(DRIVER_ID));
        return Map.copyOf(map);
    }

    private static Account findAccount(UUID id) {
        return ALL.stream().filter(a -> a.id().equals(id)).findFirst().orElseThrow();
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
