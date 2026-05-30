package com.yowyob.fleet.domain.ports.out;

import com.yowyob.fleet.domain.model.Maintenance;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Port sortant : contrat de persistance des Maintenances.
 * Implémenté par MaintenancePersistenceAdapter dans la couche infrastructure (R2DBC).
 */
public interface MaintenancePersistencePort {

    Mono<Maintenance> save(Maintenance maintenance);

    Mono<Maintenance> findById(UUID id);

    Flux<Maintenance> findAll();

    /**
     * Récupère toutes les maintenances des véhicules appartenant à un manager.
     */
    Flux<Maintenance> findAllByManagerId(UUID managerId);

    Flux<Maintenance> findByVehicleId(UUID vehicleId);

    Flux<Maintenance> findByDriverId(UUID driverId);

    Flux<Maintenance> findByDateRange(LocalDateTime start, LocalDateTime end);

    /**
     * Compte les maintenances impliquant un chauffeur donné.
     */
    Mono<Long> countByDriverId(UUID driverId);

    Mono<Boolean> existsById(UUID id);

    Mono<Void> deleteById(UUID id);
}
