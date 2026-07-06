package com.yowyob.fleet.domain.exception;

import org.springframework.http.HttpStatus;

public class SubscriptionException extends DomainException {

    private SubscriptionException(String message, HttpStatus status, String code) {
        super(message, status, code);
    }

    public static SubscriptionException expired() {
        return new SubscriptionException(
                "Votre abonnement a expiré. Renouvelez votre plan pour continuer.",
                HttpStatus.FORBIDDEN, "SUB_001");
    }

    public static SubscriptionException suspended() {
        return new SubscriptionException(
                "Votre compte est suspendu. Contactez le support FleetMan.",
                HttpStatus.FORBIDDEN, "SUB_002");
    }

    public static SubscriptionException fleetLimitReached(int max) {
        return new SubscriptionException(
                "Limite de flottes atteinte pour votre plan (max " + max + ").",
                HttpStatus.FORBIDDEN, "SUB_003");
    }

    public static SubscriptionException vehicleLimitReached(int max) {
        return new SubscriptionException(
                "Limite de véhicules atteinte pour votre plan (max " + max + ").",
                HttpStatus.FORBIDDEN, "SUB_004");
    }

    public static SubscriptionException driverLimitReached(int max) {
        return new SubscriptionException(
                "Limite de conducteurs atteinte pour votre plan (max " + max + ").",
                HttpStatus.FORBIDDEN, "SUB_005");
    }

    public static SubscriptionException featureDisabled(String feature) {
        return new SubscriptionException(
                "Fonctionnalité non incluse dans votre plan : " + feature,
                HttpStatus.FORBIDDEN, "SUB_006");
    }
}
