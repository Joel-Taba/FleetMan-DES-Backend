package com.yowyob.fleet.infrastructure.adapters.inbound.messaging;

import com.yowyob.fleet.domain.model.KpiSnapshot;
import com.yowyob.fleet.domain.ports.in.KpiUseCase;
import com.yowyob.fleet.domain.ports.out.DriverPersistencePort;
import com.yowyob.fleet.domain.ports.out.FleetRepositoryPort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.VehicleLocalR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Job planifié : Calcul périodique des KPIs (flotte, véhicules, conducteurs).
 */
@Component
@RequiredArgsConstructor
public class KpiCalculationJob {

    private static final Logger log = LoggerFactory.getLogger(KpiCalculationJob.class);

    private final KpiUseCase kpiUseCase;
    private final FleetRepositoryPort fleetPort;
    private final VehicleLocalR2dbcRepository vehicleRepo;
    private final DriverPersistencePort driverPort;

    @Scheduled(cron = "0 0 3 * * *")
    public void calculateDailyKpis() {
        log.info("=== Démarrage calcul KPIs quotidiens ===");
        runForAllFleets(KpiSnapshot.PeriodType.DAILY, LocalDate.now());
    }

    @Scheduled(cron = "0 0 4 * * MON")
    public void calculateWeeklyKpis() {
        log.info("=== Démarrage calcul KPIs hebdomadaires ===");
        runForAllFleets(KpiSnapshot.PeriodType.WEEKLY, LocalDate.now().with(DayOfWeek.MONDAY));
    }

    @Scheduled(cron = "0 0 5 1 * *")
    public void calculateMonthlyKpis() {
        log.info("=== Démarrage calcul KPIs mensuels ===");
        runForAllFleets(KpiSnapshot.PeriodType.MONTHLY, LocalDate.now().withDayOfMonth(1));
    }

    @Scheduled(cron = "0 0 6 1 1 *")
    public void calculateYearlyKpis() {
        log.info("=== Démarrage calcul KPIs annuels ===");
        runForAllFleets(KpiSnapshot.PeriodType.YEARLY, LocalDate.now().withDayOfYear(1));
    }

    private void runForAllFleets(KpiSnapshot.PeriodType periodType, LocalDate periodStart) {
        fleetPort.findAll()
                .flatMap(fleet -> kpiUseCase.recalculateFleetKpi(fleet.id(), periodType, periodStart)
                        .thenMany(vehicleRepo.findByFleetId(fleet.id())
                                .flatMap(vehicle -> kpiUseCase.recalculateVehicleKpi(
                                        vehicle.getId(), periodType, periodStart)
                                        .onErrorResume(e -> {
                                            log.warn("KPI véhicule {} ignoré: {}", vehicle.getId(), e.getMessage());
                                            return reactor.core.publisher.Mono.empty();
                                        })))
                        .thenMany(driverPort.findAllByFleetId(fleet.id())
                                .flatMap(driver -> kpiUseCase.recalculateDriverKpi(
                                        driver.userId(), periodType, periodStart)
                                        .onErrorResume(e -> {
                                            log.warn("KPI conducteur {} ignoré: {}", driver.userId(), e.getMessage());
                                            return reactor.core.publisher.Mono.empty();
                                        })))
                        .doOnError(e -> log.error("Erreur KPI flotte {}: {}", fleet.id(), e.getMessage()))
                        .onErrorResume(e -> reactor.core.publisher.Mono.empty())
                )
                .doOnComplete(() -> log.info("=== Calcul KPIs {} terminé ===", periodType))
                .subscribe();
    }
}
