package com.yowyob.fleet.domain.ports.out;

import com.yowyob.fleet.domain.model.KpiSnapshot;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Port sortant — Contrat de persistance pour les KPI snapshots.
 */
public interface KpiPersistencePort {

    Mono<KpiSnapshot> save(KpiSnapshot snapshot);

    Mono<KpiSnapshot> findLatest(UUID entityId,
                                  KpiSnapshot.EntityType entityType,
                                  KpiSnapshot.PeriodType periodType);

    Flux<KpiSnapshot> findHistory(UUID entityId,
                                   KpiSnapshot.EntityType entityType,
                                   KpiSnapshot.PeriodType periodType,
                                   LocalDate from,
                                   LocalDate to);

    Flux<KpiSnapshot> findTopByFleet(UUID fleetId,
                                      KpiSnapshot.EntityType entityType,
                                      KpiSnapshot.PeriodType periodType,
                                      LocalDate periodStart,
                                      int limit);

    Mono<KpiSnapshot> findByEntityAndPeriod(UUID entityId,
                                             KpiSnapshot.EntityType entityType,
                                             KpiSnapshot.PeriodType periodType,
                                             LocalDate periodStart);

    /** Tous les véhicules d'une flotte pour une période donnée. */
    Flux<KpiSnapshot> findAllVehiclesByFleet(UUID fleetId,
                                              KpiSnapshot.PeriodType periodType,
                                              LocalDate periodStart);

    /** Tous les conducteurs d'une flotte pour une période donnée. */
    Flux<KpiSnapshot> findAllDriversByFleet(UUID fleetId,
                                             KpiSnapshot.PeriodType periodType,
                                             LocalDate periodStart);
}
