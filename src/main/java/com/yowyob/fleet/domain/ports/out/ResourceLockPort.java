package com.yowyob.fleet.domain.ports.out;

import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Verrou exclusif sur des ressources (véhicules, chauffeurs) pour la durée de la transaction
 * en cours. Sérialise les demandes concurrentes qui engagent les mêmes ressources : sans lui,
 * deux requêtes simultanées pouvaient toutes deux constater un créneau libre puis l'occuper.
 * L'appelant doit s'exécuter dans une transaction ; le verrou est libéré à sa fin.
 */
public interface ResourceLockPort {

    /** Verrouille les ressources données (null ignorés) ; attend si une autre transaction les détient. */
    Mono<Void> lock(UUID... resourceIds);
}
