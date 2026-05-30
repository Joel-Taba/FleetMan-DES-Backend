package com.yowyob.fleet.domain.exception;

import org.springframework.http.HttpStatus;

public class SuperAdminException extends DomainException {
    public SuperAdminException(String message, HttpStatus status, String code) {
        super(message, status, code);
    }

    public static SuperAdminException adminNotFound() {
        return new SuperAdminException("Administrateur introuvable.", HttpStatus.NOT_FOUND, "SADM_001");
    }

    public static SuperAdminException selfActionForbidden() {
        return new SuperAdminException("Auto-modification interdite pour le Super Administrateur.", HttpStatus.FORBIDDEN, "SADM_002");
    }

    public static SuperAdminException roleMismatch() {
        return new SuperAdminException("L'utilisateur ciblé n'est pas un Administrateur.", HttpStatus.BAD_REQUEST, "SADM_003");
    }
}