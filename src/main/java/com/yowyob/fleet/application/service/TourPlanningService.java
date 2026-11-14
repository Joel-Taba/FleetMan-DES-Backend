package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.MissionException;
import com.yowyob.fleet.domain.model.Mission;
import com.yowyob.fleet.domain.model.tour.TourComparison;
import com.yowyob.fleet.domain.model.tour.TourPlan;
import com.yowyob.fleet.domain.model.tour.VehicleTour;
import com.yowyob.fleet.domain.ports.in.ManageTourPlanningUseCase;
import com.yowyob.fleet.domain.ports.out.MissionPersistencePort;
import com.yowyob.fleet.domain.ports.out.TourSolverPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Service applicatif pour la planification et l'optimisation des tournées de livraison.
 * Responsabilités G4 §3.7 :
 * - Résolution VRP-TW (2 antennes, 4 véhicules, 20 livraisons).
 * - Comparaison systématique avec l'affectation gloutonne simple.
 * - Validation et exécution : conversion d'un plan validé en Missions réelles dans Fleetman.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TourPlanningService implements ManageTourPlanningUseCase {

    private final TourSolverPort solverPort;
    private final MissionPersistencePort missionPersistence;

    @Override
    public Mono<TourPlan> solveTour(SolveTourCommand command) {
        log.info("Lancement de la résolution de tournée pour le manager {}", command.managerId());
        return solverPort.solveOptimized(command);
    }

    @Override
    public Mono<TourComparison> compareWithBaseline(SolveTourCommand command) {
        log.info("Calcul comparatif : Solveur VRP-TW vs Baseline Gloutonne");
        return Mono.zip(
                solverPort.solveOptimized(command),
                solverPort.solveBaseline(command)
        ).map(tuple -> TourComparison.compare(tuple.getT1(), tuple.getT2()));
    }

    @Override
    @Transactional
    public Flux<Mission> validateAndExecuteTour(TourPlan tourPlan, LocalDate missionDate, UUID tenantId) {
        if (!tourPlan.feasible()) {
            return Flux.error(new MissionException(
                    "Impossible de valider un plan de tournée infaisable : " + tourPlan.infeasibilityReason(),
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "MSN_012"
            ));
        }

        log.info("Validation du plan {} : génération de {} missions", tourPlan.id(), tourPlan.vehicleTours().size());

        return Flux.fromIterable(tourPlan.vehicleTours())
                .flatMap(vehicleTour -> {
                    // Construction d'une mission Fleetman à partir de la tournée du véhicule
                    LocalDateTime start = missionDate.atTime(LocalTime.of(8, 0));
                    LocalDateTime end = start.plusMinutes(vehicleTour.totalDurationMinutes());

                    Mission mission = new Mission(
                            vehicleTour.vehicleId(),
                            vehicleTour.driverId() != null ? vehicleTour.driverId() : UUID.randomUUID(),
                            tourPlan.managerId(),
                            Mission.Type.ROUND_TRIP,
                            start,
                            end,
                            vehicleTour.homeDepotId(),
                            vehicleTour.homeDepotId(),
                            tenantId
                    );

                    // Si des arrêts avec colis existent, lier le premier comme référence
                    if (!vehicleTour.stops().isEmpty()) {
                        mission.linkToDelivery(vehicleTour.stops().get(0).deliveryDemandId(), vehicleTour.stops().get(0).parcelId());
                    }

                    return missionPersistence.save(mission);
                });
    }
}

