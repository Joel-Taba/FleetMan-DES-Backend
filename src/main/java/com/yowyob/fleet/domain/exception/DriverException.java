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
}
