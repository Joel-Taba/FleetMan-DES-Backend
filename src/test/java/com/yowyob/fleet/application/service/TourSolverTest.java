package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.model.tour.*;
import com.yowyob.fleet.domain.ports.in.ManageTourPlanningUseCase.SolveTourCommand;
import com.yowyob.fleet.domain.ports.out.MissionPersistencePort;
import com.yowyob.fleet.infrastructure.adapters.outbound.solver.VrpTwSolverAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class TourSolverTest {

    @Mock
    private MissionPersistencePort missionPersistence;

    private VrpTwSolverAdapter solverAdapter;
    private TourPlanningService planningService;

    private UUID managerId;
    private List<Depot> depots;
    private List<VehicleCapacity> vehicles;
    private List<DeliveryDemand> demands;

    @BeforeEach
    void setUp() {
        solverAdapter = new VrpTwSolverAdapter();
        planningService = new TourPlanningService(solverAdapter, missionPersistence);

        managerId = UUID.randomUUID();

        // 1. Deux antennes / dépôts (Yaoundé Centre et Douala Bassa)
        UUID depot1 = UUID.randomUUID();
        UUID depot2 = UUID.randomUUID();
        depots = List.of(
                new Depot(depot1, "Antenne Yaoundé-Mokolo", 3.8750, 11.5000, 480, 1080),
                new Depot(depot2, "Antenne Yaoundé-Bastos", 3.8900, 11.5150, 480, 1080)
        );

        // 2. Quatre véhicules (2 par antenne)
        vehicles = List.of(
                new VehicleCapacity(UUID.randomUUID(), "CE-101-AA", UUID.randomUUID(), depot1, 1500.0, 12.0, 480, 1080),
                new VehicleCapacity(UUID.randomUUID(), "CE-102-BB", UUID.randomUUID(), depot1, 1500.0, 12.0, 480, 1080),
                new VehicleCapacity(UUID.randomUUID(), "CE-201-CC", UUID.randomUUID(), depot2, 2000.0, 15.0, 480, 1080),
                new VehicleCapacity(UUID.randomUUID(), "CE-202-DD", UUID.randomUUID(), depot2, 2000.0, 15.0, 480, 1080)
        );

        // 3. Vingt livraisons réparties
        demands = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            demands.add(new DeliveryDemand(
                    UUID.randomUUID(),
                    "QR-COLIS-" + i,
                    "Client-" + i,
                    3.8500 + (i * 0.003),
                    11.4900 + (i * 0.003),
                    50.0 + (i * 5),
                    0.4,
                    510 + (i * 15), // Fenêtre de début échelonnée
                    660 + (i * 15), // Fenêtre de fin
                    10             // 10 minutes de service
            ));
        }
    }

    @Test
    @DisplayName("R12 : Résolution du problème 2 antennes, 4 véhicules et 20 livraisons")
    void testSolveTour_Success() {
        SolveTourCommand cmd = new SolveTourCommand(
                managerId,
                LocalDate.now(),
                depots,
                vehicles,
                demands,
                true // retards pénalisés acceptés
        );

        StepVerifier.create(planningService.solveTour(cmd))
                .assertNext(plan -> {
                    assertTrue(plan.feasible(), "Le plan doit être réalisable");
                    assertEquals(20, plan.totalDeliveriesServed(), "Toutes les 20 livraisons doivent être desservies");
                    assertTrue(plan.totalVehiclesUsed() > 0 && plan.totalVehiclesUsed() <= 4, "Doit utiliser au plus 4 véhicules");
                    assertTrue(plan.totalDistanceKm() > 0, "Distance totale strictement positive");
                    assertNull(plan.infeasibilityReason());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("R12 : Comparaison avec la Baseline gloutonne démontrant le gain d'optimisation")
    void testCompareWithBaseline_DemonstratesGain() {
        SolveTourCommand cmd = new SolveTourCommand(
                managerId,
                LocalDate.now(),
                depots,
                vehicles,
                demands,
                true
        );

        StepVerifier.create(planningService.compareWithBaseline(cmd))
                .assertNext(comparison -> {
                    assertNotNull(comparison.optimizedPlan());
                    assertNotNull(comparison.baselinePlan());
                    // Le solveur optimisé doit parcourir une distance inférieure ou égale à la baseline
                    assertTrue(comparison.distanceGainKm() >= 0, "L'optimisation doit économiser des kilomètres");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("R12 : Détection explicite de contrainte infaisable (surcharge de poids)")
    void testSolveTour_InfeasibleDetection() {
        // Véhicule avec capacité minuscule insuffisante
        List<VehicleCapacity> tinyVehicles = List.of(
                new VehicleCapacity(UUID.randomUUID(), "CE-TINY-1", UUID.randomUUID(), depots.get(0).id(), 10.0, 0.1, 480, 1080)
        );

        SolveTourCommand cmd = new SolveTourCommand(
                managerId,
                LocalDate.now(),
                depots,
                tinyVehicles,
                demands,
                false
        );

        StepVerifier.create(planningService.solveTour(cmd))
                .assertNext(plan -> {
                    assertFalse(plan.feasible(), "Le plan doit être marqué infaisable");
                    assertNotNull(plan.infeasibilityReason(), "Une explication claire doit être fournie");
                    assertTrue(plan.infeasibilityReason().contains("Poids total") || plan.infeasibilityReason().contains("insuffisantes"));
                })
                .verifyComplete();
    }
}

