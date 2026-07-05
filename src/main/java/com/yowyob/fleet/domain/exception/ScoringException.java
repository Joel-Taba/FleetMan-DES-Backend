package com.yowyob.fleet.domain.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Exceptions métier du module Scoring Conducteur.
 * Codes SCR_001 à SCR_010.
 */
public class ScoringException extends DomainException {

    protected ScoringException(String message, HttpStatus status, String businessCode) {
        super(message, status, businessCode);
    }

    public static ScoringException scoreNotFound(UUID id) {
        return new ScoringException(
                "Score introuvable (ID: " + id + ").",
                HttpStatus.NOT_FOUND, "SCR_001"
        );
    }

    public static ScoringException driverNotFound(UUID driverId) {
        return new ScoringException(
                "Chauffeur introuvable pour le calcul du score (ID: " + driverId + ").",
                HttpStatus.NOT_FOUND, "SCR_002"
        );
    }

    public static ScoringException fleetNotFound(UUID fleetId) {
        return new ScoringException(
                "Flotte introuvable pour le calcul du score (ID: " + fleetId + ").",
                HttpStatus.NOT_FOUND, "SCR_003"
        );
    }

    public static ScoringException invalidPeriod() {
        return new ScoringException(
                "La période de scoring est invalide. La date de début doit être antérieure à la date de fin.",
                HttpStatus.BAD_REQUEST, "SCR_004"
        );
    }

    public static ScoringException noDataAvailable(UUID driverId) {
        return new ScoringException(
                "Pas suffisamment de données pour calculer le score du chauffeur (ID: " + driverId + "). "
                        + "Il faut au moins une affectation complétée sur la période.",
                HttpStatus.UNPROCESSABLE_ENTITY, "SCR_005"
        );
    }

    public static ScoringException invalidTopLimit() {
        return new ScoringException(
                "La limite du classement doit être comprise entre 1 et 100.",
                HttpStatus.BAD_REQUEST, "SCR_006"
        );
    }
}
