package com.yowyob.fleet.domain.exception;

import org.springframework.http.HttpStatus;

public class AdminException extends DomainException {
    public AdminException(String message, HttpStatus status, String code) {
        super(message, status, code);
    }

    public static AdminException managerNotFound() {
        return new AdminException("Fleet Manager introuvable.", HttpStatus.NOT_FOUND, "ADMN_001");
    }

    public static AdminException masterAccessForbidden() {
        return new AdminException("Accès interdit : Vous ne pouvez pas manipuler un compte Super Administrateur.", HttpStatus.FORBIDDEN, "ADMN_002");
    }

    public static AdminException invalidResourceType() {
        return new AdminException("Type de ressource invalide ou inexistant.", HttpStatus.BAD_REQUEST, "ADMN_003");
    }

    public static AdminException resourceLocked() {
        return new AdminException("Cette ressource est utilisée par des véhicules et ne peut être supprimée.", HttpStatus.CONFLICT, "ADMN_004");
    }

    public static AdminException actionForbiddenOnUserType() {
        return new AdminException("Action interdite sur ce type d'utilisateur (réservé aux managers).", HttpStatus.FORBIDDEN, "ADMN_005");
    }
}