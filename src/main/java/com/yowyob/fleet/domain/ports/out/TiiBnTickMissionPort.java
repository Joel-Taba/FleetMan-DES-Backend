package com.yowyob.fleet.domain.ports.out;

import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Port sortant (ACL) : communication avec le service TiiBnTick / Cœur central (G1).
 * Transmet les statuts de mission, événements de départ, passage de hub, et preuve de livraison.
 */
public interface TiiBnTickMissionPort {

    record DeliverySyncEvent(
            UUID missionId,
            UUID deliveryId,
            String parcelId,
            String status,
            String location,
            String timestamp
    ) {}

    /**
     * Notifie TiiBnTick de la prise en charge ou démarrage de la mission.
     */
    Mono<Void> notifyMissionStarted(UUID deliveryId, UUID missionId, UUID driverId, UUID vehicleId);

    /**
     * Notifie TiiBnTick de l'achèvement de la livraison avec la référence de preuve.
     */
    Mono<Void> notifyMissionCompleted(UUID deliveryId, UUID missionId, UUID proofId);

    /**
     * Notifie TiiBnTick d'un incident ou blocage terrain impactant la livraison.
     */
    Mono<Void> notifyMissionIncident(UUID deliveryId, UUID missionId, String incidentType, String details);
}

