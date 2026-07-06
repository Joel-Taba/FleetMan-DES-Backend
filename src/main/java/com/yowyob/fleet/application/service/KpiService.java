package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.model.DriverScore;
import com.yowyob.fleet.domain.model.KpiSnapshot;
import com.yowyob.fleet.domain.ports.in.KpiUseCase;
import com.yowyob.fleet.domain.ports.out.DriverPersistencePort;
import com.yowyob.fleet.domain.ports.out.DriverScorePersistencePort;
import com.yowyob.fleet.domain.ports.out.KpiPersistencePort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.FuelRechargeR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.IncidentR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.MaintenanceR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.TripR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.VehicleLocalR2dbcRepository;
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
    private final DriverPersistencePort driverPort;
    private final DriverScorePersistencePort driverScorePort;
    private final TripR2dbcRepository tripRepo;
    private final VehicleLocalR2dbcRepository vehicleRepo;
    private final FuelRechargeR2dbcRepository fuelRepo;
    private final IncidentR2dbcRepository incidentRepo;
    private final MaintenanceR2dbcRepository maintenanceRepo;

    // ── Lecture ───────────────────────────────────────────────────────────────

    @Override
    public Mono<KpiSnapshot> getLatestFleetKpi(UUID fleetId, KpiSnapshot.PeriodType periodType) {
        return kpiPort.findLatest(fleetId, KpiSnapshot.EntityType.FLEET, periodType)
                .switchIfEmpty(recalculateFleetKpi(fleetId, periodType, currentPeriodStart(periodType)));
    }

    @Override
    public Mono<KpiSnapshot> getLatestVehicleKpi(UUID vehicleId, KpiSnapshot.PeriodType periodType) {
        return kpiPort.findLatest(vehicleId, KpiSnapshot.EntityType.VEHICLE, periodType)
                .switchIfEmpty(recalculateVehicleKpi(vehicleId, periodType, currentPeriodStart(periodType)));
    }

    @Override
    public Mono<KpiSnapshot> getLatestDriverKpi(UUID driverId, KpiSnapshot.PeriodType periodType) {
        return kpiPort.findLatest(driverId, KpiSnapshot.EntityType.DRIVER, periodType)
                .switchIfEmpty(recalculateDriverKpi(driverId, periodType, currentPeriodStart(periodType)));
    }

    @Override
    public Flux<KpiSnapshot> getFleetKpiHistory(UUID fleetId,
                                                  KpiSnapshot.PeriodType periodType,
                                                  LocalDate from,
                                                  LocalDate to) {
        return kpiPort.findHistory(fleetId, KpiSnapshot.EntityType.FLEET, periodType, from, to);
    }

    @Override
    public Flux<KpiSnapshot> getVehicleKpiHistory(UUID vehicleId,
                                                    KpiSnapshot.PeriodType periodType,
                                                    LocalDate from,
                                                    LocalDate to) {
        return kpiPort.findHistory(vehicleId, KpiSnapshot.EntityType.VEHICLE, periodType, from, to);
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
                .map(tuple -> new KpiComparisonDto(
                        tuple.getT1(), tuple.getT2(),
                        BigDecimalDelta.of(tuple.getT1().totalKm(), tuple.getT2().totalKm()),
                        BigDecimalDelta.of(tuple.getT1().costPerKm(), tuple.getT2().costPerKm()),
                        BigDecimalDelta.of(tuple.getT1().totalFuelCost(), tuple.getT2().totalFuelCost()),
                        BigDecimalDelta.of(tuple.getT1().incidentRate(), tuple.getT2().incidentRate()),
                        BigDecimalDelta.of(tuple.getT1().availabilityRate(), tuple.getT2().availabilityRate())
                ));
    }

    // ── Calcul ────────────────────────────────────────────────────────────────

    @Override
    public Mono<KpiSnapshot> recalculateFleetKpi(UUID fleetId,
                                                  KpiSnapshot.PeriodType periodType,
                                                  LocalDate periodStart) {
        LocalDate periodEnd = periodEnd(periodType, periodStart);
        log.info("Calcul KPI flotte {} — {} [{} → {}]", fleetId, periodType, periodStart, periodEnd);

        LocalDateTime startDt = periodStart.atStartOfDay();
        LocalDateTime endDt = periodEnd.plusDays(1).atStartOfDay();

        Mono<BigDecimal> kmMono = tripRepo.sumDistanceByFleetAndPeriod(fleetId, periodStart, periodEnd);
        Mono<Long> tripsMono = tripRepo.countTripsByFleetAndPeriod(fleetId, periodStart, periodEnd);
        Mono<BigDecimal> fuelCostMono = fuelRepo.sumCostByFleetAndPeriod(fleetId, startDt, endDt);
        Mono<BigDecimal> fuelLitersMono = fuelRepo.sumQuantityByFleetAndPeriod(fleetId, startDt, endDt);
        Mono<BigDecimal> maintCostMono = maintenanceRepo.sumCostByFleetAndPeriod(fleetId, startDt, endDt);
        Mono<BigDecimal> incCostMono = incidentRepo.sumCostByFleetAndPeriod(fleetId, startDt, endDt);
        Mono<Long> incCountMono = incidentRepo.countByFleetAndPeriod(fleetId, startDt, endDt);
        Mono<BigDecimal> availabilityMono = computeFleetAvailability(fleetId);

        return Mono.zip(kmMono, tripsMono, fuelCostMono, fuelLitersMono,
                        maintCostMono, incCostMono, incCountMono, availabilityMono)
                .flatMap(t -> saveSnapshot(
                        fleetId, fleetId, KpiSnapshot.EntityType.FLEET,
                        periodType, periodStart, periodEnd,
                        t.getT1(), t.getT2().intValue(), null, t.getT8(),
                        t.getT3(), t.getT4(), t.getT5(), t.getT6(),
                        t.getT7().intValue(), null, null
                ))
                .doOnSuccess(s -> log.info("KPI flotte {} calculé", fleetId));
    }

    @Override
    public Mono<KpiSnapshot> recalculateVehicleKpi(UUID vehicleId,
                                                    KpiSnapshot.PeriodType periodType,
                                                    LocalDate periodStart) {
        return vehicleRepo.findById(vehicleId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Véhicule introuvable: " + vehicleId)))
                .flatMap(vehicle -> {
                    UUID fleetId = vehicle.getFleetId();
                    LocalDate periodEnd = periodEnd(periodType, periodStart);
                    LocalDateTime startDt = periodStart.atStartOfDay();
                    LocalDateTime endDt = periodEnd.plusDays(1).atStartOfDay();

                    Mono<BigDecimal> kmMono = tripRepo.sumDistanceByVehicleAndPeriod(vehicleId, periodStart, periodEnd);
                    Mono<Long> tripsMono = tripRepo.countTripsByVehicleAndPeriod(vehicleId, periodStart, periodEnd);
                    Mono<BigDecimal> fuelCostMono = fuelRepo.sumCostByVehicleAndPeriod(vehicleId, startDt, endDt);
                    Mono<BigDecimal> fuelLitersMono = fuelRepo.sumQuantityByVehicleAndPeriod(vehicleId, startDt, endDt);
                    Mono<BigDecimal> maintCostMono = maintenanceRepo.sumCostByVehicleAndPeriod(vehicleId, startDt, endDt);
                    Mono<BigDecimal> incCostMono = incidentRepo.sumCostByVehicleAndPeriod(vehicleId, startDt, endDt);
                    Mono<Long> incCountMono = incidentRepo.countByVehicleAndPeriod(vehicleId, startDt, endDt);

                    return Mono.zip(kmMono, tripsMono, fuelCostMono, fuelLitersMono,
                                    maintCostMono, incCostMono, incCountMono)
                            .flatMap(t -> saveSnapshot(
                                    fleetId, vehicleId, KpiSnapshot.EntityType.VEHICLE,
                                    periodType, periodStart, periodEnd,
                                    t.getT1(), t.getT2().intValue(), null, null,
                                    t.getT3(), t.getT4(), t.getT5(), t.getT6(),
                                    t.getT7().intValue(), null, null
                            ));
                });
    }

    @Override
    public Mono<KpiSnapshot> recalculateDriverKpi(UUID driverId,
                                                   KpiSnapshot.PeriodType periodType,
                                                   LocalDate periodStart) {
        return driverPort.findById(driverId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Conducteur introuvable: " + driverId)))
                .flatMap(driver -> {
                    UUID fleetId = driver.fleetId();
                    LocalDate periodEnd = periodEnd(periodType, periodStart);
                    LocalDateTime startDt = periodStart.atStartOfDay();
                    LocalDateTime endDt = periodEnd.plusDays(1).atStartOfDay();

                    Mono<BigDecimal> kmMono = tripRepo.sumDistanceByDriverAndPeriod(driverId, periodStart, periodEnd);
                    Mono<Long> tripsMono = tripRepo.countTripsByDriverAndPeriod(driverId, periodStart, periodEnd);
                    Mono<BigDecimal> incCostMono = incidentRepo.sumCostByDriverAndPeriod(driverId, startDt, endDt);
                    Mono<Long> incCountMono = incidentRepo.countByDriverAndPeriod(driverId, startDt, endDt);
                    Mono<BigDecimal> scoreMono = resolveDriverScore(driverId, periodType, periodStart);

                    return Mono.zip(kmMono, tripsMono, incCostMono, incCountMono, scoreMono)
                            .flatMap(t -> saveSnapshot(
                                    fleetId, driverId, KpiSnapshot.EntityType.DRIVER,
                                    periodType, periodStart, periodEnd,
                                    t.getT1(), t.getT2().intValue(), null, null,
                                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, t.getT3(),
                                    t.getT4().intValue(), t.getT5(), null
                            ));
                });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Mono<KpiSnapshot> saveSnapshot(UUID fleetId,
                                            UUID entityId,
                                            KpiSnapshot.EntityType entityType,
                                            KpiSnapshot.PeriodType periodType,
                                            LocalDate periodStart,
                                            LocalDate periodEnd,
                                            BigDecimal km,
                                            int tripCount,
                                            BigDecimal drivingHours,
                                            BigDecimal availabilityRate,
                                            BigDecimal fuelCost,
                                            BigDecimal fuelLiters,
                                            BigDecimal maintCost,
                                            BigDecimal incCost,
                                            int incCount,
                                            BigDecimal avgDriverScore,
                                            BigDecimal docComplianceRate) {
        BigDecimal totalCost = safe(fuelCost).add(safe(maintCost)).add(safe(incCost));
        KpiSnapshot snapshot = new KpiSnapshot(
                null, fleetId, entityType, entityId,
                periodType, periodStart, periodEnd,
                km, tripCount, drivingHours, availabilityRate,
                fuelCost, fuelLiters, maintCost, incCost,
                KpiSnapshot.computeCostPerKm(totalCost, km),
                KpiSnapshot.computeFuelPer100Km(fuelLiters, km),
                incCount,
                KpiSnapshot.computeIncidentRate(incCount, km),
                avgDriverScore, docComplianceRate,
                LocalDateTime.now()
        );
        return kpiPort.save(snapshot);
    }

    private Mono<BigDecimal> computeFleetAvailability(UUID fleetId) {
        Mono<Long> total = vehicleRepo.countByFleetId(fleetId);
        Mono<Long> available = vehicleRepo.countByFleetIdAndStatus(fleetId, "AVAILABLE");
        return Mono.zip(total, available).map(t -> {
            if (t.getT1() == 0) return BigDecimal.valueOf(100);
            return BigDecimal.valueOf(t.getT2())
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(t.getT1()), 2, RoundingMode.HALF_UP);
        });
    }

    private Mono<BigDecimal> resolveDriverScore(UUID driverId,
                                                 KpiSnapshot.PeriodType periodType,
                                                 LocalDate periodStart) {
        DriverScore.PeriodType scorePeriod = switch (periodType) {
            case WEEKLY -> DriverScore.PeriodType.WEEKLY;
            case MONTHLY, YEARLY -> DriverScore.PeriodType.MONTHLY;
            case DAILY -> DriverScore.PeriodType.WEEKLY;
        };
        return driverScorePort.findByDriverAndPeriod(driverId, scorePeriod, periodStart)
                .map(DriverScore::getFinalScore)
                .defaultIfEmpty(BigDecimal.ZERO);
    }

    private BigDecimal safe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private LocalDate currentPeriodStart(KpiSnapshot.PeriodType type) {
        LocalDate today = LocalDate.now();
        return switch (type) {
            case DAILY   -> today;
            case WEEKLY  -> today.with(java.time.DayOfWeek.MONDAY);
            case MONTHLY -> today.withDayOfMonth(1);
            case YEARLY  -> today.withDayOfYear(1);
        };
    }

    private LocalDate periodEnd(KpiSnapshot.PeriodType type, LocalDate start) {
        return switch (type) {
            case DAILY   -> start;
            case WEEKLY  -> start.plusDays(6);
            case MONTHLY -> start.withDayOfMonth(start.lengthOfMonth());
            case YEARLY  -> start.withDayOfYear(start.lengthOfYear());
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
