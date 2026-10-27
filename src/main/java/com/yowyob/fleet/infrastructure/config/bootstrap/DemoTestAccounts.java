package com.yowyob.fleet.infrastructure.config.bootstrap;

import com.yowyob.fleet.domain.model.FleetRole;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Comptes connus de la fake-auth.
 * <ul>
 *   <li>4 comptes démo historiques (UUID Kernel)</li>
 *   <li>174 comptes du seed {@code fleetman_test_data.sql} — UUID famille c0a*
 *       identiques à {@code fleet.users.id}</li>
 * </ul>
 * Mot de passe seed : {@code <username>@123} (ex. admin1@123).
 * Tous les comptes acceptent aussi le mot de passe démo commun ({@link #demoPassword()}),
 * lu dans la propriété {@code application.demo.password} (variable DEMO_PASSWORD du fichier .env).
 */
public final class DemoTestAccounts {

    /** Mot de passe démo commun : configuré au démarrage, jamais écrit dans le code. */
    private static volatile String demoPassword = "";

    public static void configureDemoPassword(String password) {
        demoPassword = password == null ? "" : password;
    }

    public static String demoPassword() {
        return demoPassword;
    }

    public record Account(
            UUID id,
            String username,
            String email,
            String firstName,
            String lastName,
            List<String> roles,
            UUID kernelId
    ) {
        public String seedPassword() {
            return username + "@123";
        }
    }

    /** Owner Kernel : joeltaba4@gmail.com */
    public static final UUID SUPER_ADMIN_ID = UUID.fromString("2c9a43d2-8406-4860-b33b-f7ba989885ba");
    /** admin@fleetman.cm */
    public static final UUID ADMIN_ID       = UUID.fromString("96b87460-6179-483d-a6d5-9cbcacd9d06d");
    /** manager1@fleetman.cm */
    public static final UUID MANAGER_ID     = UUID.fromString("e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb");
    /** driver@fleetman.cm — Kernel UUID (2026-07-05) */
    public static final UUID DRIVER_ID      = UUID.fromString("35944e04-43c1-4eba-8acf-13f72a3ca5be");

    private static final String[][] ADMIN_NAMES = {
            {"Marie", "Ngo"},
            {"Ibrahim", "Ndoumbe"},
            {"Esther", "Kamga"}
    };

    private static final String[][] MANAGER_NAMES = {
            {"Jean", "Dupont"}, {"Claire", "Fotso"}, {"Paul", "Essomba"},
            {"Amina", "Bello"}, {"Serge", "Onana"},
            {"Hassan", "Moussa"}, {"Rita", "Ngono"}, {"Yves", "Fouda"},
            {"Nadine", "Talla"}, {"Brice", "Kenfack"},
            {"Florence", "Kuete"}, {"Alain", "Simo"}, {"Carine", "Djoumessi"},
            {"Patrick", "Atangana"}, {"Chantal", "Oyono"}
    };

    private static final String[] FIRST = {
            "Néhémie", "Paul", "Jean", "Christian", "Marie", "Claire", "André", "Sylvie",
            "Ibrahim", "Amina", "Hassan", "Rita", "Pierre", "François", "Esther", "Grâce",
            "Samuel", "David", "Ruth", "Joseph", "Patrick", "Carine", "Brice", "Nadine",
            "Yves", "Sandrine", "Michel", "Florence", "Alain", "Chantal", "Serge", "Vanessa",
            "Éric", "Blandine", "Roger", "Mireille", "Gaston", "Pauline", "Hervé", "Colette",
            "Franck", "Aurélie", "Lionel", "Diane", "Martial", "Solange", "Thierry", "Nicole",
            "Armand", "Josiane", "Cédric", "Prisca", "Boris", "Inès", "Landry", "Ornella"
    };

    private static final String[] LAST = {
            "Mbarga", "Mballa", "Ewane", "Ekotto", "Ngo", "Fotso", "Etoundi", "Owona",
            "Ndoumbe", "Bello", "Moussa", "Ngono", "Kamga", "Tchoumi", "Fokou", "Essomba",
            "Onana", "Abega", "Toko", "Nkoulou", "Atangana", "Mvondo", "Oyono", "Manga",
            "Sone", "Ndi", "Issa", "Yaya", "Hamadou", "Oumarou", "Djoumessi", "Kuete",
            "Talla", "Simo", "Kenfack", "Fouda", "Bengono", "Mandjeck", "Zoua", "Biwole"
    };

    private static final List<Account> ALL = buildAll();
    private static final Map<String, Account> BY_IDENTIFIER = buildIndex();

    private DemoTestAccounts() {}

    private static List<Account> buildAll() {
        List<Account> accounts = new ArrayList<>();
        accounts.add(new Account(SUPER_ADMIN_ID, "joeltaba4", "joeltaba4@gmail.com", "Joel", "Taba",
                List.of(FleetRole.FLEET_SUPER_ADMIN.name(), FleetRole.FLEET_ADMIN.name()), SUPER_ADMIN_ID));
        accounts.add(new Account(ADMIN_ID, "adminfleet", "admin@fleetman.cm", "Marie", "Admin",
                List.of(FleetRole.FLEET_ADMIN.name()), ADMIN_ID));
        accounts.add(new Account(MANAGER_ID, "manager.dupont", "manager1@fleetman.cm", "Jean", "Dupont",
                List.of(FleetRole.FLEET_MANAGER.name()), MANAGER_ID));
        accounts.add(new Account(DRIVER_ID, "fleetdriver", "driver@fleetman.cm", "André", "Mbarga",
                List.of(FleetRole.FLEET_DRIVER.name()), DRIVER_ID));
        accounts.addAll(seedOrganizationAccounts());
        return List.copyOf(accounts);
    }

    /**
     * 3 admins + 15 managers + 156 chauffeurs — UUID alignés sur
     * {@code scripts/generate_fleetman_test_data.py}.
     */
    private static List<Account> seedOrganizationAccounts() {
        List<Account> list = new ArrayList<>(174);
        for (int a = 1; a <= 3; a++) {
            UUID adminId = familyUuid("c0a10001", a);
            list.add(new Account(
                    adminId, "admin" + a, "admin" + a + "@gmail.com",
                    ADMIN_NAMES[a - 1][0], ADMIN_NAMES[a - 1][1],
                    List.of(FleetRole.FLEET_ADMIN.name()), adminId));
            for (int m = 1; m <= 5; m++) {
                UUID managerId = familyUuid("c0a20001", a * 10 + m);
                int nameIdx = (a - 1) * 5 + (m - 1);
                list.add(new Account(
                        managerId, "manager" + a + m, "manager" + a + m + "@gmail.com",
                        MANAGER_NAMES[nameIdx][0], MANAGER_NAMES[nameIdx][1],
                        List.of(FleetRole.FLEET_MANAGER.name()), managerId));
            }
            for (int d = 1; d <= 52; d++) {
                UUID driverId = familyUuid("c0a50001", a * 1000 + d);
                String username = "driver" + a + String.format("%02d", d);
                int nameI = a * 80 + d;
                list.add(new Account(
                        driverId, username, username + "@gmail.com",
                        FIRST[nameI % FIRST.length],
                        LAST[(nameI * 11 + 3) % LAST.length],
                        List.of(FleetRole.FLEET_DRIVER.name()), driverId));
            }
        }
        return list;
    }

    static UUID familyUuid(String family8, int n) {
        return UUID.fromString(family8 + "-0000-4000-8000-" + String.format("%012d", n));
    }

    private static Map<String, Account> buildIndex() {
        var map = new java.util.HashMap<String, Account>();
        for (Account a : ALL) {
            map.put(normalize(a.email()), a);
            map.put(normalize(a.username()), a);
        }
        map.put("superadmin@fleetman.cm", findAccount(SUPER_ADMIN_ID));
        map.put("superadmin", findAccount(SUPER_ADMIN_ID));
        map.put("fleetadmin", findAccount(ADMIN_ID));
        map.put("manager@fleetman.cm", findAccount(MANAGER_ID));
        map.put("fleetmanager", findAccount(MANAGER_ID));
        map.put("manager", findAccount(MANAGER_ID));
        map.put("manager2@fleetman.cm", findAccount(MANAGER_ID));
        map.put("driver", findAccount(DRIVER_ID));
        map.put("driver1@fleetman.cm", findAccount(DRIVER_ID));
        map.put("driver1", findAccount(DRIVER_ID));
        return Map.copyOf(map);
    }

    public static List<Account> all() {
        return ALL;
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
        return !demoPassword.isEmpty() && demoPassword.equals(password);
    }

    /** Mot de passe démo commun, ou {@code username@123} pour les comptes seed @gmail.com. */
    public static boolean passwordMatches(Account account, String password) {
        if (password == null || account == null) {
            return false;
        }
        if (isDemoPassword(password)) {
            return true;
        }
        return account.email() != null
                && account.email().endsWith("@gmail.com")
                && account.seedPassword().equals(password);
    }
}
