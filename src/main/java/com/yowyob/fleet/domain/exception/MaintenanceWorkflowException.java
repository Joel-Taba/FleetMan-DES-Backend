package com.yowyob.fleet.domain.exception;

import org.springframework.http.HttpStatus;

/** Règles du cycle de vie d'une maintenance (demande du chauffeur → atelier → clôture). */
public class MaintenanceWorkflowException extends DomainException {

    private MaintenanceWorkflowException(String message, HttpStatus status, String code) {
        super(message, status, code);
    }

    public static MaintenanceWorkflowException invalidTransition(String from, String to) {
        return new MaintenanceWorkflowException(
                "Cette maintenance ne peut pas passer de " + label(from) + " à " + label(to) + ".",
                HttpStatus.CONFLICT, "OPS_MNT_101");
    }

    public static MaintenanceWorkflowException plannedDateRequired() {
        return new MaintenanceWorkflowException("Indiquez la date prévue de l'intervention.",
                HttpStatus.UNPROCESSABLE_ENTITY, "OPS_MNT_102");
    }

    public static MaintenanceWorkflowException cancelReasonRequired() {
        return new MaintenanceWorkflowException("Indiquez le motif de l'annulation.",
                HttpStatus.UNPROCESSABLE_ENTITY, "OPS_MNT_103");
    }

    public static MaintenanceWorkflowException vehicleOnTrip() {
        return new MaintenanceWorkflowException(
                "Le véhicule est en trajet : l'intervention ne peut démarrer qu'à son retour.",
                HttpStatus.CONFLICT, "OPS_MNT_104");
    }

    public static MaintenanceWorkflowException driverCannotEdit() {
        return new MaintenanceWorkflowException(
                "Seul le gestionnaire fait avancer une maintenance.",
                HttpStatus.FORBIDDEN, "OPS_MNT_105");
    }

    public static String label(String status) {
        if (status == null) return "?";
        return switch (status) {
            case "REQUESTED" -> "« demandée »";
            case "PLANNED" -> "« planifiée »";
            case "IN_PROGRESS" -> "« en cours »";
            case "COMPLETED" -> "« terminée »";
            case "CANCELLED" -> "« annulée »";
            default -> status;
        };
    }
}
