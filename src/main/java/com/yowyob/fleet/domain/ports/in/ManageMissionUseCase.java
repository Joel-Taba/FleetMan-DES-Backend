package com.yowyob.fleet.domain.ports.in;

import com.yowyob.fleet.domain.model.Mission;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Port d'entrée : cas d'utilisation pour la gestion des Missions de livraison/transport.
 * Coordonne les véhicules, conducteurs, livraisons TiiBnTick (G1) et suivi Geofence (G3).
 */
public interface ManageMissionUseCase {

    // ── Commandes (Records) ───────────────────────────────────────────────────

    record CreateMissionCommand(
            UUID vehicleId,
            UUID driverId,
            UUID fleetManagerId,
            Mission.Type type,
            LocalDateTime scheduledStart,
            LocalDateTime scheduledEnd,
            UUID startLocationId,
            UUID endLocationId,
            UUID tiiBnTickDeliveryId,
            String tiiBnTickParcelId,
            String geofenceObjectId,
            String notes,
            UUID tenantId
    ) {}

    record UpdateMissionCommand(
            UUID missionId,
            LocalDateTime scheduledStart,
            LocalDateTime scheduledEnd,
            UUID startLocationId,
            UUID endLocationId,
            String notes
    ) {}

    // ── Use Cases ─────────────────────────────────────────────────────────────

    /**
     * Planifie et crée une nouvelle mission.
     * Vérifie la disponibilité du véhicule et du conducteur (pas de chevauchement ni maintenance).
     */
    Mono<Mission> createMission(CreateMissionCommand command);

    /**
     * Récupère une mission par son identifiant unique.
     */
    Mono<Mission> getById(UUID id);

    /**
     * Démarre une mission planifiée (PENDING -> ACTIVE).
     * Enregistre l'heure réelle de début et active le suivi télémétrique.
     */
    Mono<Mission> startMission(UUID missionId, UUID driverId);

    /**
     * Clôture une mission active (ACTIVE -> COMPLETED).
     * Exige une preuve de livraison conforme ou validée.
     */
    Mono<Mission> completeMission(UUID missionId, UUID driverId);

    /**
     * Annule une mission (PENDING/ACTIVE -> CANCELLED) avec un motif explicite.
     */
    Mono<Mission> cancelMission(UUID missionId, String reason, UUID operatorId);

    /**
     * Lie une mission existante à une commande/livraison TiiBnTick.
     */
    Mono<Mission> linkToTiiBnTickDelivery(UUID missionId, UUID deliveryId, String parcelId);

    /**
     * Associe l'objet de géorepérage Geofence à la mission.
     */
    Mono<Mission> linkToGeofence(UUID missionId, String geofenceObjectId);

    /**
     * Liste toutes les missions associées à un gestionnaire de flotte.
     */
    Flux<Mission> getMissionsByManager(UUID fleetManagerId);

    /**
     * Liste les missions confiées à un conducteur.
     */
    Flux<Mission> getMissionsByDriver(UUID driverId);

    /**
     * Liste les missions exécutées par un véhicule donné.
     */
    Flux<Mission> getMissionsByVehicle(UUID vehicleId);

    /**
     * Filtre les missions par statut.
     */
    Flux<Mission> getMissionsByStatus(Mission.Status status, UUID fleetManagerId);
}

