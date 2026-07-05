package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.model.KpiSnapshot;
import com.yowyob.fleet.domain.ports.in.KpiUseCase;
import com.yowyob.fleet.domain.ports.out.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class KpiService implements KpiUseCase {

    private final KpiPersistencePort kpiPort;
    private final VehiclePersistencePort vehiclePort;
    private final DriverPersistencePort driverPort;
    private final MaintenancePersistencePort maintenancePort;
    private final IncidentPersistencePort incidentPort;
    private final FuelRechargePersistencePort fuelPort;

    // ── Lecture ───────────────────────────────────────────────────────────────

    @Override
    public Mono<KpiSnapshot> getLatestFleetKpi(UUID fleetId,
                                                KpiSnapshot.PeriodType periodType) {
        return kpiPort.findLatest(fleetId, KpiSnapshot.EntityType.FLEET, periodType)
                .switchIfEmpty(recalculateFleetKpi(fleetId, periodType,
                        currentPeriodStart(periodType)));
    }

    @Override
    public Mono<KpiSnapshot> getLatestVehicleKpi(UUID vehicleId,
                                                  KpiSnapshot.PeriodType periodType) {
        return kpiPort.findLatest(vehicleId, KpiSnapshot.EntityType.VEHICLE, periodType);
    }

    @Override
    public Mono<KpiSnapshot> getLatestDriverKpi(UUID driverId,
                                                 KpiSnapshot.PeriodType periodType) {
        return kpiPort.findLatest(driverId, KpiSnapshot.EntityType.DRIVER, periodType);
    }

    @Override
    public Flux<KpiSnapshot> getFleetKpiHistory(UUID fleetId,
                                                  KpiSnapshot.PeriodType periodType,
                                                  LocalDate from,
                                                  LocalDate to) {
        return kpiPort.findHistory(fleetId, KpiSnapshot.EntityType.FLEET,
                periodType, from, to);
    }

    @Override
    public Flux<KpiSnapshot> getTopVehiclesByKm(UUID fleetId,
                                                  KpiSnapshot.PeriodType periodType,
                                                  int limit) {
        return kpiPort.findTopByFleet(fleetId, KpiSnapshot.EntityType.VEHICLE,
                periodType, currentPeriodStart(periodType), limit);
    }

    @Override
    public Flux<KpiSnapshot> getTopDriversByScore(UUID fleetId,
                                                   KpiSnapshot.PeriodType periodType,
                                                   int limit) {
        return kpiPort.findTopByFleet(fleetId, KpiSnapshot.EntityType.DRIVER,
                periodType, currentPeriodStart(periodType), limit);
    }

    @Override
    public Mono<KpiComparisonDto> compareFleetKpi(UUID fleetId,
                                                   KpiSnapshot.PeriodType periodType,
                                                   LocalDate period1Start,
                                                   LocalDate period2Start) {
        Mono<KpiSnapshot> p1 = kpiPort.findByEntityAndPeriod(
                fleetId, KpiSnapshot.EntityType.FLEET, periodType, period1Start);
        Mono<KpiSnapshot> p2 = kpiPort.findByEntityAndPeriod(
                fleetId, KpiSnapshot.EntityType.FLEET, periodType, period2Start);

        return Mono.zip(p1.defaultIfEmpty(emptySnapshot(fleetId, periodType, period1Start)),
                        p2.defaultIfEmpty(emptySnapshot(fleetId, periodType, period2Start)))
                .map(tuple -> {
                    KpiSnapshot s1 = tuple.getT1();
                    KpiSnapshot s2 = tuple.getT2();
                    return new KpiComparisonDto(
                            s1, s2,
                            BigDecimalDelta.of(s1.totalKm(), s2.totalKm()),
                            BigDecimalDelta.of(s1.costPerKm(), s2.costPerKm()),
                            BigDecimalDelta.of(s1.totalFuelCost(), s2.totalFuelCost()),
                            BigDecimalDelta.of(s1.incidentRate(), s2.incidentRate()),
                            BigDecimalDelta.of(s1.availabilityRate(), s2.availabilityRate())
                    );
                });
    }

    // ── Calcul ────────────────────────────────────────────────────────────────

    @Override
    public Mono<KpiSnapshot> recalculateFleetKpi(UUID fleetId,
                                                  KpiSnapshot.PeriodType periodType,
                                                  LocalDate periodStart) {
        LocalDate periodEnd = periodEnd(periodType, periodStart);
        log.info("Calcul KPI flotte {} — {} [{} → {}]",
                fleetId, periodType, periodStart, periodEnd);

        // Agrégation réactive de toutes les sources de données
        Mono<BigDecimal> totalKmMono = vehiclePort.getVehiclesByManager(fleetId)
                .flatMap(v -> fuelPort.getTotalQuantityByVehicleId(v.id()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Mono<BigDecimal> fuelCostMono = vehiclePort.getVehiclesByManager(fleetId)
                .flatMap(v -> fuelPort.getTotalCostByVehicleId(v.id()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Mono<BigDecimal> fuelLitersMono = vehiclePort.getVehiclesByManager(fleetId)
                .flatMap(v -> fuelPort.getTotalQuantityByVehicleId(v.id()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Mono<BigDecimal> maintenanceCostMono = vehiclePort.getVehiclesByManager(fleetId)
                .flatMap(v -> maintenancePort.findByVehicleId(v.id())
                        .map(m -> m.getCost() != null ? m.getCost() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Mono<BigDecimal> incidentCostMono = vehiclePort.getVehiclesByManager(fleetId)
                .flatMap(v -> incidentPort.getTotalCostByVehicleId(v.id()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Mono<Long> incidentCountMono = vehiclePort.getVehiclesByManager(fleetId)
                .flatMap(v -> incidentPort.countByVehicleId(v.id()))
                .reduce(0L, Long::sum);

        return Mono.zip(totalKmMono, fuelCostMono, fuelLitersMono,
                        maintenanceCostMono, incidentCostMono, incidentCountMono)
                .flatMap(t -> {
                    BigDecimal km          = t.getT1();
                    BigDecimal fuelCost    = t.getT2();
                    BigDecimal fuelLiters  = t.getT3();
                    BigDecimal maintCost   = t.getT4();
                    BigDecimal incCost     = t.getT5();
                    long       incCount    = t.getT6();

                    BigDecimal totalCost = fuelCost.add(maintCost).add(incCost);

                    KpiSnapshot snapshot = new KpiSnapshot(
                            null, fleetId,
                            KpiSnapshot.EntityType.FLEET, fleetId,
                            periodType, periodStart, periodEnd,
                            km, null, null, null,
                            fuelCost, fuelLiters, maintCost, incCost,
                            KpiSnapshot.computeCostPerKm(totalCost, km),
                            KpiSnapshot.computeFuelPer100Km(fuelLiters, km),
                            (int) incCount,
                            KpiSnapshot.computeIncidentRate((int) incCount, km),
                            null, null,
                            LocalDateTime.now()
                    );
                    return kpiPort.save(snapshot);
                })
                .doOnSuccess(s -> log.info("KPI flotte {} calculé et sauvegardé", fleetId));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private LocalDate currentPeriodStart(KpiSnapshot.PeriodType type) {
        LocalDate today = LocalDate.now();
        return switch (type) {
            case DAILY   -> today;
            case WEEKLY  -> today.with(java.time.DayOfWeek.MONDAY);
            case MONTHLY -> today.withDayOfMonth(1);
        };
    }

    private LocalDate periodEnd(KpiSnapshot.PeriodType type, LocalDate start) {
        return switch (type) {
            case DAILY   -> start;
            case WEEKLY  -> start.plusDays(6);
            case MONTHLY -> start.withDayOfMonth(start.lengthOfMonth());
        };
    }

    private KpiSnapshot emptySnapshot(UUID entityId,
                                       KpiSnapshot.PeriodType periodType,
                                       LocalDate periodStart) {
        return new KpiSnapshot(
                null, entityId, KpiSnapshot.EntityType.FLEET, entityId,
                periodType, periodStart, periodEnd(periodType, periodStart),
                BigDecimal.ZERO, 0, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                LocalDateTime.now()
        );
    }
}
