package com.yowyob.fleet.domain.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Exceptions pour la gestion des trajets et de la télémétrie (Module 11).
 */
public class TripException extends DomainException {

    protected TripException(
        String message,
        HttpStatus status,
        String businessCode
    ) {
        super(message, status, businessCode);
    }

    public static TripException notFound(UUID id) {
        return new TripException(
            "Trajet introuvable.",
            HttpStatus.NOT_FOUND,
            "TRP_001"
        );
    }

    public static TripException driverOccupied() {
        return new TripException(
            "Ce conducteur est déjà affecté à un trajet en cours.",
            HttpStatus.CONFLICT,
            "TRP_002"
        );
    }

    public static TripException vehicleOccupied() {
        return new TripException(
            "Ce véhicule est déjà utilisé pour une autre course.",
            HttpStatus.CONFLICT,
            "TRP_003"
        );
    }

    public static TripException vehicleUnavailable(String status) {
        return new TripException(
            "Véhicule indisponible : " + com.yowyob.fleet.domain.model.VehicleLifecycle.label(status) + ".",
            HttpStatus.BAD_REQUEST,
            "TRP_004"
        );
    }

    public static TripException vehicleNotAssigned() {
        return new TripException(
            "Accès refusé : Ce véhicule ne vous est pas assigné.",
            HttpStatus.FORBIDDEN,
            "TRP_005"
        );
    }

    public static TripException actionOnCompletedTrip() {
        return new TripException(
            "Impossible d'agir sur une course déjà terminée.",
            HttpStatus.UNPROCESSABLE_ENTITY,
            "TRP_006"
        );
    }

    public static TripException notFoundByCode(String code) {
        return new TripException(
            "Aucun trajet trouvé avec le code : " + code,
            HttpStatus.NOT_FOUND,
            "TRP_007"
        );
    }

    public static TripException tripNotModifiable() {
        return new TripException(
            "Ce trajet est clôturé et ne peut plus être modifié.",
            HttpStatus.UNPROCESSABLE_ENTITY,
            "TRP_008"
        );
    }

    public static TripException forbidden() {
        return new TripException(
            "Accès refusé à ce trajet.",
            HttpStatus.FORBIDDEN,
            "TRP_009"
        );
    }

    public static TripException invalidStartState() {
        return new TripException(
            "Seuls les trajets créés peuvent être lancés.",
            HttpStatus.UNPROCESSABLE_ENTITY,
            "TRP_010"
        );
    }

    public static TripException codeCollision() {
        return new TripException(
            "Le code de trajet généré était déjà utilisé. Merci de réessayer.",
            HttpStatus.CONFLICT,
            "TRP_011"
        );
    }

    public static TripException invalidReturnKmIndex() {
        return new TripException(
            "L'index kilométrique de retour doit être supérieur ou égal au départ.",
            HttpStatus.UNPROCESSABLE_ENTITY,
            "TRP_012"
        );
    }

    public static TripException vehicleSlotConflict() {
        return new TripException(
            "Ce véhicule est déjà engagé (trajet ou affectation à un autre conducteur) sur ce créneau horaire.",
            HttpStatus.CONFLICT,
            "TRP_013"
        );
    }

    public static TripException driverSlotConflict() {
        return new TripException(
            "Ce conducteur est déjà engagé (trajet ou affectation sur un autre véhicule) sur ce créneau horaire.",
            HttpStatus.CONFLICT,
            "TRP_014"
        );
    }

    public static TripException invalidSchedule() {
        return new TripException(
            "Le retour prévu doit être postérieur au départ prévu.",
            HttpStatus.UNPROCESSABLE_ENTITY,
            "TRP_015"
        );
    }

    public static TripException returnBeforeDeparture() {
        return new TripException(
            "La date et l'heure de retour ne peuvent pas précéder le départ.",
            HttpStatus.UNPROCESSABLE_ENTITY,
            "TRP_016"
        );
    }

    public static TripException notDeparted() {
        return new TripException(
            "Le départ de ce trajet n'a pas été enregistré : lancez-le avant d'enregistrer son retour.",
            HttpStatus.UNPROCESSABLE_ENTITY,
            "TRP_017"
        );
    }

    public static TripException vehicleNotInFleet() {
        return new TripException(
            "Ce véhicule n'appartient pas à la flotte du trajet.",
            HttpStatus.UNPROCESSABLE_ENTITY,
            "TRP_019"
        );
    }

    public static TripException driverInactive() {
        return new TripException(
            "Ce conducteur est inactif ou n'est rattaché à aucune flotte : il ne peut pas être engagé sur un trajet.",
            HttpStatus.UNPROCESSABLE_ENTITY,
            "TRP_018"
        );
    }

    public static TripException returnFuelExceedsAvailable(java.math.BigDecimal available) {
        return new TripException(
            "L'index carburant de retour dépasse le carburant disponible ("
                + available.stripTrailingZeros().toPlainString()
                + " L : départ + pleins enregistrés pendant le trajet). "
                + "Enregistrez le plein manquant ou corrigez l'index.",
            HttpStatus.UNPROCESSABLE_ENTITY,
            "TRP_020"
        );
    }

    public static TripException departureKmBelowOdometer(java.math.BigDecimal odometer) {
        return new TripException(
            "L'index kilométrique de départ est inférieur au dernier relevé du compteur ("
                + odometer.stripTrailingZeros().toPlainString() + " km).",
            HttpStatus.UNPROCESSABLE_ENTITY,
            "TRP_021"
        );
    }

    public static TripException departureFuelExceedsTank(java.math.BigDecimal capacity) {
        return new TripException(
            "L'index carburant de départ dépasse la capacité du réservoir ("
                + capacity.stripTrailingZeros().toPlainString() + " L).",
            HttpStatus.UNPROCESSABLE_ENTITY,
            "TRP_022"
        );
    }

    public static TripException invalidArrivalState() {
        return new TripException(
            "Seul un trajet parti (et pas encore arrivé) peut être déclaré arrivé à destination.",
            HttpStatus.CONFLICT,
            "TRP_023"
        );
    }

    public static TripException detailNotInTrip() {
        return new TripException(
            "Cette ligne de chargement n'appartient pas au manifeste du trajet.",
            HttpStatus.UNPROCESSABLE_ENTITY,
            "TRP_024"
        );
    }

    public static TripException invalidDeliveredQuantity(int loaded) {
        return new TripException(
            "La quantité livrée doit être comprise entre 0 et la quantité chargée (" + loaded + ").",
            HttpStatus.UNPROCESSABLE_ENTITY,
            "TRP_025"
        );
    }

    public static TripException submissionNotPending() {
        return new TripException(
            "Complément introuvable ou déjà traité.",
            HttpStatus.NOT_FOUND,
            "TRP_026"
        );
    }

    public static TripException invalidStop(String message) {
        return new TripException(message, HttpStatus.UNPROCESSABLE_ENTITY, "TRP_027");
    }

    public static TripException stopNotFound() {
        return new TripException("Arrêt introuvable sur ce trajet.", HttpStatus.NOT_FOUND, "TRP_028");
    }

    public static TripException stopNotServable(String status) {
        return new TripException("Cet arrêt est déjà clos (" + status + ").", HttpStatus.CONFLICT, "TRP_029");
    }

    public static TripException stopsNotEditable() {
        return new TripException("Les arrêts ne se modifient plus une fois le trajet clos.", HttpStatus.CONFLICT, "TRP_030");
    }

    public static TripException stopsReorderOnlyPlanned() {
        return new TripException("L'ordre et la suppression des arrêts ne se changent qu'avant le départ ; en route, passez l'arrêt.",
                HttpStatus.CONFLICT, "TRP_031");
    }

    public static TripException lastStopRequired() {
        return new TripException("Une tournée garde au moins un arrêt.", HttpStatus.CONFLICT, "TRP_032");
    }

    public static TripException recipientRequired() {
        return new TripException("Le nom du réceptionnaire est obligatoire pour une livraison.",
                HttpStatus.UNPROCESSABLE_ENTITY, "TRP_033");
    }

    public static TripException invalidProof() {
        return new TripException("La preuve doit être un fichier déposé dans FleetMan.", HttpStatus.UNPROCESSABLE_ENTITY, "TRP_034");
    }

    public static TripException stopActionNeedsDeparture() {
        return new TripException("Les arrêts se servent une fois le trajet parti.", HttpStatus.CONFLICT, "TRP_035");
    }
}
