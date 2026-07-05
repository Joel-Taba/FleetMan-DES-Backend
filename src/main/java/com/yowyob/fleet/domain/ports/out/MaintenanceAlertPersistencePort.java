package com.yowyob.fleet.domain.ports.out;

import com.yowyob.fleet.domain.model.MaintenanceAlert;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Port sortant : contrat de persistance des Alertes de Maintenance Préventive.
 */
public interface MaintenanceAlertPersistencePort {

    Mono<MaintenanceAlert> save(MaintenanceAlert alert);

    Mono<MaintenanceAlert> findById(UUID id);

    Flux<MaintenanceAlert> findAll();

    /** Toutes les alertes non résolues d'un manager. */
    Flux<MaintenanceAlert> findActiveByManagerId(UUID managerId);

    /** Alertes urgentes (DUE + OVERDUE) pour le dashboard. */
    Flux<MaintenanceAlert> findUrgentByManagerId(UUID managerId);

    /** Toutes les alertes d'un véhicule. */
    Flux<MaintenanceAlert> findByVehicleId(UUID vehicleId);

    /** Alertes actives d'une flotte. */
    Flux<MaintenanceAlert> findActiveByFleetId(UUID fleetId);

    /**
     * Alerte active pour un véhicule et un type de maintenance spécifique.
     * Utilisé pour éviter les doublons lors de la génération d'alertes.
     */
    Mono<MaintenanceAlert> findActiveByVehicleAndType(UUID vehicleId,
                                                       String maintenanceType);

    Mono<Boolean> existsById(UUID id);

    Mono<Void> deleteById(UUID id);
}
