package com.yowyob.fleet.domain.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Exception métier du module Documents Légaux.
 * Codes d'erreur : DOC_001 à DOC_012
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
}
