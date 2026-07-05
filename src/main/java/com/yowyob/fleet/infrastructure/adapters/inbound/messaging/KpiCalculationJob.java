package com.yowyob.fleet.infrastructure.adapters.inbound.messaging;

import com.yowyob.fleet.domain.model.KpiSnapshot;
import com.yowyob.fleet.domain.ports.in.KpiUseCase;
import com.yowyob.fleet.domain.ports.out.FleetRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Job planifié : Calcul périodique des KPIs.
 *
 * - Quotidien  : chaque nuit à 3h00
 * - Hebdomadaire : chaque lundi à 4h00
 * - Mensuel    : le 1er de chaque mois à 5h00
 *
 * Pour chaque flotte active, calcule et persiste un KpiSnapshot.
 */
@Component
@RequiredArgsConstructor
public class KpiCalculationJob {

    private static final Logger log = LoggerFactory.getLogger(KpiCalculationJob.class);

    private final KpiUseCase kpiUseCase;
    private final FleetRepositoryPort fleetPort;

    /** Calcul quotidien — chaque nuit à 3h00 */
    @Scheduled(cron = "0 0 3 * * *")
    public void calculateDailyKpis() {
        log.info("=== Démarrage calcul KPIs quotidiens ===");
        LocalDate today = LocalDate.now();
        runForAllFleets(KpiSnapshot.PeriodType.DAILY, today);
    }

    /** Calcul hebdomadaire — chaque lundi à 4h00 */
    @Scheduled(cron = "0 0 4 * * MON")
    public void calculateWeeklyKpis() {
        log.info("=== Démarrage calcul KPIs hebdomadaires ===");
        LocalDate monday = LocalDate.now().with(DayOfWeek.MONDAY);
        runForAllFleets(KpiSnapshot.PeriodType.WEEKLY, monday);
    }

    /** Calcul mensuel — le 1er de chaque mois à 5h00 */
    @Scheduled(cron = "0 0 5 1 * *")
    public void calculateMonthlyKpis() {
        log.info("=== Démarrage calcul KPIs mensuels ===");
        LocalDate firstOfMonth = LocalDate.now().withDayOfMonth(1);
        runForAllFleets(KpiSnapshot.PeriodType.MONTHLY, firstOfMonth);
    }

    private void runForAllFleets(KpiSnapshot.PeriodType periodType, LocalDate periodStart) {
        fleetPort.findAll()
                .flatMap(fleet -> kpiUseCase
                        .recalculateFleetKpi(fleet.id(), periodType, periodStart)
                        .doOnSuccess(s -> log.debug("KPI {} calculé pour flotte {}",
                                periodType, fleet.id()))
                        .doOnError(e -> log.error("Erreur KPI flotte {}: {}",
                                fleet.id(), e.getMessage()))
                        .onErrorResume(e -> reactor.core.publisher.Mono.empty())
                )
                .doOnComplete(() -> log.info("=== Calcul KPIs {} terminé ===", periodType))
                .subscribe();
    }
}
