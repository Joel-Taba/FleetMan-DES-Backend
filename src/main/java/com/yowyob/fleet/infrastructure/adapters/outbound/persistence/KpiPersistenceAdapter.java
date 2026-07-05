package com.yowyob.fleet.infrastructure.adapters.outbound.persistence;

import com.yowyob.fleet.domain.model.KpiSnapshot;
import com.yowyob.fleet.domain.ports.out.KpiPersistencePort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.KpiSnapshotEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.KpiSnapshotR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class KpiPersistenceAdapter implements KpiPersistencePort {

    private final KpiSnapshotR2dbcRepository repository;

    @Override
    public Mono<KpiSnapshot> save(KpiSnapshot snapshot) {
        KpiSnapshotEntity entity = toEntity(snapshot);
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID());
            entity.setNew(true);
        }
        return repository.save(entity).map(this::toDomain);
    }

    @Override
    public Mono<KpiSnapshot> findLatest(UUID entityId,
                                         KpiSnapshot.EntityType entityType,
                                         KpiSnapshot.PeriodType periodType) {
        return repository.findLatest(entityId, entityType.name(), periodType.name())
                .map(this::toDomain);
    }

    @Override
    public Flux<KpiSnapshot> findHistory(UUID entityId,
                                          KpiSnapshot.EntityType entityType,
                                          KpiSnapshot.PeriodType periodType,
                                          LocalDate from, LocalDate to) {
        return repository.findHistory(entityId, entityType.name(), periodType.name(), from, to)
                .map(this::toDomain);
    }

    @Override
    public Flux<KpiSnapshot> findTopByFleet(UUID fleetId,
                                             KpiSnapshot.EntityType entityType,
                                             KpiSnapshot.PeriodType periodType,
                                             LocalDate periodStart, int limit) {
        if (entityType == KpiSnapshot.EntityType.DRIVER) {
            return repository.findTopByScore(fleetId, entityType.name(),
                    periodType.name(), periodStart, limit).map(this::toDomain);
        }
        return repository.findTopByKm(fleetId, entityType.name(),
                periodType.name(), periodStart, limit).map(this::toDomain);
    }

    @Override
    public Mono<KpiSnapshot> findByEntityAndPeriod(UUID entityId,
                                                    KpiSnapshot.EntityType entityType,
                                                    KpiSnapshot.PeriodType periodType,
                                                    LocalDate periodStart) {
        return repository.findByEntityAndPeriod(entityId, entityType.name(),
                periodType.name(), periodStart).map(this::toDomain);
    }

    @Override
    public Flux<KpiSnapshot> findAllVehiclesByFleet(UUID fleetId,
                                                     KpiSnapshot.PeriodType periodType,
                                                     LocalDate periodStart) {
        return repository.findAllByFleetAndPeriod(fleetId,
                KpiSnapshot.EntityType.VEHICLE.name(), periodType.name(), periodStart)
                .map(this::toDomain);
    }

    @Override
    public Flux<KpiSnapshot> findAllDriversByFleet(UUID fleetId,
                                                    KpiSnapshot.PeriodType periodType,
                                                    LocalDate periodStart) {
        return repository.findAllByFleetAndPeriod(fleetId,
                KpiSnapshot.EntityType.DRIVER.name(), periodType.name(), periodStart)
                .map(this::toDomain);
    }

    // ── Conversions ───────────────────────────────────────────────────────────

    private KpiSnapshot toDomain(KpiSnapshotEntity e) {
        return new KpiSnapshot(
                e.getId(), e.getFleetId(),
                KpiSnapshot.EntityType.valueOf(e.getEntityType()),
                e.getEntityId(),
                KpiSnapshot.PeriodType.valueOf(e.getPeriodType()),
                e.getPeriodStart(), e.getPeriodEnd(),
                e.getTotalKm(), e.getTotalTrips(), e.getTotalDrivingHours(),
                e.getAvailabilityRate(),
                e.getTotalFuelCost(), e.getTotalFuelLiters(),
                e.getTotalMaintenanceCost(), e.getTotalIncidentCost(),
                e.getCostPerKm(), e.getFuelPer100km(),
                e.getTotalIncidents(), e.getIncidentRate(),
                e.getAvgDriverScore(), e.getDocComplianceRate(),
                e.getCalculatedAt()
        );
    }

    private KpiSnapshotEntity toEntity(KpiSnapshot s) {
        KpiSnapshotEntity e = new KpiSnapshotEntity();
        e.setId(s.id());
        e.setFleetId(s.fleetId());
        e.setEntityType(s.entityType().name());
        e.setEntityId(s.entityId());
        e.setPeriodType(s.periodType().name());
        e.setPeriodStart(s.periodStart());
        e.setPeriodEnd(s.periodEnd());
        e.setTotalKm(s.totalKm());
        e.setTotalTrips(s.totalTrips());
        e.setTotalDrivingHours(s.totalDrivingHours());
        e.setAvailabilityRate(s.availabilityRate());
        e.setTotalFuelCost(s.totalFuelCost());
        e.setTotalFuelLiters(s.totalFuelLiters());
        e.setTotalMaintenanceCost(s.totalMaintenanceCost());
        e.setTotalIncidentCost(s.totalIncidentCost());
        e.setCostPerKm(s.costPerKm());
        e.setFuelPer100km(s.fuelPer100Km());
        e.setTotalIncidents(s.totalIncidents());
        e.setIncidentRate(s.incidentRate());
        e.setAvgDriverScore(s.avgDriverScore());
        e.setDocComplianceRate(s.docComplianceRate());
        e.setCalculatedAt(s.calculatedAt() != null ? s.calculatedAt() : LocalDateTime.now());
        return e;
    }
}
