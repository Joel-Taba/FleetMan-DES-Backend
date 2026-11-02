package com.yowyob.fleet.domain.model;

import java.time.Year;
import java.util.Locale;
import java.util.Set;

/**
 * Cycle de vie d'un véhicule de flotte :
 * <pre>
 *   AVAILABLE ⇄ ON_TRIP        (automatique : départ / retour de trajet)
 *   AVAILABLE ⇄ MAINTENANCE    (atelier, incident grave)
 *   AVAILABLE ⇄ IMMOBILIZED    (non conforme, hors service : motif obligatoire)
 *   * → RETIRED                (réformé, vendu, volé… : définitif, historique conservé)
 * </pre>
 */
public final class VehicleLifecycle {

    public static final String AVAILABLE = "AVAILABLE";
    public static final String ON_TRIP = "ON_TRIP";
    public static final String MAINTENANCE = "MAINTENANCE";
    public static final String IMMOBILIZED = "IMMOBILIZED";
    public static final String RETIRED = "RETIRED";

    /** Statuts qu'un gestionnaire peut poser à la main (ON_TRIP et RETIRED ont leur propre parcours). */
    public static final Set<String> MANUAL_STATUSES = Set.of(AVAILABLE, MAINTENANCE, IMMOBILIZED);

    public static final Set<String> RETIREMENT_TYPES =
            Set.of("SOLD", "SCRAPPED", "STOLEN", "TOTAL_LOSS", "RETURNED", "OTHER");

    public static final Set<String> FUEL_TYPES = Set.of("DIESEL", "GASOLINE", "HYBRID", "ELECTRIC", "LPG");
    public static final Set<String> TRANSMISSIONS = Set.of("MANUAL", "AUTOMATIC");

    private VehicleLifecycle() {
    }

    /** Libellé lisible d'un statut (messages d'erreur). */
    public static String label(String status) {
        if (status == null) return "statut inconnu";
        return switch (status) {
            case AVAILABLE -> "disponible";
            case ON_TRIP -> "en trajet";
            case MAINTENANCE -> "en maintenance";
            case IMMOBILIZED -> "immobilisé";
            case RETIRED -> "retiré du parc";
            default -> status.toLowerCase(Locale.ROOT);
        };
    }

    /** Un véhicule peut-il être engagé (trajet, affectation, véhicule attitré) ? */
    public static boolean isDispatchable(String status) {
        return status == null || AVAILABLE.equals(status) || ON_TRIP.equals(status);
    }

    /** Le véhicule compte-t-il dans le parc (et donc dans la limite du plan) ? */
    public static boolean isInService(String status) {
        return !RETIRED.equals(status);
    }

    /** Anciennes valeurs (IN_SERVICE, PARKED, UNDER_MAINTENANCE…) ramenées au cycle de vie ; null si inconnue. */
    public static String normalizeStatus(Object raw) {
        String value = raw == null ? "" : String.valueOf(raw).trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case AVAILABLE, ON_TRIP, MAINTENANCE, IMMOBILIZED, RETIRED -> value;
            case "IN_SERVICE", "PARKED" -> AVAILABLE;
            case "UNDER_MAINTENANCE", "IN_MAINTENANCE" -> MAINTENANCE;
            case "OUT_OF_SERVICE", "IMMOBILISED" -> IMMOBILIZED;
            default -> null;
        };
    }

    /** Plaque stockée : majuscules, espaces superflus retirés. */
    public static String normalizePlate(String plate) {
        return plate == null ? null : plate.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    /** Clé d'unicité d'une plaque : sans séparateurs (« LT 892 CE » = « lt-892-ce »). */
    public static String plateKey(String plate) {
        return plate == null ? "" : plate.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    /** Code carburant normalisé (libellés du référentiel compris) ; null si non reconnu. */
    public static String normalizeFuelType(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String v = withoutAccents(raw);
        if (v.contains("DIESEL") || v.contains("GASOIL") || v.contains("GAZOLE")) return "DIESEL";
        if (v.contains("HYBR")) return "HYBRID";
        if (v.contains("ELEC")) return "ELECTRIC";
        if (v.contains("GPL") || v.contains("LPG")) return "LPG";
        if (v.contains("ESSENCE") || v.contains("GASOLINE") || v.contains("PETROL") || v.contains("SUPER")) return "GASOLINE";
        return null;
    }

    public static String normalizeTransmission(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String v = withoutAccents(raw);
        if (v.startsWith("AUTO")) return "AUTOMATIC";
        if (v.startsWith("MAN")) return "MANUAL";
        return null;
    }

    private static String withoutAccents(String raw) {
        return java.text.Normalizer.normalize(raw.trim(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT);
    }

    /** Année de fabrication plausible : de 1950 à l'année prochaine (modèles millésimés). */
    public static boolean isPlausibleYear(Integer year) {
        return year == null || (year >= 1950 && year <= Year.now().getValue() + 1);
    }
}
