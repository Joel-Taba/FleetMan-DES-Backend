package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.DriverScoreEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

@Repository
public interface DriverScoreR2dbcRepository extends ReactiveCrudRepository<DriverScoreEntity, UUID> {

    /**
     * Score le plus récent d'un chauffeur pour un type de période.
     */
    @Query("SELECT * FROM fleet.driver_scores WHERE driver_id = :driverId AND period_type = :periodType ORDER BY period_start DESC LIMIT 1")
    Mono<DriverScoreEntity> findLatestByDriverAndPeriodType(UUID driverId, String periodType);

    /**
     * Score d'un chauffeur pour une période précise (idempotence du calcul).
     */
    @Query("SELECT * FROM fleet.driver_scores WHERE driver_id = :driverId AND period_type = :periodType AND period_start = :periodStart LIMIT 1")
    Mono<DriverScoreEntity> findByDriverAndPeriod(UUID driverId, String periodType, LocalDate periodStart);

    /**
     * Historique d'un chauffeur sur une plage de dates.
     */
    @Query("SELECT * FROM fleet.driver_scores WHERE driver_id = :driverId AND period_type = :periodType AND period_start BETWEEN :from AND :to ORDER BY period_start DESC")
    Flux<DriverScoreEntity> findHistory(UUID driverId, String periodType, LocalDate from, LocalDate to);

    /**
     * Tous les scores d'une flotte pour une période (1 score par chauffeur).
     */
    @Query("SELECT * FROM fleet.driver_scores WHERE fleet_id = :fleetId AND period_type = :periodType AND period_start = :periodStart ORDER BY final_score DESC")
    Flux<DriverScoreEntity> findByFleetAndPeriod(UUID fleetId, String periodType, LocalDate periodStart);

    /**
     * Top N chauffeurs (meilleurs scores) d'une flotte.
     */
    @Query("SELECT * FROM fleet.driver_scores WHERE fleet_id = :fleetId AND period_type = :periodType AND period_start = :periodStart ORDER BY final_score DESC LIMIT :lim")
    Flux<DriverScoreEntity> findTopByFleet(UUID fleetId, String periodType, LocalDate periodStart, int lim);

    /**
     * Bottom N chauffeurs (scores les plus bas) d'une flotte.
     */
    @Query("SELECT * FROM fleet.driver_scores WHERE fleet_id = :fleetId AND period_type = :periodType AND period_start = :periodStart ORDER BY final_score ASC LIMIT :lim")
    Flux<DriverScoreEntity> findBottomByFleet(UUID fleetId, String periodType, LocalDate periodStart, int lim);
}
