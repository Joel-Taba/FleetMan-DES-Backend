package com.yowyob.fleet.application.service;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.DriverEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.TripEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.AssignmentR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.DriverR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.TripR2dbcRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Règle « le chauffeur conduit ce véhicule » : habituel, trajet en cours ou poste du jour. */
@ExtendWith(MockitoExtension.class)
class DriverOperatesVehicleTest {

    @Mock AccessScopeService accessScope;
    @Mock DriverR2dbcRepository driverRepo;
    @Mock TripR2dbcRepository tripRepo;
    @Mock AssignmentR2dbcRepository assignmentRepo;

    @InjectMocks ResourceAccessService service;

    private final UUID driverId = UUID.randomUUID();
    private final UUID regularVehicle = UUID.randomUUID();
    private final UUID otherVehicle = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(accessScope.resolveLocalUserId(driverId)).thenReturn(Mono.just(driverId));
        DriverEntity d = new DriverEntity();
        d.setUserId(driverId);
        d.setAssignedVehicleId(regularVehicle);
        when(driverRepo.findById(driverId)).thenReturn(Mono.just(d));
    }

    @Test
    void vehiculeHabituel_autorise() {
        StepVerifier.create(service.assertDriverOperatesVehicle(driverId, regularVehicle)).verifyComplete();
        verify(assignmentRepo, never()).existsOpenShiftForDriverOnVehicle(any(), any(), any(), any());
    }

    @Test
    void vehiculeDuTrajetEnCours_autorise() {
        TripEntity t = new TripEntity();
        t.setVehicleId(otherVehicle);
        when(tripRepo.findInProgressTripForDriver(driverId)).thenReturn(Mono.just(t));
        StepVerifier.create(service.assertDriverOperatesVehicle(driverId, otherVehicle)).verifyComplete();
    }

    @Test
    void vehiculeDUnPosteDuJour_autorise_memeAvecUnTrajetSurUnAutreVehicule() {
        TripEntity t = new TripEntity();
        t.setVehicleId(UUID.randomUUID());
        when(tripRepo.findInProgressTripForDriver(driverId)).thenReturn(Mono.just(t));
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        when(assignmentRepo.existsOpenShiftForDriverOnVehicle(driverId, otherVehicle, dayStart, dayStart.plusDays(1)))
                .thenReturn(Mono.just(true));
        StepVerifier.create(service.assertDriverOperatesVehicle(driverId, otherVehicle)).verifyComplete();
    }

    @Test
    void vehiculeNonConduit_refuse() {
        when(tripRepo.findInProgressTripForDriver(driverId)).thenReturn(Mono.empty());
        when(assignmentRepo.existsOpenShiftForDriverOnVehicle(eq(driverId), eq(otherVehicle), any(), any()))
                .thenReturn(Mono.just(false));
        StepVerifier.create(service.assertDriverOperatesVehicle(driverId, otherVehicle))
                .expectError(AccessDeniedException.class)
                .verify();
    }
}
