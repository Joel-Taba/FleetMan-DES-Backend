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

    /**
     * Assigne un rôle FleetMan (portée TENANT) à un utilisateur Kernel, sans créer d'acteur
     * ni exiger d'organisation. Utilisé pour les rôles plateforme (FLEET_ADMIN,
     * FLEET_SUPER_ADMIN) et pour les gestionnaires approuvés (FLEET_MANAGER) qui n'ont pas
     * encore d'organisation/flotte au moment de la création du compte.
     *
     * Best-effort : ne doit jamais bloquer le flux appelant en cas d'échec Kernel.
     */
    Mono<Void> assignPlatformRole(UUID kernelUserId, String fleetRole);
}
