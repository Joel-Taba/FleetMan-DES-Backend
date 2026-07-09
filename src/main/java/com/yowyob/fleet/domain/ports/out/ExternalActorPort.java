package com.yowyob.fleet.domain.ports.out;

import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Port sortant vers actor-core du Kernel RT-Comops.
 * Gère la création d'acteurs et leur rattachement à une organisation.
 */
public interface ExternalActorPort {

    boolean isEnabled();

    /**
     * Crée un acteur Kernel pour un conducteur et le rattache à l'organisation de la flotte.
     *
     * @param kernelUserId   identifiant utilisateur Kernel (auth-core)
     * @param email          email du conducteur
     * @param firstName      prénom
     * @param lastName       nom
     * @param organizationId organisation Kernel de la flotte
     * @param fleetRole      rôle FleetMan (ex. FLEET_DRIVER) pour assignRole optionnel
     * @return identifiant de l'acteur Kernel créé
     */
    Mono<UUID> provisionDriverActor(
            UUID kernelUserId,
            String email,
            String firstName,
            String lastName,
            UUID organizationId,
            String fleetRole);
}
