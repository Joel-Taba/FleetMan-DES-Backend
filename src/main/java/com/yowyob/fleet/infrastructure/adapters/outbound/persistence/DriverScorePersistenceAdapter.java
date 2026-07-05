package com.yowyob.fleet.infrastructure.adapters.outbound.persistence;

import com.yowyob.fleet.domain.model.DriverScore;
import com.yowyob.fleet.domain.ports.out.DriverScorePersistencePort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.DriverScoreEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.DriverScoreR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Adapter de persistance pour les Scores Conducteur.
 * Implémente DriverScorePersistencePort via Spring Data R2DBC.
 */
@Component
@RequiredArgsConstructor
public class DriverScorePersistenceAdapter implements DriverScorePersistencePort {

    private final DriverScoreR2dbcRepository repository;

    @Override
    public Mono<DriverScore> save(DriverScore score) {
        DriverScoreEntity entity = toEntity(score);
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID());
            entity.setNew(true);
        }
        return repository.save(entity).map(this::toDomain);
    }

    @Override
    public Mono<DriverScore> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Mono<DriverScore> findLatest(UUID driverId, DriverScore.PeriodType periodType) {
        return repository.findLatestByDriverAndPeriodType(driverId, periodType.name())
                .map(this::toDomain);
    }

    @Override
    public Mono<DriverScore> findByDriverAndPeriod(UUID driverId,
                                                     DriverScore.PeriodType periodType,
                                                     LocalDate periodStart) {
        return repository.findByDriverAndPeriod(driverId, periodType.name(), periodStart)
                .map(this::toDomain);
    }

    @Override
    public Flux<DriverScore> findHistory(UUID driverId,
                                          DriverScore.PeriodType periodType,
                                          LocalDate from,
                                          LocalDate to) {
        return repository.findHistory(driverId, periodType.name(), from, to).map(this::toDomain);
    }

    @Override
    public Flux<DriverScore> findByFleetAndPeriod(UUID fleetId,
                                                   DriverScore.PeriodType periodType,
                                                   LocalDate periodStart) {
        return repository.findByFleetAndPeriod(fleetId, periodType.name(), periodStart)
                .map(this::toDomain);
    }

    @Override
    public Flux<DriverScore> findTopByFleet(UUID fleetId,
                                             DriverScore.PeriodType periodType,
                                             LocalDate periodStart,
                                             int limit) {
        return repository.findTopByFleet(fleetId, periodType.name(), periodStart, limit)
                .map(this::toDomain);
    }

    @Override
    public Flux<DriverScore> findBottomByFleet(UUID fleetId,
                                                DriverScore.PeriodType periodType,
                                                LocalDate periodStart,
                                                int limit) {
        return repository.findBottomByFleet(fleetId, periodType.name(), periodStart, limit)
                .map(this::toDomain);
    }

    @Override
    public Mono<Boolean> existsById(UUID id) {
        return repository.existsById(id);
    }

    @Override
    public Mono<Void> deleteById(UUID id) {
        return repository.deleteById(id);
    }

    // ── Conversion Entity → Domain ────────────────────────────────────────────

    private DriverScore toDomain(DriverScoreEntity e) {
        DriverScore.PeriodType periodType;
        try {
            periodType = DriverScore.PeriodType.valueOf(e.getPeriodType());
        } catch (IllegalArgumentException ex) {
            periodType = DriverScore.PeriodType.MONTHLY;
        }

        DriverScore.ScoreBadge badge;
        try {
            badge = DriverScore.ScoreBadge.valueOf(e.getBadge());
        } catch (IllegalArgumentException ex) {
            badge = DriverScore.resolveBadge(e.getFinalScore());
        }

        return new DriverScore(
                e.getId(), e.getDriverId(), e.getFleetId(), e.getManagerId(),
                periodType, e.getPeriodStart(), e.getPeriodEnd(),
                e.getIncidentCount(), e.getTotalTrips(),
                e.getFuelPer100Km(), e.getFleetAvgFuelPer100Km(),
                e.getDocComplianceRate(), e.getAbnormalMaintenanceCount(),
                e.getCompletedAssignments(), e.getNoShowAssignments(),
                e.getIncidentScore(), e.getFuelScore(),
                e.getComplianceScore(), e.getPunctualityScore(), e.getMaintenanceScore(),
                e.getFinalScore(), badge, e.getCalculatedAt()
        );
    }

    // ── Conversion Domain → Entity ────────────────────────────────────────────

    private DriverScoreEntity toEntity(DriverScore s) {
        DriverScoreEntity e = new DriverScoreEntity();
        e.setId(s.getId());
        e.setDriverId(s.getDriverId());
        e.setFleetId(s.getFleetId());
        e.setManagerId(s.getManagerId());
        e.setPeriodType(s.getPeriodType().name());
        e.setPeriodStart(s.getPeriodStart());
        e.setPeriodEnd(s.getPeriodEnd());
        e.setIncidentCount(s.getIncidentCount());
        e.setTotalTrips(s.getTotalTrips());
        e.setFuelPer100Km(s.getFuelPer100Km());
        e.setFleetAvgFuelPer100Km(s.getFleetAvgFuelPer100Km());
        e.setDocComplianceRate(s.getDocComplianceRate());
        e.setAbnormalMaintenanceCount(s.getAbnormalMaintenanceCount());
        e.setCompletedAssignments(s.getCompletedAssignments());
        e.setNoShowAssignments(s.getNoShowAssignments());
        e.setIncidentScore(s.getIncidentScore());
        e.setFuelScore(s.getFuelScore());
        e.setComplianceScore(s.getComplianceScore());
        e.setPunctualityScore(s.getPunctualityScore());
        e.setMaintenanceScore(s.getMaintenanceScore());
        e.setFinalScore(s.getFinalScore());
        e.setBadge(s.getBadge() != null ? s.getBadge().name() : null);
        e.setCalculatedAt(s.getCalculatedAt());
        return e;
    }
}
