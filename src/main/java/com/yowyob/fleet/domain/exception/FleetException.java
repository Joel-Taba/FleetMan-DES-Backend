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

    public static FleetException managerRequired() {
        return new FleetException(
                "Choisissez le gestionnaire responsable de la flotte : une flotte a toujours un gestionnaire.",
                HttpStatus.BAD_REQUEST, "FLT_008");
    }

    public static FleetException managerNotInCompany() {
        return new FleetException("Ce gestionnaire n'appartient pas à votre entreprise.", HttpStatus.FORBIDDEN, "FLT_009");
    }

    public static FleetException managerNotActive() {
        return new FleetException(
                "Ce gestionnaire est suspendu ou clôturé : choisissez un gestionnaire actif.", HttpStatus.CONFLICT, "FLT_010");
    }

    public static FleetException nameTaken(String name) {
        return new FleetException("Votre entreprise a déjà une flotte nommée « " + name + " ».", HttpStatus.CONFLICT, "FLT_011");
    }

    public static FleetException openCommitments(String fleetName) {
        return new FleetException(
                "La flotte « " + fleetName + " » a des trajets ou des affectations en cours ou prévus : "
                        + "terminez-les ou annulez-les avant de la transférer.",
                HttpStatus.CONFLICT, "FLT_012");
    }

    public static FleetException frozen(String fleetName) {
        return new FleetException(
                "La flotte « " + fleetName + " » est gelée : son gestionnaire est suspendu. "
                        + "L'administrateur doit la transférer à un gestionnaire actif.",
                HttpStatus.CONFLICT, "FLT_013");
    }

    public static FleetException sameManager() {
        return new FleetException("Cette flotte est déjà confiée à ce gestionnaire.", HttpStatus.CONFLICT, "FLT_014");
    }
}

