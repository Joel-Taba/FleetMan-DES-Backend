package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.ScoringException;
import com.yowyob.fleet.domain.model.Driver;
import com.yowyob.fleet.domain.model.DriverScore;
import com.yowyob.fleet.domain.ports.in.ComputeDriverScoreUseCase;
import com.yowyob.fleet.domain.ports.out.*;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.AssignmentR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.DriverDocumentR2dbcRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DriverScoringService implements ComputeDriverScoreUseCase {

    private final DriverScorePersistencePort scorePort;
    private final DriverPersistencePort driverPort;
    private final FleetRepositoryPort fleetPort;
    private final IncidentPersistencePort incidentPort;
    private final MaintenancePersistencePort maintenancePort;
    private final AssignmentR2dbcRepository assignmentRepo;
    private final DriverDocumentR2dbcRepository driverDocRepo;

    // ── Lecture ───────────────────────────────────────────────────────────────

    @Override
    public Mono<DriverScore> getLatestScore(UUID driverId, DriverScore.PeriodType periodType) {
        return scorePort.findLatest(driverId, periodType)
                .switchIfEmpty(calculateScore(driverId, periodType, currentPeriodStart(periodType)));
    }

    @Override
    public Mono<DriverScore> getScoreById(UUID scoreId) {
        return scorePort.findById(scoreId)
                .switchIfEmpty(Mono.error(ScoringException.scoreNotFound(scoreId)));
    }

    @Override
    public Flux<DriverScore> getScoreHistory(UUID driverId,
                                              DriverScore.PeriodType periodType,
                                              LocalDate from,
                                              LocalDate to) {
        return scorePort.findHistory(driverId, periodType, from, to);
    }

    @Override
    public Flux<DriverScore> getFleetScores(UUID fleetId, DriverScore.PeriodType periodType) {
        return scorePort.findByFleetAndPeriod(fleetId, periodType, currentPeriodStart(periodType));
    }

    @Override
    public Flux<DriverScore> getTopDrivers(UUID fleetId,
                                            DriverScore.PeriodType periodType,
                                            int limit) {
        if (limit < 1 || limit > 100) {
            return Flux.error(ScoringException.invalidTopLimit());
        }
        return scorePort.findTopByFleet(fleetId, periodType, currentPeriodStart(periodType), limit);
    }

    @Override
    public Flux<DriverScore> getBottomDrivers(UUID fleetId,
                                               DriverScore.PeriodType periodType,
                                               int limit) {
        if (limit < 1 || limit > 100) {
            return Flux.error(ScoringException.invalidTopLimit());
        }
        return scorePort.findBottomByFleet(fleetId, periodType, currentPeriodStart(periodType), limit);
    }

    // ── Calcul ────────────────────────────────────────────────────────────────

    @Override
    public Mono<DriverScore> calculateScore(UUID driverId,
                                             DriverScore.PeriodType periodType,
                                             LocalDate periodStart) {
        LocalDate periodEnd   = periodEnd(periodType, periodStart);
        LocalDateTime dtStart = periodStart.atStartOfDay();
        LocalDateTime dtEnd   = periodEnd.plusDays(1).atStartOfDay();

        log.info("Calcul score chauffeur {} — {} [{} → {}]", driverId, periodType, periodStart, periodEnd);

        return driverPort.findById(driverId)
                .switchIfEmpty(Mono.error(ScoringException.driverNotFound(driverId)))
                .flatMap(driver -> {
                    UUID fleetId = driver.fleetId();
                    if (fleetId == null) {
                        return Mono.error(ScoringException.driverNotFound(driverId));
                    }
                    return fleetPort.findById(fleetId)
                            .switchIfEmpty(Mono.error(ScoringException.fleetNotFound(fleetId)))
                            .flatMap(fleet -> doCompute(driverId, fleet.managerId(), fleetId,
                                    periodType, periodStart, periodEnd, dtStart, dtEnd));
                });
    }

    private Mono<DriverScore> doCompute(UUID driverId,
                                         UUID managerId,
                                         UUID fleetId,
                                         DriverScore.PeriodType periodType,
                                         LocalDate periodStart,
                                         LocalDate periodEnd,
                                         LocalDateTime dtStart,
                                         LocalDateTime dtEnd) {

        Mono<Long> incidentsMono    = incidentPort.countByDriverId(driverId).defaultIfEmpty(0L);
        Mono<Long> maintenancesMono = maintenancePort.countByDriverId(driverId).defaultIfEmpty(0L);

        Mono<List<String>> statusesMono = assignmentRepo.findByDriverId(driverId)
                .filter(a -> {
                    LocalDateTime s = a.getStartDatetime();
                    return s != null && !s.isBefore(dtStart) && s.isBefore(dtEnd);
                })
                .map(a -> a.getStatus())
                .collectList();

        Mono<BigDecimal> complianceMono = driverDocRepo.findByDriverId(driverId)
                .collectList()
                .map(docs -> {
                    if (docs.isEmpty()) return BigDecimal.valueOf(100);
                    long valid = docs.stream()
                            .filter(d -> "VALID".equals(d.getStatus()))
                            .count();
                    return BigDecimal.valueOf(valid)
                            .multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(docs.size()), 1, RoundingMode.HALF_UP);
                });

        return Mono.zip(incidentsMono, maintenancesMono, statusesMono, complianceMono)
                .flatMap(t -> {
                    int incidents    = t.getT1().intValue();
                    int maintenances = t.getT2().intValue();
                    List<String> statuses = t.getT3();
                    BigDecimal compliance = t.getT4();

                    int completed  = (int) statuses.stream().filter("COMPLETED"::equals).count();
                    int noShow     = (int) statuses.stream().filter("NO_SHOW"::equals).count();
                    int totalTrips = completed + noShow;

                    BigDecimal incidentScore    = DriverScore.computeIncidentScore(incidents);
                    BigDecimal fuelScore        = DriverScore.computeFuelScore(null, null);
                    BigDecimal complianceScore  = DriverScore.computeComplianceScore(compliance);
                    BigDecimal punctualityScore = DriverScore.computePunctualityScore(completed, noShow);
                    BigDecimal maintenanceScore = DriverScore.computeMaintenanceScore(maintenances);
                    BigDecimal finalScore       = DriverScore.computeFinalScore(
                            incidentScore, fuelScore, complianceScore,
                            punctualityScore, maintenanceScore);

                    DriverScore score = new DriverScore(
                            null, driverId, fleetId, managerId,
                            periodType, periodStart, periodEnd,
                            incidents, totalTrips,
                            null, null, compliance, maintenances,
                            completed, noShow,
                            incidentScore, fuelScore, complianceScore,
                            punctualityScore, maintenanceScore,
                            finalScore, DriverScore.resolveBadge(finalScore),
                            LocalDateTime.now()
                    );

                    return scorePort.save(score)
                            .doOnSuccess(s -> log.info(
                                    "✅ Score chauffeur {} : {} pts [{}]",
                                    driverId, finalScore, s.getBadge()));
                });
    }

    @Override
    public Flux<DriverScore> calculateFleetScores(UUID fleetId,
                                                   DriverScore.PeriodType periodType,
                                                   LocalDate periodStart) {
        return driverPort.findAllByFleetId(fleetId)
                .flatMap(driver -> calculateScore(driver.userId(), periodType, periodStart)
                        .doOnError(e -> log.error("Erreur score chauffeur {}: {}",
                                driver.userId(), e.getMessage()))
                        .onErrorResume(e -> Mono.empty())
                );
    }

    // ── Résumé flotte ─────────────────────────────────────────────────────────

    @Override
    public Mono<FleetScoreSummaryDto> getFleetScoreSummary(UUID fleetId,
                                                             DriverScore.PeriodType periodType) {
        return scorePort.findByFleetAndPeriod(fleetId, periodType, currentPeriodStart(periodType))
                .collectList()
                .map(scores -> {
                    if (scores.isEmpty()) {
                        return new FleetScoreSummaryDto(fleetId, periodType, 0,
                                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                                0L, 0L, 0L, 0L, 0L);
                    }
                    BigDecimal avg = scores.stream()
                            .map(DriverScore::getFinalScore)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .divide(BigDecimal.valueOf(scores.size()), 1, RoundingMode.HALF_UP);
                    BigDecimal min = scores.stream().map(DriverScore::getFinalScore)
                            .min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
                    BigDecimal max = scores.stream().map(DriverScore::getFinalScore)
                            .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);

                    java.util.Map<DriverScore.ScoreBadge, Long> dist = scores.stream()
                            .collect(Collectors.groupingBy(DriverScore::getBadge, Collectors.counting()));

                    return new FleetScoreSummaryDto(fleetId, periodType, scores.size(), avg, min, max,
                            dist.getOrDefault(DriverScore.ScoreBadge.EXCELLENCE, 0L),
                            dist.getOrDefault(DriverScore.ScoreBadge.GOOD, 0L),
                            dist.getOrDefault(DriverScore.ScoreBadge.SATISFACTORY, 0L),
                            dist.getOrDefault(DriverScore.ScoreBadge.WARNING, 0L),
                            dist.getOrDefault(DriverScore.ScoreBadge.INSUFFICIENT, 0L)
                    );
                });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private LocalDate currentPeriodStart(DriverScore.PeriodType type) {
        LocalDate today = LocalDate.now();
        return switch (type) {
            case WEEKLY  -> today.with(DayOfWeek.MONDAY);
            case MONTHLY -> today.withDayOfMonth(1);
        };
    }

    private LocalDate periodEnd(DriverScore.PeriodType type, LocalDate start) {
        return switch (type) {
            case WEEKLY  -> start.plusDays(6);
            case MONTHLY -> start.withDayOfMonth(start.lengthOfMonth());
        };
    }
}
