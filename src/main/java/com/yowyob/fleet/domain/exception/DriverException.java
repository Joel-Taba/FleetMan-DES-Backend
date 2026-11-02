package com.yowyob.fleet.domain.exception;

import org.springframework.http.HttpStatus;

public class DriverException extends DomainException {
    public DriverException(String message, HttpStatus status, String code) {
        super(message, status, code);
    }

    public static DriverException licenseConflict() {
        return new DriverException("Ce numéro de permis est déjà utilisé.", HttpStatus.CONFLICT, "DRV_001");
    }

    public static DriverException vehicleAlreadyAssigned(String plate) {
        return new DriverException("Le véhicule " + plate + " est déjà assigné à un autre chauffeur.", HttpStatus.CONFLICT, "DRV_002");
    }

    public static DriverException driverBusy() {
        return new DriverException("Ce chauffeur est déjà assigné à un autre véhicule.", HttpStatus.CONFLICT, "DRV_003");
    }

    public static DriverException ongoingTripConflict() {
        return new DriverException("Impossible de modifier l'assignation : une course est en cours.", HttpStatus.FORBIDDEN, "DRV_004");
    }

    public static DriverException notADriver() {
        return new DriverException("L'utilisateur sélectionné n'a pas le rôle CHAUFFEUR.", HttpStatus.BAD_REQUEST, "DRV_005");
    }

    public static DriverException openCommitments() {
        return new DriverException(
                "Ce chauffeur a des trajets ou des affectations non terminés : terminez-les, annulez-les ou réaffectez-les d'abord.",
                HttpStatus.CONFLICT, "DRV_006");
    }

    public static DriverException alreadyInAnotherFleet() {
        return new DriverException(
                "Ce chauffeur est déjà rattaché à une flotte hors de votre périmètre.",
                HttpStatus.CONFLICT, "DRV_007");
    }

    public static DriverException notInFleet() {
        return new DriverException("Ce chauffeur n'appartient pas à cette flotte.", HttpStatus.BAD_REQUEST, "DRV_008");
    }

    public static DriverException noDrivingLicense() {
        return new DriverException(
                "Ce chauffeur n'a aucun permis de conduire enregistré : ajoutez-le dans ses documents.",
                HttpStatus.UNPROCESSABLE_ENTITY, "DRV_009");
    }

    public static DriverException drivingLicenseExpired(java.time.LocalDate date) {
        return new DriverException(
                "Le permis de conduire de ce chauffeur n'est pas valide au " + date + " (expiré).",
                HttpStatus.UNPROCESSABLE_ENTITY, "DRV_010");
    }

    public static DriverException drivingLicenseCategoryMissing(String requiredCategory) {
        return new DriverException(
                "Le permis de ce chauffeur ne couvre pas la catégorie " + requiredCategory
                        + " exigée pour ce type de véhicule.",
                HttpStatus.UNPROCESSABLE_ENTITY, "DRV_011");
    }

    public static DriverException absent(String type, java.time.LocalDate from, java.time.LocalDate to) {
        String what = switch (type) {
            case "SUSPENSION" -> "suspendu";
            case "SICK_LEAVE" -> "en arrêt maladie";
            default -> "en congé";
        };
        String period = to == null ? "depuis le " + from + " (sans date de fin)" : "du " + from + " au " + to;
        return new DriverException("Ce chauffeur est " + what + " " + period + " : il ne peut pas être engagé sur ce créneau.",
                HttpStatus.UNPROCESSABLE_ENTITY, "DRV_012");
    }

    public static DriverException absenceOverlapsCommitments() {
        return new DriverException(
                "Ce chauffeur a des trajets ou des affectations prévus pendant cette absence : réaffectez-les ou annulez-les d'abord.",
                HttpStatus.CONFLICT, "DRV_013");
    }

    public static DriverException invalidAbsence(String detail) {
        return new DriverException("Absence invalide : " + detail, HttpStatus.BAD_REQUEST, "DRV_014");
    }

    public static DriverException absenceOverlap() {
        return new DriverException("Une autre absence est déjà enregistrée sur ces dates.", HttpStatus.CONFLICT, "DRV_015");
    }

    public static DriverException left() {
        return new DriverException(
                "Ce chauffeur a quitté l'entreprise : réintégrez-le avant de l'engager ou de le modifier.",
                HttpStatus.CONFLICT, "DRV_016");
    }

    public static DriverException phoneRequired() {
        return new DriverException(
                "Le téléphone du chauffeur est obligatoire : c'est le moyen de le joindre pendant une mission.",
                HttpStatus.BAD_REQUEST, "DRV_017");
    }

    public static DriverException notFound() {
        return new DriverException("Chauffeur introuvable.", HttpStatus.NOT_FOUND, "DRV_018");
    }

    public static DriverException invalidDeparture(String detail) {
        return new DriverException("Départ invalide : " + detail, HttpStatus.BAD_REQUEST, "DRV_019");
    }

    public static DriverException hasHistory() {
        return new DriverException(
                "Ce chauffeur a déjà un historique (trajets, affectations, dépenses…) : enregistrez son départ de l'entreprise au lieu de le supprimer.",
                HttpStatus.CONFLICT, "DRV_020");
    }

    /** Pièces rendues obligatoires par l'entreprise, manquantes ou expirées. */
    public static DriverException documentsNotCompliant(String gaps) {
        return new DriverException(
                "Dossier du chauffeur incomplet pour ce créneau : " + gaps + ". Déposez ou renouvelez ces pièces.",
                HttpStatus.UNPROCESSABLE_ENTITY, "DRV_021");
    }
}
