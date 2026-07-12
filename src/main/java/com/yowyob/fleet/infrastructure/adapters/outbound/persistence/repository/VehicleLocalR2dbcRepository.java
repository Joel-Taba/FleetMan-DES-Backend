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

    // Compte les véhicules d'un manager ayant un statut spécifique (ex: 'ON_TRIP')
    @Query("SELECT COUNT(*) FROM fleet.vehicles WHERE manager_id = :managerId AND status = :status")
    Mono<Long> countByManagerIdAndStatus(UUID managerId, String status);

    @Query("SELECT v.* FROM fleet.vehicles v " +
            "JOIN fleet.fleet_managers fm ON v.manager_id = fm.user_id " +
            "WHERE fm.company_name = (SELECT company_name FROM fleet.fleet_managers WHERE user_id = :userId)")
    Flux<VehicleLocalEntity> findAllBySameCompanyAsUser(UUID userId);
}