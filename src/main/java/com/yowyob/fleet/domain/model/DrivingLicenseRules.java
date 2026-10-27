package com.yowyob.fleet.domain.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Règles de catégorie de permis de conduire (classification CEMAC : A deux-roues, B véhicule
 * léger, C poids lourd, D transport de personnes, E remorque).
 */
public final class DrivingLicenseRules {

    /** Catégorie exigée par défaut selon le code du type de véhicule (null : aucune exigence). */
    private static final Map<String, String> DEFAULT_CATEGORY_BY_TYPE = Map.ofEntries(
            Map.entry("BIKE", "A"), Map.entry("MOTO", "A"),
            Map.entry("CAR", "B"), Map.entry("SUV", "B"), Map.entry("COUPE", "B"),
            Map.entry("PICKUP", "B"), Map.entry("VAN", "B"),
            Map.entry("TRUCK", "C"), Map.entry("TRACTOR", "C"),
            Map.entry("BUS", "D"), Map.entry("MINIBUS", "D"),
            Map.entry("TRAILER", "E"));

    private DrivingLicenseRules() {
    }

    public static String defaultCategoryFor(String vehicleTypeCode) {
        return vehicleTypeCode == null ? null
                : DEFAULT_CATEGORY_BY_TYPE.get(vehicleTypeCode.trim().toUpperCase(Locale.ROOT));
    }

    /** Catégories d'un permis saisies librement (« B,C », « b; c », « B C ») normalisées. */
    public static Set<String> parse(String licenseCategories) {
        if (licenseCategories == null || licenseCategories.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(licenseCategories.split("[,;/\\s]+"))
                .map(c -> c.trim().toUpperCase(Locale.ROOT))
                .filter(c -> !c.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Le permis couvre-t-il la catégorie exigée ? (aucune exigence → oui) */
    public static boolean covers(String licenseCategories, String requiredCategory) {
        if (requiredCategory == null || requiredCategory.isBlank()) {
            return true;
        }
        return parse(licenseCategories).contains(requiredCategory.trim().toUpperCase(Locale.ROOT));
    }
}
