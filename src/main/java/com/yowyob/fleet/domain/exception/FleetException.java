package com.yowyob.fleet.domain.exception;

import org.springframework.http.HttpStatus;
import java.util.UUID;

/**
 * Exceptions spécifiques au domaine des Flottes (Module 10).
 * Centralise les codes FLT_XXX pour le frontend.
 */
public class FleetException extends DomainException {

    protected FleetException(String message, HttpStatus status, String businessCode) {
        super(message, status, businessCode);
    }

    public static FleetException notFound(UUID id) {
        return new FleetException("Flotte introuvable (ID: " + id + ").", HttpStatus.NOT_FOUND, "FLT_001");
    }

    public static FleetException accessDenied() {
        return new FleetException("Accès refusé : Cette flotte ne vous appartient pas.", HttpStatus.FORBIDDEN, "FLT_002");
    }

    public static FleetException resourceAlreadyAssigned() {
        return new FleetException("Ce véhicule ou ce chauffeur appartient déjà à une autre flotte.", HttpStatus.CONFLICT, "FLT_003");
    }

    public static FleetException cannotDeleteNotEmpty() {
        return new FleetException("Impossible de supprimer : La flotte contient encore des véhicules ou des chauffeurs.", HttpStatus.CONFLICT, "FLT_004");
    }

    public static FleetException invalidResourceStatus(String details) {
        return new FleetException("Action impossible sur la ressource : " + details, HttpStatus.BAD_REQUEST, "FLT_005");
    }

    public static FleetException recruitmentFailed(String reason) {
        return new FleetException("Échec du recrutement : " + reason, HttpStatus.BAD_REQUEST, "FLT_006");
    }

    public static FleetException alreadyAssignedToAnotherManager(String fleetName) {
        return new FleetException(
                "La flotte « " + fleetName + " » est déjà assignée à un autre gestionnaire. "
                        + "Désassignez-la d'abord avant de la réassigner.",
                HttpStatus.CONFLICT, "FLT_007");
    }
}