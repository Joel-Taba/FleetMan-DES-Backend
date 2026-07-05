package com.yowyob.fleet.infrastructure.adapters.inbound.messaging;

import com.yowyob.fleet.domain.model.DriverScore;
import com.yowyob.fleet.domain.ports.in.ComputeDriverScoreUseCase;
import com.yowyob.fleet.domain.ports.out.FleetRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Job planifié : Calcul périodique des scores de conduite.
 *
 * - Hebdomadaire : chaque lundi à 5h00 (calcul de la semaine écoulée)
 * - Mensuel      : le 1er de chaque mois à 5h30 (calcul du mois écoulé)
 *
 * Pour chaque flotte active, calcule et persiste le score de chaque chauffeur.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ScoringCalculationJob {

    private final ComputeDriverScoreUseCase scoringUseCase;
    private final FleetRepositoryPort fleetPort;

    /** Calcul hebdomadaire — chaque lundi à 5h00 */
    @Scheduled(cron = "0 0 5 * * MON")
    public void calculateWeeklyScores() {
        log.info("=== Démarrage calcul scores hebdomadaires ===");
        // Semaine précédente (lundi dernier)
        LocalDate lastMonday = LocalDate.now().minusWeeks(1).with(DayOfWeek.MONDAY);
        runForAllFleets(DriverScore.PeriodType.WEEKLY, lastMonday);
    }

    /** Calcul mensuel — le 1er de chaque mois à 5h30 */
    @Scheduled(cron = "0 30 5 1 * *")
    public void calculateMonthlyScores() {
        log.info("=== Démarrage calcul scores mensuels ===");
        // Mois précédent
        LocalDate firstOfLastMonth = LocalDate.now().minusMonths(1).withDayOfMonth(1);
        runForAllFleets(DriverScore.PeriodType.MONTHLY, firstOfLastMonth);
    }

    private void runForAllFleets(DriverScore.PeriodType periodType, LocalDate periodStart) {
        fleetPort.findAll()
                .flatMap(fleet -> scoringUseCase
                        .calculateFleetScores(fleet.id(), periodType, periodStart)
                        .doOnNext(s -> log.debug("Score {} calculé pour chauffeur {} [{} pts]",
                                periodType, s.getDriverId(), s.getFinalScore()))
                        .doOnError(e -> log.error("Erreur calcul score flotte {}: {}",
                                fleet.id(), e.getMessage()))
                        .onErrorResume(e -> reactor.core.publisher.Flux.empty())
                )
                .doOnComplete(() -> log.info("=== Calcul scores {} terminé ===", periodType))
                .subscribe();
    }
}
