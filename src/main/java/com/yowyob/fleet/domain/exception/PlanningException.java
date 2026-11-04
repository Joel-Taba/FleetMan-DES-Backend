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
        return vehicleNotAvailable(vehicleId, null);
    }

    public static PlanningException vehicleNotAvailable(UUID vehicleId, String status) {
        return new PlanningException(
                "Ce véhicule n'est pas disponible"
                        + (status != null ? " (" + com.yowyob.fleet.domain.model.VehicleLifecycle.label(status) + ")" : "")
                        + " : il ne peut pas être affecté.",
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

    /** PLN_013 — Affectation incompatible avec son planning (flotte ou période) */
    public static PlanningException scheduleMismatch() {
        return new PlanningException(
                "L'affectation doit appartenir à la flotte du planning et se situer dans sa période.",
                HttpStatus.UNPROCESSABLE_ENTITY, "PLN_013");
    }

    /** PLN_014 — Archivage impossible tant que des affectations sont actives */
    public static PlanningException scheduleHasActiveAssignments(UUID id) {
        return new PlanningException(
                "Le planning " + id + " contient encore des affectations en attente ou en cours : "
                        + "terminez-les ou annulez-les avant de l'archiver.",
                HttpStatus.UNPROCESSABLE_ENTITY, "PLN_014");
    }

    /** PLN_015 — Une affectation commencée ou terminée fait partie de l'historique */
    public static PlanningException assignmentNotDeletable(UUID id) {
        return new PlanningException(
                "L'affectation " + id + " est en cours ou terminée : elle ne peut pas être supprimée (annulez-la si besoin).",
                HttpStatus.UNPROCESSABLE_ENTITY, "PLN_015");
    }

    /** PLN_016 — Le véhicule n'appartient pas à la flotte de l'affectation */
    public static PlanningException vehicleNotInFleet(UUID vehicleId) {
        return new PlanningException(
                "Le véhicule " + vehicleId + " n'appartient pas à la flotte de l'affectation.",
                HttpStatus.UNPROCESSABLE_ENTITY, "PLN_016");
    }

    /** PLN_017 — Démarrage et fin d'un poste sont automatiques */
    public static PlanningException manualStatusNotAllowed(String status) {
        return new PlanningException(
                "Le statut " + status + " ne se pose pas à la main : le poste démarre au premier départ d'un "
                        + "trajet et se termine à son heure de fin. Seuls NO_SHOW et CANCELLED sont manuels.",
                HttpStatus.UNPROCESSABLE_ENTITY, "PLN_017");
    }

    /** PLN_018 — Un trajet du poste est en cours */
    public static PlanningException shiftHasTripInProgress(UUID id) {
        return new PlanningException(
                "Un trajet rattaché au poste " + id + " est en cours : enregistrez son retour avant d'annuler le poste.",
                HttpStatus.CONFLICT, "PLN_018");
    }

    /** PLN_019 — Transition de statut impossible */
    public static PlanningException invalidStatusTransition(String reason) {
        return new PlanningException(reason, HttpStatus.CONFLICT, "PLN_019");
    }

    /** PLN_012 — Conducteur introuvable pour l'affectation */
    public static PlanningException driverNotFound(UUID driverId) {
        return new PlanningException(
                "Conducteur introuvable pour l'affectation : " + driverId,
                HttpStatus.NOT_FOUND, "PLN_012");
    }

    /** PLN_020 — Règles de temps de travail dépassées sans motif */
    public static PlanningException workRulesExceeded(String details) {
        return new PlanningException("Temps de travail : " + details + " Indiquez un motif pour confirmer.",
                HttpStatus.CONFLICT, "PLN_020");
    }

    /** PLN_021 — Motif obligatoire pour modifier un planning publié */
    public static PlanningException changeReasonRequired() {
        return new PlanningException("Ce planning est publié : indiquez le motif du changement, il sera communiqué au chauffeur.",
                HttpStatus.UNPROCESSABLE_ENTITY, "PLN_021");
    }

    /** PLN_022 — Modèle de poste invalide ou introuvable */
    public static PlanningException invalidTemplate(String message) {
        return new PlanningException(message, HttpStatus.UNPROCESSABLE_ENTITY, "PLN_022");
    }

    /** PLN_023 — Création en série invalide */
    public static PlanningException invalidBulk(String message) {
        return new PlanningException(message, HttpStatus.UNPROCESSABLE_ENTITY, "PLN_023");
    }

    /** PLN_024 — Règles de temps de travail invalides */
    public static PlanningException invalidWorkRules(String message) {
        return new PlanningException(message, HttpStatus.UNPROCESSABLE_ENTITY, "PLN_024");
    }

    /** PLN_025 — Accusé de réception impossible */
    public static PlanningException notYourShift() {
        return new PlanningException("Seul le chauffeur du poste peut en accuser réception.", HttpStatus.FORBIDDEN, "PLN_025");
    }

    /** Même erreur, précédée du contexte (jour d'une série, poste copié…). */
    public static PlanningException prefixed(String prefix, DomainException e) {
        return new PlanningException(prefix + e.getMessage(), e.getStatus(), e.getBusinessCode());
    }
}
