package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.DriverEntity;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface DriverR2dbcRepository extends ReactiveCrudRepository<DriverEntity, UUID> {

    /**
     * Recherche tous les chauffeurs appartenant à une flotte spécifique.
     * Spring Data génère automatiquement le SQL basé sur le nom du champ 'fleetId' dans DriverEntity.
     */
    Flux<DriverEntity> findByFleetId(UUID fleetId);

    /**
     * Recherche par statut (actif/inactif).
     */
    Flux<DriverEntity> findByStatus(String status);

    /**
     * Recherche par ID de véhicule assigné.
     */
    Mono<DriverEntity> findByAssignedVehicleId(UUID assignedVehicleId);

    Mono<DriverEntity> findByKernelActorId(UUID kernelActorId);
    // --- AJOUT TÂCHE 6.2 ---
    Mono<Long> countByFleetId(UUID fleetId);

    // --- CORRECTIF TÂCHE 7.2 : Compter les chauffeurs par Manager ---
    @Query("SELECT COUNT(d.*) FROM fleet.drivers d " +
           "JOIN fleet.fleets f ON d.fleet_id = f.id " +
           "WHERE f.manager_id = :managerId")
    Mono<Long> countByManagerId(UUID managerId);
}