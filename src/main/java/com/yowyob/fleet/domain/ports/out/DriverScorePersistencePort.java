package com.yowyob.fleet.domain.ports.out;

import com.yowyob.fleet.domain.model.DriverScore;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Port sortant : contrat de persistance des Scores Conducteur.
 * Implémenté par DriverScorePersistenceAdapter dans la couche infrastructure (R2DBC).
 */
public interface DriverScorePersistencePort {

    Mono<DriverScore> save(DriverScore score);

    Mono<DriverScore> findById(UUID id);

    /**
     * Score le plus récent d'un chauffeur pour un type de période.
     */
    Mono<DriverScore> findLatest(UUID driverId, DriverScore.PeriodType periodType);

    /**
     * Score d'un chauffeur pour une période spécifique (start + type).
     */
    Mono<DriverScore> findByDriverAndPeriod(UUID driverId,
                                             DriverScore.PeriodType periodType,
                                             LocalDate periodStart);

    /**
     * Historique des scores d'un chauffeur sur une plage.
     */
    Flux<DriverScore> findHistory(UUID driverId,
                                   DriverScore.PeriodType periodType,
                                   LocalDate from,
                                   LocalDate to);

    /**
     * Tous les scores d'une flotte pour une période donnée.
     */
    Flux<DriverScore> findByFleetAndPeriod(UUID fleetId,
                                            DriverScore.PeriodType periodType,
                                            LocalDate periodStart);

    /**
     * Top N scores d'une flotte (meilleurs conducteurs).
     */
    Flux<DriverScore> findTopByFleet(UUID fleetId,
                                      DriverScore.PeriodType periodType,
                                      LocalDate periodStart,
                                      int limit);

    /**
     * Bottom N scores d'une flotte (conducteurs à risque).
     */
    Flux<DriverScore> findBottomByFleet(UUID fleetId,
                                         DriverScore.PeriodType periodType,
                                         LocalDate periodStart,
                                         int limit);

    Mono<Boolean> existsById(UUID id);

    Mono<Void> deleteById(UUID id);
}
