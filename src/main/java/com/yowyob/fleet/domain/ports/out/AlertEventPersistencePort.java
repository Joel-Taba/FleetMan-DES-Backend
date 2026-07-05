package com.yowyob.fleet.domain.ports.out;

import com.yowyob.fleet.domain.model.AlertEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Port sortant : contrat de persistance des Événements d'alerte.
 */
public interface AlertEventPersistencePort {

    Mono<AlertEvent> save(AlertEvent event);

    Mono<AlertEvent> findById(UUID id);

    /** Notifications non lues d'un manager, triées par date décroissante. */
    Flux<AlertEvent> findUnreadByManagerId(UUID managerId);

    /** Toutes les notifications d'un manager (historique complet). */
    Flux<AlertEvent> findAllByManagerId(UUID managerId);

    /** Compte les notifications non lues d'un manager. */
    Mono<Long> countUnreadByManagerId(UUID managerId);

    /**
     * Marque toutes les notifications UNREAD d'un manager comme READ.
     * Retourne le nombre de notifications mises à jour.
     */
    Mono<Long> markAllAsReadByManagerId(UUID managerId);

    Mono<Boolean> existsById(UUID id);

    Mono<Void> deleteById(UUID id);
}
