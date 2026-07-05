package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.KpiSnapshotEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

public interface KpiSnapshotR2dbcRepository
        extends ReactiveCrudRepository<KpiSnapshotEntity, UUID> {

    @Query("""
            SELECT * FROM fleet.kpi_snapshots
            WHERE entity_id = :entityId
              AND entity_type = :entityType
              AND period_type = :periodType
            ORDER BY period_start DESC
            LIMIT 1
            """)
    Mono<KpiSnapshotEntity> findLatest(UUID entityId, String entityType, String periodType);

    @Query("""
            SELECT * FROM fleet.kpi_snapshots
            WHERE entity_id = :entityId
              AND entity_type = :entityType
              AND period_type = :periodType
              AND period_start >= :from
              AND period_start <= :to
            ORDER BY period_start DESC
            """)
    Flux<KpiSnapshotEntity> findHistory(UUID entityId, String entityType,
                                         String periodType,
                                         LocalDate from, LocalDate to);

    @Query("""
            SELECT * FROM fleet.kpi_snapshots
            WHERE fleet_id = :fleetId
              AND entity_type = :entityType
              AND period_type = :periodType
              AND period_start = :periodStart
            ORDER BY total_km DESC NULLS LAST
            LIMIT :limitVal
            """)
    Flux<KpiSnapshotEntity> findTopByKm(UUID fleetId, String entityType,
                                         String periodType, LocalDate periodStart,
                                         int limitVal);

    @Query("""
            SELECT * FROM fleet.kpi_snapshots
            WHERE fleet_id = :fleetId
              AND entity_type = :entityType
              AND period_type = :periodType
              AND period_start = :periodStart
            ORDER BY avg_driver_score DESC NULLS LAST
            LIMIT :limitVal
            """)
    Flux<KpiSnapshotEntity> findTopByScore(UUID fleetId, String entityType,
                                            String periodType, LocalDate periodStart,
                                            int limitVal);

    @Query("""
            SELECT * FROM fleet.kpi_snapshots
            WHERE entity_id = :entityId
              AND entity_type = :entityType
              AND period_type = :periodType
              AND period_start = :periodStart
            LIMIT 1
            """)
    Mono<KpiSnapshotEntity> findByEntityAndPeriod(UUID entityId, String entityType,
                                                   String periodType, LocalDate periodStart);

    @Query("""
            SELECT * FROM fleet.kpi_snapshots
            WHERE fleet_id = :fleetId
              AND entity_type = :entityType
              AND period_type = :periodType
              AND period_start = :periodStart
            """)
    Flux<KpiSnapshotEntity> findAllByFleetAndPeriod(UUID fleetId, String entityType,
                                                     String periodType, LocalDate periodStart);
}
