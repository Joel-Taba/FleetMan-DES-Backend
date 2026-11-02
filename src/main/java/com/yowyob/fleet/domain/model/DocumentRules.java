package com.yowyob.fleet.domain.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Règles de conformité documentaire : seul un document approuvé et en vigueur compte, et il doit
 * être valide sur tout le créneau (une pièce sans échéance, comme la carte grise, reste valide).
 * Même règle côté front : lib/vehicles/compliance.ts, lib/drivers/license.ts.
 */
public final class DocumentRules {

    public static final int EXPIRING_WITHIN_DAYS = 30;

    private DocumentRules() {
    }

    /** Vue minimale d'un document pour les règles. */
    public record Piece(String docType, String reviewStatus, boolean current, LocalDate expiryDate) {
        boolean inForce() {
            return current && "APPROVED".equals(reviewStatus);
        }
    }

    public record Gap(String docType, boolean missing, LocalDate expiredOn) {
    }

    /** Pièces obligatoires manquantes ou expirées au {@code validOn} (vide : en règle). */
    public static List<Gap> gaps(Collection<Piece> pieces, Collection<String> required, LocalDate validOn) {
        List<Gap> gaps = new ArrayList<>();
        for (String type : required) {
            List<Piece> inForce = pieces.stream().filter(p -> p.docType().equals(type) && p.inForce()).toList();
            if (inForce.isEmpty()) {
                gaps.add(new Gap(type, true, null));
            } else if (inForce.stream().noneMatch(p -> p.expiryDate() == null || !p.expiryDate().isBefore(validOn))) {
                gaps.add(new Gap(type, false, inForce.stream().map(Piece::expiryDate).max(LocalDate::compareTo).orElse(null)));
            }
        }
        return gaps;
    }

    /** Statut affiché : la revue d'abord, puis l'échéance d'un document approuvé. */
    public static String effectiveStatus(String reviewStatus, boolean archived, boolean superseded,
                                         LocalDate expiryDate, LocalDate today) {
        if (archived) return "ARCHIVED";
        if (superseded) return "REPLACED";
        if (!"APPROVED".equals(reviewStatus)) return reviewStatus;
        if (expiryDate == null) return "VALID";
        if (expiryDate.isBefore(today)) return "EXPIRED";
        return expiryDate.isAfter(today.plusDays(EXPIRING_WITHIN_DAYS)) ? "VALID" : "EXPIRING_SOON";
    }

    /** État d'une pièce dans la checklist d'une cible. */
    public static String checklistState(Collection<Piece> ofType, LocalDate today) {
        Piece force = ofType.stream().filter(Piece::inForce).findFirst().orElse(null);
        if (force != null) {
            String status = effectiveStatus("APPROVED", false, false, force.expiryDate(), today);
            return "VALID".equals(status) ? "OK" : "EXPIRING_SOON".equals(status) ? "EXPIRING" : "EXPIRED";
        }
        if (ofType.stream().anyMatch(p -> "PENDING".equals(p.reviewStatus()))) return "PENDING";
        if (ofType.stream().anyMatch(p -> "REJECTED".equals(p.reviewStatus()))) return "REJECTED";
        return "MISSING";
    }
}
