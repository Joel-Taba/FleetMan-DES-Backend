package com.yowyob.fleet.domain.ports.out;

import com.yowyob.fleet.domain.model.MaintenancePlan;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Port sortant : contrat de persistance des Plans de Maintenance Préventive.
 */
public interface MaintenancePlanPersistencePort {

    Mono<MaintenancePlan> save(MaintenancePlan plan);

    Mono<MaintenancePlan> findById(UUID id);

    Flux<MaintenancePlan> findAll();

    Flux<MaintenancePlan> findByFleetId(UUID fleetId);

    Flux<MaintenancePlan> findByVehicleId(UUID vehicleId);

    Flux<MaintenancePlan> findByManagerId(UUID managerId);

    /** Plans actifs pour une flotte (pour le job d'évaluation). */
    Flux<MaintenancePlan> findActiveByFleetId(UUID fleetId);

    /**
     * Plan actif pour un véhicule et un type de maintenance donné.
     * Priorité : plan VEHICLE > plan FLEET.
     */
    Mono<MaintenancePlan> findEffectivePlan(UUID vehicleId,
                                             UUID fleetId,
                                             MaintenancePlan.MaintenanceType type);

    Mono<Boolean> existsById(UUID id);

    Mono<Void> deleteById(UUID id);
}
