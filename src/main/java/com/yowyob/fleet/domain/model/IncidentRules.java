package com.yowyob.fleet.domain.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Cycle de vie d'un dossier d'incident et pièces exigées pour le clôturer. */
public final class IncidentRules {

    private static final Map<Incident.Status, Set<Incident.Status>> TRANSITIONS = Map.of(
            Incident.Status.REPORTED, Set.of(Incident.Status.ACKNOWLEDGED, Incident.Status.CANCELLED),
            Incident.Status.ACKNOWLEDGED, Set.of(Incident.Status.UNDER_INVESTIGATION, Incident.Status.CANCELLED),
            Incident.Status.UNDER_INVESTIGATION, Set.of(Incident.Status.RESOLVED, Incident.Status.CANCELLED),
            // Réouverture possible tant que le dossier n'est pas clôturé.
            Incident.Status.RESOLVED, Set.of(Incident.Status.CLOSED, Incident.Status.UNDER_INVESTIGATION),
            Incident.Status.CLOSED, Set.of(),
            Incident.Status.CANCELLED, Set.of());

    private IncidentRules() {
    }

    public static boolean canTransition(Incident.Status from, Incident.Status to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    /** Ce que le dossier contient au moment de la clôture. */
    public record CaseFacts(Incident.Type type, boolean thirdParty, boolean injuries, String insuranceStatus,
                            String policeReport, String insuranceClaim, String rootCause, String correctiveAction,
                            String responsibility, int attachments, boolean repairOrder, int approvedCosts,
                            int pendingCosts) {
    }

    /** Ce qui manque pour clôturer (vide : le dossier peut l'être). */
    public static List<String> closureGaps(CaseFacts f) {
        List<String> gaps = new ArrayList<>();
        if (blank(f.rootCause())) gaps.add("la cause de l'incident");
        if (blank(f.correctiveAction())) gaps.add("l'action corrective");
        if (blank(f.responsibility()) || "UNDETERMINED".equals(f.responsibility())) gaps.add("la responsabilité");
        if (f.attachments() == 0) gaps.add("au moins une photo ou une pièce");
        if (f.pendingCosts() > 0) gaps.add("la validation des coûts en attente");
        switch (f.type()) {
            case ACCIDENT -> {
                if ((f.thirdParty() || f.injuries()) && blank(f.policeReport())) gaps.add("le constat ou procès-verbal");
            }
            case THEFT -> {
                if (blank(f.policeReport())) gaps.add("le numéro de plainte");
            }
            case BREAKDOWN -> {
                if (!f.repairOrder()) gaps.add("l'ordre de réparation");
            }
            case TRAFFIC_VIOLATION -> {
                if (f.approvedCosts() == 0) gaps.add("l'amende (coût validé)");
            }
            default -> {
            }
        }
        if (!"NONE".equals(f.insuranceStatus()) && blank(f.insuranceClaim())) gaps.add("le numéro de déclaration d'assurance");
        return gaps;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
