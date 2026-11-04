package com.yowyob.fleet.domain.model;

import com.yowyob.fleet.domain.exception.TripException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Règles d'une tournée multi-arrêts : plan, exécution d'un arrêt, ponctualité. */
public final class TripStopRules {

    public static final int MAX_STOPS = 20;
    /** Écart toléré entre l'heure prévue et l'arrivée réelle pour être « à l'heure ». */
    public static final int ON_TIME_TOLERANCE_MINUTES = 15;

    public static final Set<String> STOP_TYPES = Set.of("DELIVERY", "PICKUP");
    public static final Set<String> FAILURE_REASONS =
            Set.of("REFUSED", "ABSENT", "CLOSED", "ADDRESS_NOT_FOUND", "DAMAGED", "NO_TIME", "OTHER");
    public static final Set<String> SHORTAGE_REASONS = Set.of("REFUSED", "DAMAGED", "MISSING", "OTHER");

    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9 ()-]{6,20}$");

    /** Arrêt saisi au planning (ou ajouté en cours de tournée). */
    public record StopInput(
            String stopType,
            String location,
            BigDecimal latitude,
            BigDecimal longitude,
            String contactName,
            String contactPhone,
            String instructions,
            LocalDateTime plannedArrival
    ) {}

    private TripStopRules() {
    }

    public static void validateStop(StopInput stop) {
        if (stop == null || stop.stopType() == null || !STOP_TYPES.contains(stop.stopType())) {
            throw TripException.invalidStop("Type d'arrêt inconnu (livraison ou ramassage).");
        }
        String location = clean(stop.location());
        if (location == null || location.length() > 255) {
            throw TripException.invalidStop("Le lieu de chaque arrêt est obligatoire.");
        }
        String phone = clean(stop.contactPhone());
        if (phone != null && !PHONE.matcher(phone).matches()) {
            throw TripException.invalidStop("Téléphone du contact invalide.");
        }
        if (clean(stop.contactName()) != null && stop.contactName().trim().length() > 120) {
            throw TripException.invalidStop("Le nom du contact ne doit pas dépasser 120 caractères.");
        }
        if (clean(stop.instructions()) != null && stop.instructions().trim().length() > 500) {
            throw TripException.invalidStop("Les consignes ne doivent pas dépasser 500 caractères.");
        }
    }

    /**
     * Plan d'une tournée : de 1 à {@value #MAX_STOPS} arrêts valides ; les heures prévues
     * renseignées sont dans le créneau du trajet et suivent l'ordre de passage.
     */
    public static void validatePlan(List<StopInput> stops, LocalDateTime slotStart, LocalDateTime slotEnd) {
        if (stops == null || stops.isEmpty()) {
            throw TripException.invalidStop("La tournée doit compter au moins un arrêt.");
        }
        if (stops.size() > MAX_STOPS) {
            throw TripException.invalidStop("Une tournée compte au plus " + MAX_STOPS + " arrêts.");
        }
        LocalDateTime previous = null;
        for (StopInput stop : stops) {
            validateStop(stop);
            validatePlannedArrival(stop.plannedArrival(), slotStart, slotEnd);
            if (stop.plannedArrival() != null) {
                if (previous != null && stop.plannedArrival().isBefore(previous)) {
                    throw TripException.invalidStop("Les heures prévues des arrêts doivent suivre l'ordre de passage.");
                }
                previous = stop.plannedArrival();
            }
        }
    }

    public static void validatePlannedArrival(LocalDateTime planned, LocalDateTime slotStart, LocalDateTime slotEnd) {
        if (planned == null) return;
        if ((slotStart != null && planned.isBefore(slotStart)) || (slotEnd != null && planned.isAfter(slotEnd))) {
            throw TripException.invalidStop("L'heure prévue d'un arrêt doit être comprise entre le départ et le retour prévus.");
        }
    }

    public static boolean isTerminal(String status) {
        return "COMPLETED".equals(status) || "FAILED".equals(status) || "SKIPPED".equals(status);
    }

    /** Un arrêt se sert tant qu'il n'est pas clos (en attente ou chauffeur sur place). */
    public static boolean isServable(String status) {
        return "PENDING".equals(status) || "ARRIVED".equals(status);
    }

    public static boolean allTerminal(List<String> statuses) {
        return !statuses.isEmpty() && statuses.stream().allMatch(TripStopRules::isTerminal);
    }

    /**
     * Quantité traitée sur une ligne : livrée (au plus la quantité chargée) ou ramassée. Un
     * écart avec la quantité prévue exige un motif.
     */
    public static void validateHandled(String stopType, int expected, Integer handled, String shortageReason) {
        if (handled == null || handled < 0) {
            throw TripException.invalidStop("La quantité traitée doit être positive ou nulle.");
        }
        if ("DELIVERY".equals(stopType) && handled > expected) {
            throw TripException.invalidDeliveredQuantity(expected);
        }
        if (handled < expected && (shortageReason == null || !SHORTAGE_REASONS.contains(shortageReason))) {
            throw TripException.invalidStop("Indiquez le motif de l'écart (refus, avarie, manquant…).");
        }
    }

    public static void validateFailureReason(String reason) {
        if (reason == null || !FAILURE_REASONS.contains(reason)) {
            throw TripException.invalidStop("Motif d'échec inconnu.");
        }
    }

    /** Heure déclarée (saisie hors ligne) bornée entre {@code lower} et maintenant. */
    public static LocalDateTime boundedTime(LocalDateTime declared, LocalDateTime lower, LocalDateTime now) {
        LocalDateTime at = declared != null && declared.isBefore(now) ? declared : now;
        return lower != null && at.isBefore(lower) ? lower : at;
    }

    public static Long delayMinutes(LocalDateTime planned, LocalDateTime arrived) {
        return planned == null || arrived == null ? null : ChronoUnit.MINUTES.between(planned, arrived);
    }

    /** ON_TIME, LATE ou EARLY (au-delà de la tolérance) ; null sans heure prévue ou sans arrivée. */
    public static String punctuality(LocalDateTime planned, LocalDateTime arrived) {
        Long delay = delayMinutes(planned, arrived);
        if (delay == null) return null;
        if (delay > ON_TIME_TOLERANCE_MINUTES) return "LATE";
        if (delay < -ON_TIME_TOLERANCE_MINUTES) return "EARLY";
        return "ON_TIME";
    }

    public static String clean(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
