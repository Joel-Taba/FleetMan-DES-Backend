package com.yowyob.fleet.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IncidentRulesTest {

    private static IncidentRules.CaseFacts facts(Incident.Type type, boolean third, String police, int attachments,
                                                 boolean repair, int approved, int pending) {
        return new IncidentRules.CaseFacts(type, third, false, "NONE", police, null, "Cause", "Action", "DRIVER",
                attachments, repair, approved, pending);
    }

    private static Incident incident(Incident.Type type, Incident.Severity severity) {
        return new Incident(UUID.randomUUID(), type, "x", severity, null, null, null, "r", UUID.randomUUID(), "PL", null, null);
    }

    @Test
    void steps_cannotBeSkipped() {
        assertTrue(IncidentRules.canTransition(Incident.Status.REPORTED, Incident.Status.ACKNOWLEDGED));
        assertFalse(IncidentRules.canTransition(Incident.Status.REPORTED, Incident.Status.RESOLVED));
        assertFalse(IncidentRules.canTransition(Incident.Status.UNDER_INVESTIGATION, Incident.Status.CLOSED));
        assertTrue(IncidentRules.canTransition(Incident.Status.RESOLVED, Incident.Status.UNDER_INVESTIGATION));
        assertFalse(IncidentRules.canTransition(Incident.Status.CLOSED, Incident.Status.UNDER_INVESTIGATION));
        assertFalse(IncidentRules.canTransition(Incident.Status.RESOLVED, Incident.Status.CANCELLED));
    }

    @Test
    void closure_requiresTheBasics() {
        var gaps = IncidentRules.closureGaps(new IncidentRules.CaseFacts(Incident.Type.OTHER, false, false, "NONE", null, null,
                null, " ", "UNDETERMINED", 0, false, 0, 1));
        assertEquals(List.of("la cause de l'incident", "l'action corrective", "la responsabilité",
                "au moins une photo ou une pièce", "la validation des coûts en attente"), gaps);
    }

    @Test
    void closure_dependsOnTheType() {
        assertTrue(IncidentRules.closureGaps(facts(Incident.Type.ACCIDENT, true, null, 1, false, 0, 0)).contains("le constat ou procès-verbal"));
        assertTrue(IncidentRules.closureGaps(facts(Incident.Type.ACCIDENT, false, null, 1, false, 0, 0)).isEmpty());
        assertTrue(IncidentRules.closureGaps(facts(Incident.Type.THEFT, false, null, 1, false, 0, 0)).contains("le numéro de plainte"));
        assertTrue(IncidentRules.closureGaps(facts(Incident.Type.BREAKDOWN, false, null, 1, false, 0, 0)).contains("l'ordre de réparation"));
        assertTrue(IncidentRules.closureGaps(facts(Incident.Type.BREAKDOWN, false, null, 1, true, 0, 0)).isEmpty());
        assertTrue(IncidentRules.closureGaps(facts(Incident.Type.TRAFFIC_VIOLATION, false, null, 1, false, 0, 0)).contains("l'amende (coût validé)"));
    }

    @Test
    void immobilization_followsDrivableTheftSeverity_unlessManagerDecides() {
        assertFalse(incident(Incident.Type.BREAKDOWN, Incident.Severity.LOW).immobilizesVehicle());
        assertTrue(incident(Incident.Type.THEFT, Incident.Severity.LOW).immobilizesVehicle());
        assertTrue(incident(Incident.Type.ACCIDENT, Incident.Severity.HIGH).immobilizesVehicle());

        Incident stranded = incident(Incident.Type.BREAKDOWN, Incident.Severity.LOW);
        stranded.setVehicleDrivable(false);
        assertTrue(stranded.immobilizesVehicle());
        stranded.setImmobilizationOverride(false);
        assertFalse(stranded.immobilizesVehicle());

        Incident minor = incident(Incident.Type.OTHER, Incident.Severity.LOW);
        minor.setImmobilizationOverride(true);
        assertTrue(minor.immobilizesVehicle());
        minor.resolve(null);
        assertFalse(minor.immobilizesVehicle());
    }

    @Test
    void reloading_keepsTheResolutionDate() {
        Incident i = incident(Incident.Type.OTHER, Incident.Severity.LOW);
        var when = java.time.LocalDateTime.of(2026, 1, 2, 3, 4);
        i.restoreStatus(Incident.Status.RESOLVED, when);
        assertEquals(when, i.getResolvedAt());
    }
}
