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

    public static VehicleException openCommitments() {
        return new VehicleException(
                "Ce véhicule a des trajets ou des affectations non terminés : terminez-les, annulez-les ou réaffectez-les d'abord.",
                HttpStatus.CONFLICT, "VHC_009");
    }

    public static VehicleException statusManagedByTrips() {
        return new VehicleException(
                "Le statut « en trajet » est géré par les trajets : il ne peut pas être modifié à la main pendant un trajet en cours.",
                HttpStatus.UNPROCESSABLE_ENTITY, "VHC_010");
    }

    public static VehicleException invalidStatus(String value) {
        return new VehicleException(
                "Statut de véhicule invalide : " + value + " (valeurs admises : disponible, en maintenance, immobilisé ; la réforme passe par « Retirer du parc »).",
                HttpStatus.BAD_REQUEST, "VHC_011");
    }

    public static VehicleException heldByIncident() {
        return new VehicleException(
                "Ce véhicule est immobilisé par un incident grave non résolu : résolvez l'incident pour le rendre disponible.",
                HttpStatus.CONFLICT, "VHC_012");
    }

    public static VehicleException notCompliant(String gaps) {
        return new VehicleException(
                "Véhicule non conforme pour ce créneau : " + gaps
                        + ". Mettez à jour ses documents (assurance, visite technique, carte grise).",
                HttpStatus.UNPROCESSABLE_ENTITY, "VHC_013");
    }

    public static VehicleException immobilizationReasonRequired() {
        return new VehicleException(
                "Indiquez le motif de l'immobilisation (ex. assurance expirée, panne en attente de pièce).",
                HttpStatus.BAD_REQUEST, "VHC_014");
    }

    public static VehicleException retired() {
        return new VehicleException(
                "Ce véhicule a été retiré du parc : il est conservé pour l'historique et ne peut plus être modifié ni engagé.",
                HttpStatus.CONFLICT, "VHC_015");
    }

    public static VehicleException notDispatchable(String statusLabel) {
        return new VehicleException(
                "Ce véhicule n'est pas utilisable actuellement (" + statusLabel + ").",
                HttpStatus.CONFLICT, "VHC_016");
    }

    public static VehicleException hasHistory() {
        return new VehicleException(
                "Ce véhicule a déjà un historique (trajets, carburant, dépenses ou maintenance) : "
                        + "retirez-le du parc (vente, réforme…) au lieu de le supprimer.",
                HttpStatus.CONFLICT, "VHC_017");
    }

    public static VehicleException invalidRetirement(String detail) {
        return new VehicleException("Retrait du parc invalide : " + detail, HttpStatus.BAD_REQUEST, "VHC_018");
    }

    public static VehicleException invalidSpecs(String detail) {
        return new VehicleException("Caractéristiques du véhicule invalides : " + detail, HttpStatus.BAD_REQUEST, "VHC_019");
    }

    public static VehicleException odometerDecrease(java.math.BigDecimal current) {
        return new VehicleException(
                "Le relevé kilométrique ne peut pas être inférieur au dernier relevé connu (" + current.stripTrailingZeros().toPlainString() + " km).",
                HttpStatus.UNPROCESSABLE_ENTITY, "VHC_020");
    }

    public static VehicleException missingVehicleType() {
        return new VehicleException(
                "Le type de véhicule est obligatoire : il détermine la catégorie de permis exigée.",
                HttpStatus.BAD_REQUEST, "VHC_021");
    }
}
