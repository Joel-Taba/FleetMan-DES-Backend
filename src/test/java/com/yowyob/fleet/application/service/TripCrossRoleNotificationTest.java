package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.TripException;
import com.yowyob.fleet.domain.ports.in.ManageTripUseCase.TripDetailInput;
import com.yowyob.fleet.domain.ports.out.DistanceCalculatorPort;
import com.yowyob.fleet.domain.ports.out.DriverPersistencePort;
import com.yowyob.fleet.domain.ports.out.ExternalGeofencePort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.TripEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.TripMissionSubmissionEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.TripStopEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.FuelRechargeR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.OperationalParameterR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.TripDetailR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.TripMissionSubmissionR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.TripR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.TripStopR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.VehicleLocalR2dbcRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Règles croisées gestionnaire ↔ chauffeur des trajets : chaque action déclenche la bonne
 * notification vers l'autre rôle (et seulement elle).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TripCrossRoleNotificationTest {

    @Mock TripR2dbcRepository tripRepository;
    @Mock TripDetailR2dbcRepository detailRepository;
    @Mock TripMissionSubmissionR2dbcRepository submissionRepository;
    @Mock VehicleLocalR2dbcRepository vehicleRepository;
    @Mock DriverPersistencePort driverPersistence;
    @Mock OperationalParameterR2dbcRepository operationalRepo;
    @Mock DistanceCalculatorPort distanceCalculator;
    @Mock ExternalGeofencePort geofenceApi;
    @Mock DatabaseClient db;
    @Mock AccessScopeService accessScope;
    @Mock ResourceAvailabilityService availability;
    @Mock FuelRechargeR2dbcRepository fuelRechargeRepository;
    @Mock TripStopService stopService;
    @Mock TripStopR2dbcRepository stopRepository;
    @Mock TripNotifier notifier;

    private TripService tripService;
    private TripStopService tripStopService;
    private TripEntity trip;
    private final UUID manager = UUID.randomUUID();
    private final UUID driver = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        tripService = new TripService(tripRepository, detailRepository, submissionRepository, vehicleRepository,
                driverPersistence, operationalRepo, distanceCalculator, geofenceApi, db, accessScope,
                availability, fuelRechargeRepository, stopService, notifier);
        tripStopService = new TripStopService(stopRepository, detailRepository, tripRepository, accessScope, db, notifier);

        trip = new TripEntity();
        trip.setId(UUID.randomUUID());
        trip.setTripCode("TRP-2026-0042");
        trip.setDriverId(driver);
        trip.setVehicleId(UUID.randomUUID());
        trip.setFleetId(UUID.randomUUID());
        trip.setStatus("SCHEDULED");
        trip.setStartDate(LocalDate.of(2026, 11, 5));
        trip.setStartTime(LocalTime.of(8, 0));

        when(tripRepository.findById(trip.getId())).thenReturn(Mono.just(trip));
        when(tripRepository.save(any(TripEntity.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(accessScope.assertTripFleetAccess(any(), any())).thenReturn(Mono.empty());
        when(accessScope.resolveLocalUserId(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(detailRepository.findAllByTripIdOrderBySortOrder(any())).thenReturn(Flux.empty());
        when(stopService.stopsOf(any())).thenReturn(Mono.just(List.of()));
        when(notifier.cancelled(any(), any(), any())).thenReturn(Mono.empty());
        when(notifier.updated(any(), any(), any())).thenReturn(Mono.empty());
        when(notifier.assigned(any(), any())).thenReturn(Mono.empty());
        when(notifier.unassigned(any(), any(), any())).thenReturn(Mono.empty());
        when(notifier.vehicleChange(any())).thenReturn(Mono.just("véhicule changé (CE-456-AB)"));
        when(notifier.submissionReviewed(any(), any(), any(Boolean.class), any())).thenReturn(Mono.empty());
        when(notifier.stopFailed(any(), any(), any())).thenReturn(Mono.empty());
        when(notifier.lateArrival(any(), any(), anyLong(), any())).thenReturn(Mono.empty());
        when(notifier.tourDone(any(), anyLong(), any(Integer.class), any())).thenReturn(Mono.empty());
    }

    @Test
    void cancelTrip_notifiesTheDriverWithTheReason() {
        StepVerifier.create(tripService.cancelTrip(trip.getId(), "Client fermé", manager))
                .assertNext(t -> assertEquals("CANCELLED", t.getStatus().name()))
                .verifyComplete();
        verify(notifier).cancelled(trip, manager, "Client fermé");
    }

    @Test
    void rejectSubmission_tellsTheDriverWhoSubmittedIt() {
        TripMissionSubmissionEntity sub = new TripMissionSubmissionEntity();
        sub.setId(UUID.randomUUID());
        sub.setTripId(trip.getId());
        sub.setSubmittedBy(driver);
        sub.setStatus("PENDING");
        when(submissionRepository.findByIdAndStatus(sub.getId(), "PENDING")).thenReturn(Mono.just(sub));
        when(submissionRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(tripService.rejectMissionSubmission(sub.getId(), "Non prévu", manager)).verifyComplete();
        assertEquals("REJECTED", sub.getStatus());
        verify(notifier).submissionReviewed(trip, sub, false, manager);
    }

    @Test
    void driverChange_withdrawsFromFormerDriver_andAssignsTheNewOne() {
        TripService.Snapshot before = TripService.Snapshot.of(trip);
        UUID newDriver = UUID.randomUUID();
        trip.setDriverId(newDriver);

        StepVerifier.create(tripService.notifyChanges(before, trip, manager)).verifyComplete();
        verify(notifier).unassigned(trip, driver, manager);
        verify(notifier).assigned(trip, manager);
        verify(notifier, never()).updated(any(), any(), any());
    }

    @Test
    void scheduleAndVehicleChange_areSummarisedToTheDriver() {
        TripService.Snapshot before = TripService.Snapshot.of(trip);
        trip.setStartTime(LocalTime.of(9, 30));
        trip.setVehicleId(UUID.randomUUID());

        StepVerifier.create(tripService.notifyChanges(before, trip, manager)).verifyComplete();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> changes = ArgumentCaptor.forClass(List.class);
        verify(notifier).updated(eq(trip), eq(manager), changes.capture());
        assertEquals(List.of("véhicule changé (CE-456-AB)", "départ déplacé au 05/11/2026 à 09:30"), changes.getValue());
        verify(notifier, never()).assigned(any(), any());
    }

    @Test
    void unchangedTrip_sendsNoSummary() {
        TripService.Snapshot before = TripService.Snapshot.of(trip);
        StepVerifier.create(tripService.notifyChanges(before, trip, manager)).verifyComplete();
        verify(notifier).updated(trip, manager, List.of());
    }

    @Test
    void failedStop_warnsTheManager() {
        trip.setStatus("DEPARTED");
        TripStopEntity stop = stop("PENDING", null);
        TripStopEntity other = stop("PENDING", null);
        when(stopRepository.findByIdAndTripId(stop.getId(), trip.getId())).thenReturn(Mono.just(stop));
        when(stopRepository.save(any(TripStopEntity.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(stopRepository.findAllByTripIdOrderBySequence(trip.getId())).thenReturn(Flux.just(stop, other));

        StepVerifier.create(tripStopService.fail(trip.getId(), stop.getId(), driver, "ABSENT", "Portail fermé"))
                .assertNext(s -> assertEquals("FAILED", s.status()))
                .verifyComplete();
        verify(notifier).stopFailed(eq(trip), eq(stop), eq(driver));
        // Un arrêt reste ouvert : la tournée n'est pas terminée.
        verify(notifier, never()).tourDone(any(), anyLong(), any(Integer.class), any());
    }

    @Test
    void lateArrival_isReported_butAnOnTimeOneIsNot() {
        trip.setStatus("DEPARTED");
        LocalDateTime now = LocalDateTime.now().withNano(0);
        trip.setStartDate(now.minusHours(3).toLocalDate());
        trip.setStartTime(now.minusHours(3).toLocalTime());
        TripStopEntity late = stop("PENDING", now.minusMinutes(90));
        TripStopEntity onTime = stop("PENDING", now.minusMinutes(5));
        when(stopRepository.findByIdAndTripId(late.getId(), trip.getId())).thenReturn(Mono.just(late));
        when(stopRepository.findByIdAndTripId(onTime.getId(), trip.getId())).thenReturn(Mono.just(onTime));
        when(stopRepository.save(any(TripStopEntity.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(tripStopService.arrive(trip.getId(), onTime.getId(), driver, now))
                .assertNext(s -> assertEquals("ARRIVED", s.status()))
                .verifyComplete();
        verify(notifier, never()).lateArrival(any(), any(), anyLong(), any());

        StepVerifier.create(tripStopService.arrive(trip.getId(), late.getId(), driver, now))
                .assertNext(s -> assertEquals("LATE", s.punctuality()))
                .verifyComplete();
        verify(notifier).lateArrival(eq(trip), eq(late), eq(90L), eq(driver));
    }

    @Test
    void manifestLines_areValidated() {
        assertThrows(TripException.class, () -> TripStopService.validateLine(new TripDetailInput("BOXES", "x", 1, null, null, null)));
        assertThrows(TripException.class, () -> TripStopService.validateLine(new TripDetailInput("CARGO", "x", 0, null, null, null)));
        TripException negative = assertThrows(TripException.class,
                () -> TripStopService.validateLine(new TripDetailInput("CARGO", "x", 2, null, -1, null)));
        assertEquals("TRP_037", negative.getBusinessCode());
        TripStopService.validateLine(new TripDetailInput("CARGO", "Ciment", 3, null, 3, null));
    }

    @Test
    void manifestEdit_afterDeparture_isRefused() {
        trip.setStatus("DEPARTED");
        StepVerifier.create(tripStopService.deleteLine(trip.getId(), UUID.randomUUID(), manager))
                .expectErrorSatisfies(e -> assertTrue(e instanceof TripException
                        && "TRP_036".equals(((TripException) e).getBusinessCode())))
                .verify();
        verify(notifier, never()).updated(any(), any(), any());
    }

    private TripStopEntity stop(String status, LocalDateTime planned) {
        TripStopEntity stop = new TripStopEntity();
        stop.setId(UUID.randomUUID());
        stop.setTripId(trip.getId());
        stop.setSequence(1);
        stop.setStopType("DELIVERY");
        stop.setLocation("Marché Mokolo");
        stop.setStatus(status);
        stop.setPlannedArrival(planned);
        return stop;
    }
}
