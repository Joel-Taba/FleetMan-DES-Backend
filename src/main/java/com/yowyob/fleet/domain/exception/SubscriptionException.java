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

    public static SubscriptionException featureDisabled(String featureLabel) {
        return new SubscriptionException(
                "Fonctionnalité non incluse dans votre plan : " + featureLabel
                        + ". Passez à un plan supérieur pour y accéder.",
                HttpStatus.FORBIDDEN, "SUB_006");
    }

    /** Plan incohérent (limites, prix…) : le message précise la règle enfreinte. */
    public static SubscriptionException invalidPlan(String reason) {
        return new SubscriptionException(reason, HttpStatus.UNPROCESSABLE_ENTITY, "SUB_007");
    }

    public static SubscriptionException planNameTaken(String name) {
        return new SubscriptionException(
                "Un plan nommé « " + name + " » existe déjà.", HttpStatus.CONFLICT, "SUB_008");
    }

    public static SubscriptionException planNotFound() {
        return new SubscriptionException("Plan tarifaire introuvable.", HttpStatus.NOT_FOUND, "SUB_009");
    }

    public static SubscriptionException planInactive(String name) {
        return new SubscriptionException(
                "Le plan « " + name + " » n'est plus proposé.", HttpStatus.UNPROCESSABLE_ENTITY, "SUB_010");
    }

    /** Passage à un plan plus petit que l'usage actuel (flottes, véhicules, chauffeurs). */
    public static SubscriptionException usageExceedsPlan(String planName, String details) {
        return new SubscriptionException(
                "Le plan « " + planName + " » ne couvre pas votre usage actuel (" + details
                        + "). Réduisez votre parc ou choisissez un plan supérieur.",
                HttpStatus.CONFLICT, "SUB_011");
    }

    public static SubscriptionException billingCycleUnavailable(String planName) {
        return new SubscriptionException(
                "Le plan « " + planName + " » n'est pas proposé en facturation annuelle.",
                HttpStatus.UNPROCESSABLE_ENTITY, "SUB_012");
    }

    public static SubscriptionException orderNotFound() {
        return new SubscriptionException("Commande d'abonnement introuvable.", HttpStatus.NOT_FOUND, "SUB_013");
    }

    public static SubscriptionException orderNotPayable(String status) {
        return new SubscriptionException(
                "Cette commande ne peut plus être réglée (statut : " + status + ").",
                HttpStatus.CONFLICT, "SUB_014");
    }

    public static SubscriptionException companyNotFound() {
        return new SubscriptionException("Entreprise introuvable.", HttpStatus.NOT_FOUND, "SUB_016");
    }

    public static SubscriptionException reasonRequired() {
        return new SubscriptionException("Indiquez le motif de cette opération.", HttpStatus.BAD_REQUEST, "SUB_017");
    }

    public static SubscriptionException invalidExtension(int max) {
        return new SubscriptionException("La prolongation doit compter de 1 à " + max + " jours.",
                HttpStatus.BAD_REQUEST, "SUB_018");
    }

    public static SubscriptionException freePlanHasNoDeadline() {
        return new SubscriptionException("Une offre gratuite n'a pas d'échéance à prolonger.",
                HttpStatus.CONFLICT, "SUB_019");
    }

    public static SubscriptionException alreadySuspended() {
        return new SubscriptionException("Cet abonnement est déjà suspendu.", HttpStatus.CONFLICT, "SUB_020");
    }

    public static SubscriptionException notSuspended() {
        return new SubscriptionException("Cet abonnement n'est pas suspendu.", HttpStatus.CONFLICT, "SUB_021");
    }

    public static SubscriptionException requestStillPending() {
        return new SubscriptionException("Cette entreprise est encore une demande : traitez-la depuis les demandes de souscription.",
                HttpStatus.CONFLICT, "SUB_022");
    }

    public static SubscriptionException invalidPaymentMethod() {
        return new SubscriptionException("Mode de règlement inconnu.", HttpStatus.BAD_REQUEST, "SUB_023");
    }

    public static SubscriptionException paymentReferenceRequired() {
        return new SubscriptionException("La référence du règlement est obligatoire pour ce mode.",
                HttpStatus.BAD_REQUEST, "SUB_024");
    }

    public static SubscriptionException paymentSimulationDisabled() {
        return new SubscriptionException(
                "La simulation de paiement est désactivée : le paiement passe par le service de paiement réel.",
                HttpStatus.FORBIDDEN, "SUB_015");
    }
}
