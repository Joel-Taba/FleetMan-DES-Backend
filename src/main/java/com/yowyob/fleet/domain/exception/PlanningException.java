package com.yowyob.fleet.domain.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Exception métier du module Planification & Ordonnancement.
 * Hérite de DomainException — pattern identique aux exceptions existantes.
 *
 * Codes d'erreur : PLN_001 à PLN_015
 */
public class PlanningException extends DomainException {

    private PlanningException(String message, HttpStatus status, String code) {
        super(message, status, code);
    }

    // ── Erreurs Planning ──────────────────────────────────────────────────────

    /** PLN_001 — Planning introuvable */
    public static PlanningException scheduleNotFound(UUID id) {
        return new PlanningException(
                "Planning introuvable avec l'ID : " + id,
                HttpStatus.NOT_FOUND, "PLN_001");
    }

    /** PLN_002 — Titre de planning obligatoire */
    public static PlanningException titleRequired() {
        return new PlanningException(
                "Le titre du planning est obligatoire.",
                HttpStatus.BAD_REQUEST, "PLN_002");
    }

    /** PLN_003 — Dates invalides */
    public static PlanningException invalidDates() {
        return new PlanningException(
                "La date de fin doit être postérieure à la date de début.",
                HttpStatus.BAD_REQUEST, "PLN_003");
    }

    /** PLN_004 — Planning archivé non modifiable */
    public static PlanningException scheduleArchived(UUID id) {
        return new PlanningException(
                "Le planning " + id + " est archivé et ne peut plus être modifié.",
                HttpStatus.UNPROCESSABLE_ENTITY, "PLN_004");
    }

    // ── Erreurs Affectation ───────────────────────────────────────────────────

    /** PLN_005 — Affectation introuvable */
    public static PlanningException assignmentNotFound(UUID id) {
        return new PlanningException(
                "Affectation introuvable avec l'ID : " + id,
                HttpStatus.NOT_FOUND, "PLN_005");
    }

    /** PLN_006 — Conflit d'affectation véhicule */
    public static PlanningException vehicleConflict(UUID vehicleId,
                                                     String start, String end) {
        return new PlanningException(
                "Le véhicule " + vehicleId + " est déjà affecté sur la plage "
                        + start + " → " + end + ".",
                HttpStatus.CONFLICT, "PLN_006");
    }

    /** PLN_007 — Conflit d'affectation conducteur */
    public static PlanningException driverConflict(UUID driverId,
                                                    String start, String end) {
        return new PlanningException(
                "Le conducteur " + driverId + " est déjà affecté sur la plage "
                        + start + " → " + end + ".",
                HttpStatus.CONFLICT, "PLN_007");
    }

    /** PLN_008 — Véhicule non disponible (en maintenance ou hors service) */
    public static PlanningException vehicleNotAvailable(UUID vehicleId) {
        return new PlanningException(
                "Le véhicule " + vehicleId + " n'est pas disponible (statut MAINTENANCE ou hors service).",
                HttpStatus.UNPROCESSABLE_ENTITY, "PLN_008");
    }

    /** PLN_009 — Conducteur inactif */
    public static PlanningException driverInactive(UUID driverId) {
        return new PlanningException(
                "Le conducteur " + driverId + " est inactif et ne peut pas être affecté.",
                HttpStatus.UNPROCESSABLE_ENTITY, "PLN_009");
    }

    /** PLN_010 — Transition de statut invalide */
    public static PlanningException invalidStatusTransition(String from, String to) {
        return new PlanningException(
                "Transition de statut invalide : " + from + " → " + to + ".",
                HttpStatus.UNPROCESSABLE_ENTITY, "PLN_010");
    }

    /** PLN_011 — Véhicule introuvable pour l'affectation */
    public static PlanningException vehicleNotFound(UUID vehicleId) {
        return new PlanningException(
                "Véhicule introuvable pour l'affectation : " + vehicleId,
                HttpStatus.NOT_FOUND, "PLN_011");
    }

    /** PLN_012 — Conducteur introuvable pour l'affectation */
    public static PlanningException driverNotFound(UUID driverId) {
        return new PlanningException(
                "Conducteur introuvable pour l'affectation : " + driverId,
                HttpStatus.NOT_FOUND, "PLN_012");
    }
}
