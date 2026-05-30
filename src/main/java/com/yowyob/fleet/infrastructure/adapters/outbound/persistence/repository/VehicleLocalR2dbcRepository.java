package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.VehicleLocalEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface VehicleLocalR2dbcRepository extends ReactiveCrudRepository<VehicleLocalEntity, UUID> {

    Flux<VehicleLocalEntity> findByFleetId(UUID fleetId);
    Flux<VehicleLocalEntity> findByManagerId(UUID managerId); // Nouveau
    Flux<VehicleLocalEntity> findByStatus(String status);

    Flux<VehicleLocalEntity> findByCurrentDriverId(UUID currentDriverId);
    @Query("SELECT COUNT(*) FROM fleet.vehicles WHERE fleet_id = :fleetId AND status = :status")
    Mono<Long> countByFleetIdAndStatus(UUID fleetId, String status);
    Mono<Long> countByFleetId(UUID fleetId);
    Mono<Long> countByManagerId(UUID managerId);
    // --- AJOUT POUR LES KPIS ---
    // Compte les véhicules d'un manager ayant un statut spécifique (ex: 'ON_TRIP')
    @Query("SELECT COUNT(*) FROM fleet.vehicles WHERE manager_id = :managerId AND status = :status")
    Mono<Long> countByManagerIdAndStatus(UUID managerId, String status);
}