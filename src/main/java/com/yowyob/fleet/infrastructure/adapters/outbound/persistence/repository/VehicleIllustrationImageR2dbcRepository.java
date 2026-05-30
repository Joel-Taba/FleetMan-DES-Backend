package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.VehicleIllustrationImageEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * Repository réactif pour la gestion des images d'illustration des véhicules.
 * Gère la table fleet.vehicle_illustration_images (Relation 1-N).
 */
@Repository
public interface VehicleIllustrationImageR2dbcRepository extends ReactiveCrudRepository<VehicleIllustrationImageEntity, UUID> {

    /**
     * Récupère toutes les images associées à un véhicule spécifique.
     * 
     * @param vehicleId L'identifiant unique du véhicule.
     * @return Un flux d'entités d'images.
     */
    Flux<VehicleIllustrationImageEntity> findByVehicleId(UUID vehicleId);

    /**
     * Supprime toutes les images d'un véhicule (utile lors de la suppression du véhicule).
     * 
     * @param vehicleId L'identifiant unique du véhicule.
     * @return Un Mono vide signalant la fin de l'opération.
     */
    // Mono<Void> deleteByVehicleId(UUID vehicleId); // Déjà géré par ON DELETE CASCADE en SQL, mais possible ici aussi.
}