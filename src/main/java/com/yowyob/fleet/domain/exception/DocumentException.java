package com.yowyob.fleet.domain.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Exception métier du module Documents Légaux.
 * Codes d'erreur : DOC_001 à DOC_020
 */
public class DocumentException extends DomainException {

    private DocumentException(String message, HttpStatus status, String code) {
        super(message, status, code);
    }

    /** DOC_001 — Document véhicule introuvable */
    public static DocumentException vehicleDocNotFound(UUID id) {
        return new DocumentException(
                "Document véhicule introuvable : " + id,
                HttpStatus.NOT_FOUND, "DOC_001");
    }

    /** DOC_002 — Document conducteur introuvable */
    public static DocumentException driverDocNotFound(UUID id) {
        return new DocumentException(
                "Document conducteur introuvable : " + id,
                HttpStatus.NOT_FOUND, "DOC_002");
    }

    /** DOC_003 — Type de document obligatoire */
    public static DocumentException docTypeRequired() {
        return new DocumentException(
                "Le type de document est obligatoire.",
                HttpStatus.BAD_REQUEST, "DOC_003");
    }

    /** DOC_004 — Date d'expiration obligatoire */
    public static DocumentException expiryDateRequired() {
        return new DocumentException(
                "La date d'expiration est obligatoire pour ce type de document.",
                HttpStatus.BAD_REQUEST, "DOC_004");
    }

    /** DOC_005 — Véhicule introuvable */
    public static DocumentException vehicleNotFound(UUID vehicleId) {
        return new DocumentException(
                "Véhicule introuvable pour l'ajout de document : " + vehicleId,
                HttpStatus.NOT_FOUND, "DOC_005");
    }

    /** DOC_006 — Conducteur introuvable */
    public static DocumentException driverNotFound(UUID driverId) {
        return new DocumentException(
                "Conducteur introuvable pour l'ajout de document : " + driverId,
                HttpStatus.NOT_FOUND, "DOC_006");
    }

    /** DOC_007 — Document déjà expiré à la création */
    public static DocumentException alreadyExpired() {
        return new DocumentException(
                "La date d'expiration fournie est déjà passée.",
                HttpStatus.BAD_REQUEST, "DOC_007");
    }

    /** DOC_008 — Type de document invalide */
    public static DocumentException invalidDocType(String type) {
        return new DocumentException(
                "Type de document invalide : " + type,
                HttpStatus.BAD_REQUEST, "DOC_008");
    }

    /** DOC_009 — Type ou taille de fichier invalide */
    public static DocumentException invalidFileType(String detail) {
        return new DocumentException(
                "Fichier non autorisé (PDF, JPEG, PNG ou WebP uniquement) : " + detail,
                HttpStatus.BAD_REQUEST, "DOC_009");
    }

    /** DOC_010 — Fichier trop volumineux */
    public static DocumentException fileTooLarge(long maxBytes) {
        return new DocumentException(
                "Fichier trop volumineux (max " + (maxBytes / 1024 / 1024) + " Mo).",
                HttpStatus.BAD_REQUEST, "DOC_010");
    }

    /** DOC_011 — Document introuvable */
    public static DocumentException notFound(UUID id) {
        return new DocumentException("Document introuvable : " + id, HttpStatus.NOT_FOUND, "DOC_011");
    }

    /** DOC_012 — Cible introuvable */
    public static DocumentException targetNotFound(UUID id) {
        return new DocumentException("Véhicule ou personne introuvable : " + id, HttpStatus.NOT_FOUND, "DOC_012");
    }

    /** DOC_013 — Le justificatif est obligatoire */
    public static DocumentException fileRequired() {
        return new DocumentException(
                "Joignez le fichier du document (PDF ou image) : une pièce sans justificatif ne peut pas être contrôlée.",
                HttpStatus.UNPROCESSABLE_ENTITY, "DOC_013");
    }

    /** DOC_014 — Fichier déjà rattaché à une autre cible */
    public static DocumentException fileBelongsToAnotherTarget() {
        return new DocumentException("Ce fichier est déjà rattaché au dossier d'un autre véhicule ou d'une autre personne.",
                HttpStatus.CONFLICT, "DOC_014");
    }

    /** DOC_015 — Un dépôt attend déjà un contrôle */
    public static DocumentException pendingExists() {
        return new DocumentException(
                "Une version de cette pièce attend déjà d'être contrôlée : corrigez-la ou retirez-la.",
                HttpStatus.CONFLICT, "DOC_015");
    }

    /** DOC_016 — Seul un document en attente se modifie, se valide ou se rejette */
    public static DocumentException notPending() {
        return new DocumentException(
                "Ce document n'est plus en attente de contrôle. Pour le corriger ou le renouveler, déposez une nouvelle version.",
                HttpStatus.CONFLICT, "DOC_016");
    }

    /** DOC_017 — Motif obligatoire */
    public static DocumentException reasonRequired() {
        return new DocumentException("Indiquez le motif.", HttpStatus.UNPROCESSABLE_ENTITY, "DOC_017");
    }

    /** DOC_018 — Catégories de permis obligatoires */
    public static DocumentException licenseCategoriesRequired() {
        return new DocumentException("Indiquez les catégories du permis (ex. B,C).",
                HttpStatus.UNPROCESSABLE_ENTITY, "DOC_018");
    }

    /** DOC_019 — Dates incohérentes */
    public static DocumentException invalidDates(String detail) {
        return new DocumentException(detail, HttpStatus.UNPROCESSABLE_ENTITY, "DOC_019");
    }

    /** DOC_020 — Le socle légal ne se désactive pas */
    public static DocumentException legalPieceAlwaysMandatory(String label) {
        return new DocumentException("La pièce « " + label + " » est imposée par la réglementation : elle reste obligatoire.",
                HttpStatus.UNPROCESSABLE_ENTITY, "DOC_020");
    }
}
