package com.yowyob.fleet.domain.exception;

import org.springframework.http.HttpStatus;

public class VehicleException extends DomainException {
    public VehicleException(String message, HttpStatus status, String code) {
        super(message, status, code);
    }

    public static VehicleException plateConflict() {
        return new VehicleException("Ce numéro d'immatriculation est déjà utilisé.", HttpStatus.CONFLICT, "VHC_001");
    }

    public static VehicleException invalidVehicleType() {
        return new VehicleException("Le type de véhicule sélectionné est invalide.", HttpStatus.BAD_REQUEST, "VHC_002");
    }

    public static VehicleException invalidResource() {
        return new VehicleException("La marque ou le type de carburant sélectionné est invalide.", HttpStatus.BAD_REQUEST, "VHC_003");
    }

    public static VehicleException notFound(java.util.UUID id) {
        return new VehicleException("Véhicule introuvable (ID: " + id + ").", HttpStatus.NOT_FOUND, "VHC_004");
    }

    public static VehicleException ongoingTripConflict() {
        return new VehicleException("Impossible de supprimer un véhicule en cours de trajet.", HttpStatus.FORBIDDEN, "VHC_005");
    }

    public static VehicleException accessDenied() {
        return new VehicleException("Impossible de modifier ce véhicule : accès refusé.", HttpStatus.FORBIDDEN, "VHC_006");
    }

    public static VehicleException resourceInUse() {
        return new VehicleException("Suppression impossible : cette ressource est utilisée par des véhicules.", HttpStatus.CONFLICT, "ADM_010");
    }

    public static VehicleException duplicateResourceCode() {
        return new VehicleException("Ce code de ressource existe déjà.", HttpStatus.CONFLICT, "ADM_011");
    }

    public static VehicleException syncFailed(String details) {
        return new VehicleException("Échec de la synchronisation : " + details, HttpStatus.BAD_GATEWAY, "VHC_008");
    }
}