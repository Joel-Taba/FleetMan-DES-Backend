package com.yowyob.fleet.domain.model;

/**
 * État d'une livraison Fleetman, synchronisé avec le service TiiBnTick (G1).
 */
public enum DeliveryStatus {
    /** En attente de prise en charge */
    PENDING,
    /** Prise en charge par une antenne ou agence */
    PICKED_UP,
    /** En cours de transit (mission Fleetman active) */
    IN_TRANSIT,
    /** En transit / passage par un hub relais */
    IN_HUB,
    /** Livré avec preuve de livraison conforme */
    DELIVERED,
    /** Échec de livraison */
    FAILED,
    /** Colis retourné */
    RETURNED,
    /** Litige en cours */
    DISPUTED
}
