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
}