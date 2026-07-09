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
            "Véhicule indisponible (Statut: " + status + ").",
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
}
