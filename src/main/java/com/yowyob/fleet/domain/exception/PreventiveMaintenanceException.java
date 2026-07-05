package com.yowyob.fleet.domain.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Exceptions métier du module Maintenance Préventive.
 * Codes PMT_001 à PMT_012.
 */
public class PreventiveMaintenanceException extends DomainException {

    protected PreventiveMaintenanceException(String message, HttpStatus status, String businessCode) {
        super(message, status, businessCode);
    }

    // ── Plans ──────────────────────────────────────────────────────────────────

    public static PreventiveMaintenanceException planNotFound(UUID id) {
        return new PreventiveMaintenanceException(
                "Plan de maintenance introuvable (ID: " + id + ").",
                HttpStatus.NOT_FOUND, "PMT_001"
        );
    }

    public static PreventiveMaintenanceException planThresholdRequired() {
        return new PreventiveMaintenanceException(
                "Au moins un seuil (intervalle km ou intervalle jours) doit être défini.",
                HttpStatus.BAD_REQUEST, "PMT_002"
        );
    }

    public static PreventiveMaintenanceException planAlreadyExists(UUID vehicleId,
                                                                    String type) {
        return new PreventiveMaintenanceException(
                "Un plan " + type + " existe déjà pour ce véhicule (ID: " + vehicleId + ").",
                HttpStatus.CONFLICT, "PMT_003"
        );
    }

    public static PreventiveMaintenanceException planTypeRequired() {
        return new PreventiveMaintenanceException(
                "Le type de maintenance est obligatoire.",
                HttpStatus.BAD_REQUEST, "PMT_004"
        );
    }

    // ── Alertes ───────────────────────────────────────────────────────────────

    public static PreventiveMaintenanceException alertNotFound(UUID id) {
        return new PreventiveMaintenanceException(
                "Alerte de maintenance introuvable (ID: " + id + ").",
                HttpStatus.NOT_FOUND, "PMT_005"
        );
    }

    public static PreventiveMaintenanceException alertAlreadyResolved(UUID id) {
        return new PreventiveMaintenanceException(
                "L'alerte (ID: " + id + ") est déjà résolue.",
                HttpStatus.UNPROCESSABLE_ENTITY, "PMT_006"
        );
    }

    // ── Références ────────────────────────────────────────────────────────────

    public static PreventiveMaintenanceException vehicleNotFound(UUID vehicleId) {
        return new PreventiveMaintenanceException(
                "Véhicule introuvable (ID: " + vehicleId + ").",
                HttpStatus.NOT_FOUND, "PMT_007"
        );
    }

    public static PreventiveMaintenanceException fleetNotFound(UUID fleetId) {
        return new PreventiveMaintenanceException(
                "Flotte introuvable (ID: " + fleetId + ").",
                HttpStatus.NOT_FOUND, "PMT_008"
        );
    }

    public static PreventiveMaintenanceException invalidIntervalValue() {
        return new PreventiveMaintenanceException(
                "Les intervalles km et jours doivent être strictement positifs.",
                HttpStatus.BAD_REQUEST, "PMT_009"
        );
    }

    public static PreventiveMaintenanceException noOperationalData(UUID vehicleId) {
        return new PreventiveMaintenanceException(
                "Aucune donnée opérationnelle (kilométrage) disponible pour le véhicule (ID: "
                        + vehicleId + "). Impossible d'évaluer le plan kilométrique.",
                HttpStatus.UNPROCESSABLE_ENTITY, "PMT_010"
        );
    }
}
