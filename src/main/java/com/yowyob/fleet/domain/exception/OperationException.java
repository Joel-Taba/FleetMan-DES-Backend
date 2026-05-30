package com.yowyob.fleet.domain.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Exceptions métier du module Opérations Terrain.
 * Couvre les agrégats : Maintenance, Incident, Recharge Carburant.
 *
 * Codes OPR_001 à OPR_012.
 */
public class OperationException extends DomainException {

    protected OperationException(String message, HttpStatus status, String businessCode) {
        super(message, status, businessCode);
    }

    // ── Maintenance ───────────────────────────────────────────────────────────

    public static OperationException maintenanceNotFound(UUID id) {
        return new OperationException(
            "Maintenance introuvable (ID: " + id + ").",
            HttpStatus.NOT_FOUND, "OPR_001"
        );
    }

    public static OperationException maintenanceSubjectRequired() {
        return new OperationException(
            "L'objet de la maintenance est obligatoire.",
            HttpStatus.BAD_REQUEST, "OPR_002"
        );
    }

    public static OperationException negativeCostForbidden() {
        return new OperationException(
            "Le coût ne peut pas être négatif.",
            HttpStatus.BAD_REQUEST, "OPR_003"
        );
    }

    // ── Incident ──────────────────────────────────────────────────────────────

    public static OperationException incidentNotFound(UUID id) {
        return new OperationException(
            "Incident introuvable (ID: " + id + ").",
            HttpStatus.NOT_FOUND, "OPR_004"
        );
    }

    public static OperationException incidentTypeRequired() {
        return new OperationException(
            "Le type d'incident est obligatoire.",
            HttpStatus.BAD_REQUEST, "OPR_005"
        );
    }

    public static OperationException incidentAlreadyClosed(UUID id) {
        return new OperationException(
            "L'incident (ID: " + id + ") est déjà clôturé et ne peut plus être modifié.",
            HttpStatus.UNPROCESSABLE_ENTITY, "OPR_006"
        );
    }

    public static OperationException invalidStatusTransition(String from, String to) {
        return new OperationException(
            "Transition de statut invalide : " + from + " → " + to + ".",
            HttpStatus.UNPROCESSABLE_ENTITY, "OPR_007"
        );
    }

    // ── Recharge carburant ────────────────────────────────────────────────────

    public static OperationException fuelRechargeNotFound(UUID id) {
        return new OperationException(
            "Recharge de carburant introuvable (ID: " + id + ").",
            HttpStatus.NOT_FOUND, "OPR_008"
        );
    }

    public static OperationException invalidFuelQuantity() {
        return new OperationException(
            "La quantité de carburant doit être strictement positive.",
            HttpStatus.BAD_REQUEST, "OPR_009"
        );
    }

    public static OperationException invalidFuelPrice() {
        return new OperationException(
            "Le prix de la recharge ne peut pas être négatif.",
            HttpStatus.BAD_REQUEST, "OPR_010"
        );
    }

    // ── Références croisées ───────────────────────────────────────────────────

    public static OperationException vehicleNotFound(UUID vehicleId) {
        return new OperationException(
            "Véhicule introuvable pour cette opération (ID: " + vehicleId + ").",
            HttpStatus.NOT_FOUND, "OPR_011"
        );
    }

    public static OperationException driverNotFound(UUID driverId) {
        return new OperationException(
            "Chauffeur introuvable pour cette opération (ID: " + driverId + ").",
            HttpStatus.NOT_FOUND, "OPR_012"
        );
    }
}
