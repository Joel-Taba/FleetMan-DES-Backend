package com.yowyob.fleet.domain.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Exceptions métier du module Alertes & Règles Métier.
 * Codes ALR_001 à ALR_010.
 */
public class AlertException extends DomainException {

    protected AlertException(String message, HttpStatus status, String businessCode) {
        super(message, status, businessCode);
    }

    public static AlertException ruleNotFound(UUID id) {
        return new AlertException(
                "Règle d'alerte introuvable (ID: " + id + ").",
                HttpStatus.NOT_FOUND, "ALR_001"
        );
    }

    public static AlertException ruleNameRequired() {
        return new AlertException(
                "Le nom de la règle est obligatoire.",
                HttpStatus.BAD_REQUEST, "ALR_002"
        );
    }

    public static AlertException systemRuleNotDeletable(UUID id) {
        return new AlertException(
                "La règle (ID: " + id + ") est un template système et ne peut pas être supprimée.",
                HttpStatus.UNPROCESSABLE_ENTITY, "ALR_003"
        );
    }

    public static AlertException eventNotFound(UUID id) {
        return new AlertException(
                "Événement d'alerte introuvable (ID: " + id + ").",
                HttpStatus.NOT_FOUND, "ALR_004"
        );
    }

    public static AlertException invalidConditionValue(String type) {
        return new AlertException(
                "La valeur de condition est invalide pour le type " + type + ".",
                HttpStatus.BAD_REQUEST, "ALR_005"
        );
    }

    public static AlertException duplicateRuleName(String name) {
        return new AlertException(
                "Une règle avec le nom '" + name + "' existe déjà pour ce manager.",
                HttpStatus.CONFLICT, "ALR_006"
        );
    }
}
