package com.yowyob.fleet.domain.ports.out;

import com.yowyob.fleet.domain.model.Incident;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Port sortant : contrat de persistance des Incidents.
 * Implémenté par IncidentPersistenceAdapter dans la couche infrastructure (R2DBC).
 *
 * Expose des requêtes métier avancées (par type, sévérité, statut, coût total)
 * pour alimenter les tableaux de bord et KPIs.
 */
public interface IncidentPersistencePort {

    Mono<Incident> save(Incident incident);

    Mono<Incident> findById(UUID id);

    Flux<Incident> findAll();

    /**
     * Récupère tous les incidents des véhicules appartenant à un manager.
     */
    Flux<Incident> findAllByManagerId(UUID managerId);

    Flux<Incident> findByVehicleId(UUID vehicleId);

    Flux<Incident> findByDriverId(UUID driverId);

    Flux<Incident> findByType(Incident.Type type, UUID managerId);

    Flux<Incident> findBySeverity(Incident.Severity severity, UUID managerId);

    Flux<Incident> findByStatus(Incident.Status status, UUID managerId);

    /**
     * Récupère les incidents encore ouverts (REPORTED ou UNDER_INVESTIGATION).
     */
    Flux<Incident> findOpenIncidents(UUID managerId);

    Flux<Incident> findByDateRange(LocalDateTime start, LocalDateTime end, UUID managerId);

    // ── Agrégats pour KPIs ────────────────────────────────────────────────────

    Mono<Long> countByVehicleId(UUID vehicleId);

    Mono<Long> countByDriverId(UUID driverId);

    /**
     * Somme des coûts de tous les incidents d'un véhicule.
     */
    Mono<BigDecimal> getTotalCostByVehicleId(UUID vehicleId);

    /**
     * Somme des coûts de tous les incidents impliquant un chauffeur.
     */
    Mono<BigDecimal> getTotalCostByDriverId(UUID driverId);

    Mono<Boolean> existsById(UUID id);

    Mono<Void> deleteById(UUID id);
}
