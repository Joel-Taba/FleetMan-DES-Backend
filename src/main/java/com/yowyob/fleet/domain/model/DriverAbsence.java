package com.yowyob.fleet.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Absence datée d'un chauffeur : congé, arrêt maladie ou suspension (fin inconnue possible
 * pour une suspension seulement). Pendant l'absence le chauffeur n'est pas engageable ; il
 * redevient disponible de lui-même le lendemain de la date de fin.
 */
public record DriverAbsence(
        UUID id,
        UUID driverId,
        String type,
        LocalDate startDate,
        LocalDate endDate,
        String reason,
        UUID createdBy,
        Instant createdAt,
        Instant cancelledAt
) {
    public static final String LEAVE = "LEAVE";
    public static final String SICK_LEAVE = "SICK_LEAVE";
    public static final String SUSPENSION = "SUSPENSION";
    public static final Set<String> TYPES = Set.of(LEAVE, SICK_LEAVE, SUSPENSION);

    /** Statut affiché d'un chauffeur actif pendant une absence. */
    public static final String ON_LEAVE = "ON_LEAVE";
    public static final String SUSPENDED = "SUSPENDED";

    public boolean isCancelled() {
        return cancelledAt != null;
    }

    /** L'absence couvre-t-elle au moins un jour de [from, to] ? (to null : jour unique {@code from}). */
    public boolean overlaps(LocalDate from, LocalDate to) {
        if (isCancelled()) return false;
        LocalDate last = to != null ? to : from;
        return !startDate.isAfter(last) && (endDate == null || !endDate.isBefore(from));
    }

    public boolean coversDay(LocalDate day) {
        return overlaps(day, day);
    }

    public String displayStatus() {
        return SUSPENSION.equals(type) ? SUSPENDED : ON_LEAVE;
    }

    /** Absence en cours le jour donné (une suspension l'emporte sur un congé). */
    public static Optional<DriverAbsence> current(Collection<DriverAbsence> absences, LocalDate day) {
        return absences.stream()
                .filter(a -> a.coversDay(day))
                .min((a, b) -> Boolean.compare(!SUSPENSION.equals(a.type()), !SUSPENSION.equals(b.type())));
    }

    /**
     * Statut affiché : LEFT (parti), SUSPENDED ou ON_LEAVE (absence en cours), sinon ACTIVE.
     */
    public static String effectiveStatus(String contractStatus, Collection<DriverAbsence> absences, LocalDate day) {
        if ("LEFT".equals(contractStatus)) return "LEFT";
        return current(absences, day).map(DriverAbsence::displayStatus).orElse("ACTIVE");
    }
}
