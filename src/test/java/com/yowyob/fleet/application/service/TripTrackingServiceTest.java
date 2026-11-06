package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.TripException;
import com.yowyob.fleet.domain.model.TripTracking;
import com.yowyob.fleet.domain.model.TripTracking.LiveTripPosition;
import com.yowyob.fleet.domain.model.TripTracking.PositionPoint;
import com.yowyob.fleet.domain.ports.out.TripPositionPersistencePort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.RedisTelemetryAdapter;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.TripEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.VehicleLocalEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.OperationalParameterR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.TripR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.VehicleLocalR2dbcRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TripTrackingServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-05T10:00:00Z");

    @Mock TripR2dbcRepository tripRepository;
    @Mock VehicleLocalR2dbcRepository vehicleRepository;
    @Mock OperationalParameterR2dbcRepository operationalRepo;
    @Mock TripPositionPersistencePort positions;
    @Mock RedisTelemetryAdapter redisTelemetry;
    @Mock AccessScopeService accessScope;

    private TripTrackingService service;
    private UUID tripId;
    private UUID driverId;
    private UUID vehicleId;
    private UUID fleetId;
    private TripEntity trip;

    @BeforeEach
    void setUp() {
        service = new TripTrackingService(tripRepository, vehicleRepository, operationalRepo, positions,
                redisTelemetry, accessScope, Clock.fixed(NOW, ZoneOffset.UTC));
        tripId = UUID.randomUUID();
        driverId = UUID.randomUUID();
        vehicleId = UUID.randomUUID();
        fleetId = UUID.randomUUID();
        trip = new TripEntity();
        trip.setId(tripId);
        trip.setTripCode("TRJ-2026-0001");
        trip.setDriverId(driverId);
        trip.setVehicleId(vehicleId);
        trip.setFleetId(fleetId);
        trip.setStatus("DEPARTED");
        when(tripRepository.findById(tripId)).thenReturn(Mono.just(trip));
        when(accessScope.resolveLocalUserId(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(positions.lastPositionAt(tripId)).thenReturn(Mono.empty());
        when(positions.record(any(), any(), any(), any(), any(), any(), anyInt())).thenReturn(Mono.just(true));
        when(redisTelemetry.addPoint(any(), any(), any())).thenReturn(Mono.empty());
        when(operationalRepo.findByVehicleId(any())).thenReturn(Mono.empty());
    }

    private static void assertTripError(Throwable e, HttpStatus status, String code) {
        assertTrue(e instanceof TripException, "TripException attendue, reçu " + e);
        assertEquals(status, ((TripException) e).getStatus());
        assertEquals(code, ((TripException) e).getBusinessCode());
    }

    // ── Envoi d'un point ──────────────────────────────────────────────────────

    @Test
    void driverPointIsRecordedOnHisTripInProgress() {
        StepVerifier.create(service.sendTelemetry(tripId, driverId, 3.848, 11.502, 42.0, 8.0))
            .assertNext(ack -> {
                assertTrue(ack.accepted());
                assertEquals(TripTracking.RECORDED, ack.outcome());
                assertEquals(NOW, ack.positionAt());
            })
            .verifyComplete();
        ArgumentCaptor<PositionPoint> point = ArgumentCaptor.forClass(PositionPoint.class);
        verify(positions).record(eq(tripId), eq(vehicleId), eq(driverId), point.capture(), eq(8.0),
            eq(TripTracking.MIN_INTERVAL), eq(TripTracking.HISTORY_LIMIT));
        assertEquals(3.848, point.getValue().lat());
        assertEquals(42.0, point.getValue().speed());
    }

    @Test
    void arrivedTripStillAcceptsPositions() {
        trip.setStatus("ARRIVED");
        StepVerifier.create(service.sendTelemetry(tripId, driverId, 4.05, 9.7, null, null))
            .assertNext(ack -> assertTrue(ack.accepted()))
            .verifyComplete();
    }

    @Test
    void kernelIdOfTheDriverIsMappedToLocalId() {
        UUID kernelId = UUID.randomUUID();
        when(accessScope.resolveLocalUserId(kernelId)).thenReturn(Mono.just(driverId));
        StepVerifier.create(service.sendTelemetry(tripId, kernelId, 3.8, 11.5, 30.0, null))
            .assertNext(ack -> assertTrue(ack.accepted()))
            .verifyComplete();
    }

    @Test
    void anotherDriverIsRefused() {
        StepVerifier.create(service.sendTelemetry(tripId, UUID.randomUUID(), 3.8, 11.5, 30.0, null))
            .expectErrorSatisfies(e -> assertTripError(e, HttpStatus.FORBIDDEN, "TRP_009"))
            .verify();
        verify(positions, never()).record(any(), any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void invalidCoordinatesAreRejectedBeforeAnyLookup() {
        StepVerifier.create(service.sendTelemetry(tripId, driverId, 91.0, 11.5, null, null))
            .expectErrorSatisfies(e -> assertTripError(e, HttpStatus.BAD_REQUEST, "TRP_038"))
            .verify();
        StepVerifier.create(service.sendTelemetry(tripId, driverId, 3.8, null, null, null))
            .expectErrorSatisfies(e -> assertTripError(e, HttpStatus.BAD_REQUEST, "TRP_038"))
            .verify();
        StepVerifier.create(service.sendTelemetry(tripId, driverId, Double.NaN, 11.5, null, null))
            .expectErrorSatisfies(e -> assertTripError(e, HttpStatus.BAD_REQUEST, "TRP_038"))
            .verify();
        verifyNoInteractions(tripRepository);
    }

    @Test
    void positionIsRefusedOutsideATripInProgress() {
        for (String status : List.of("SCHEDULED", "COMPLETED", "CANCELLED", "MISSED")) {
            trip.setStatus(status);
            StepVerifier.create(service.sendTelemetry(tripId, driverId, 3.8, 11.5, 20.0, null))
                .expectErrorSatisfies(e -> assertTripError(e, HttpStatus.UNPROCESSABLE_ENTITY, "TRP_039"))
                .verify();
        }
        verify(positions, never()).record(any(), any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void unknownTripIsNotFound() {
        UUID other = UUID.randomUUID();
        when(tripRepository.findById(other)).thenReturn(Mono.empty());
        StepVerifier.create(service.sendTelemetry(other, driverId, 3.8, 11.5, 20.0, null))
            .expectErrorSatisfies(e -> assertTripError(e, HttpStatus.NOT_FOUND, "TRP_001"))
            .verify();
    }

    @Test
    void pointTooCloseToThePreviousOneIsIgnored() {
        when(positions.lastPositionAt(tripId)).thenReturn(Mono.just(NOW.minusSeconds(5)));
        StepVerifier.create(service.sendTelemetry(tripId, driverId, 3.8, 11.5, 20.0, null))
            .assertNext(ack -> {
                assertFalse(ack.accepted());
                assertEquals(TripTracking.TOO_SOON, ack.outcome());
                assertEquals(10, ack.retryInSeconds());
            })
            .verifyComplete();
        verify(positions, never()).record(any(), any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void pointAfterTheIntervalIsRecorded() {
        when(positions.lastPositionAt(tripId)).thenReturn(Mono.just(NOW.minusSeconds(30)));
        StepVerifier.create(service.sendTelemetry(tripId, driverId, 3.8, 11.5, 20.0, null))
            .assertNext(ack -> assertTrue(ack.accepted()))
            .verifyComplete();
    }

    @Test
    void concurrentPointLosingTheAtomicGuardIsIgnored() {
        when(positions.record(any(), any(), any(), any(), any(), any(), anyInt())).thenReturn(Mono.just(false));
        StepVerifier.create(service.sendTelemetry(tripId, driverId, 3.8, 11.5, 20.0, null))
            .assertNext(ack -> assertFalse(ack.accepted()))
            .verifyComplete();
    }

    @Test
    void redisFailureDoesNotFailTheTelemetry() {
        when(redisTelemetry.addPoint(any(), any(), any())).thenReturn(Mono.error(new IllegalStateException("redis down")));
        StepVerifier.create(service.sendTelemetry(tripId, driverId, 3.8, 11.5, 20.0, null))
            .assertNext(ack -> assertTrue(ack.accepted()))
            .verifyComplete();

        when(redisTelemetry.addPoint(any(), any(), any())).thenThrow(new IllegalStateException("no connection"));
        when(positions.lastPositionAt(tripId)).thenReturn(Mono.empty());
        StepVerifier.create(service.sendTelemetry(tripId, driverId, 3.8, 11.5, 20.0, null))
            .assertNext(ack -> assertTrue(ack.accepted()))
            .verifyComplete();
    }

    @Test
    void vehicleSpeedFailureDoesNotFailTheTelemetry() {
        when(operationalRepo.findByVehicleId(vehicleId)).thenReturn(Mono.error(new IllegalStateException("db hiccup")));
        StepVerifier.create(service.sendTelemetry(tripId, driverId, 3.8, 11.5, 20.0, null))
            .assertNext(ack -> assertTrue(ack.accepted()))
            .verifyComplete();
    }

    @Test
    void aberrantSpeedIsDroppedButThePointIsKept() {
        StepVerifier.create(service.sendTelemetry(tripId, driverId, 3.8, 11.5, 950.0, -3.0))
            .assertNext(ack -> assertTrue(ack.accepted()))
            .verifyComplete();
        ArgumentCaptor<PositionPoint> point = ArgumentCaptor.forClass(PositionPoint.class);
        verify(positions).record(any(), any(), any(), point.capture(), any(), any(), anyInt());
        assertNull(point.getValue().speed());
    }

    // ── Lecture ───────────────────────────────────────────────────────────────

    @Test
    void managerOfTheFleetReadsLastPositionAndTrack() {
        UUID managerId = UUID.randomUUID();
        when(accessScope.assertTripFleetAccess(managerId, fleetId)).thenReturn(Mono.empty());
        VehicleLocalEntity vehicle = new VehicleLocalEntity();
        vehicle.setId(vehicleId);
        vehicle.setLicensePlate("LT-123-AB");
        when(vehicleRepository.findById(vehicleId)).thenReturn(Mono.just(vehicle));
        PositionPoint a = new PositionPoint(3.84, 11.50, 20.0, NOW.minusSeconds(60));
        PositionPoint b = new PositionPoint(3.85, 11.51, 35.0, NOW.minusSeconds(30));
        when(positions.lastPosition(tripId)).thenReturn(Mono.just(b));
        when(positions.track(tripId, TripTracking.DEFAULT_TRACK)).thenReturn(Flux.just(a, b));

        StepVerifier.create(service.getPositions(tripId, managerId, null))
            .assertNext(view -> {
                assertEquals("LT-123-AB", view.licensePlate());
                assertEquals(b, view.last());
                assertEquals(List.of(a, b), view.track());
                assertEquals("DEPARTED", view.status());
            })
            .verifyComplete();
    }

    @Test
    void noPositionYetGivesNullLastAndEmptyTrack() {
        UUID managerId = UUID.randomUUID();
        when(accessScope.assertTripFleetAccess(managerId, fleetId)).thenReturn(Mono.empty());
        when(vehicleRepository.findById(vehicleId)).thenReturn(Mono.empty());
        when(positions.lastPosition(tripId)).thenReturn(Mono.empty());
        when(positions.track(any(), anyInt())).thenReturn(Flux.empty());

        StepVerifier.create(service.getPositions(tripId, managerId, 50))
            .assertNext(view -> {
                assertNull(view.last());
                assertTrue(view.track().isEmpty());
                assertNull(view.licensePlate());
            })
            .verifyComplete();
        verify(positions).track(tripId, 50);
    }

    @Test
    void managerOfAnotherFleetCannotReadPositions() {
        UUID outsider = UUID.randomUUID();
        when(accessScope.assertTripFleetAccess(outsider, fleetId)).thenReturn(Mono.error(TripException.forbidden()));
        StepVerifier.create(service.getPositions(tripId, outsider, null))
            .expectErrorSatisfies(e -> assertTripError(e, HttpStatus.FORBIDDEN, "TRP_009"))
            .verify();
        verify(positions, never()).track(any(), anyInt());
    }

    @Test
    void livePositionsKeepOnlyFleetsInScopeAndAttachTracks() {
        UUID managerId = UUID.randomUUID();
        UUID foreignFleet = UUID.randomUUID();
        UUID otherTrip = UUID.randomUUID();
        PositionPoint last = new PositionPoint(3.85, 11.51, 35.0, NOW.minusSeconds(30));
        LiveTripPosition mine = new LiveTripPosition(tripId, "TRJ-1", "DEPARTED", fleetId, vehicleId, "LT-1", driverId, last, List.of());
        LiveTripPosition foreign = new LiveTripPosition(otherTrip, "TRJ-2", "DEPARTED", foreignFleet, UUID.randomUUID(), "LT-2",
            UUID.randomUUID(), null, List.of());
        when(positions.liveTrips(managerId, null)).thenReturn(Flux.just(mine, foreign));
        when(accessScope.assertTripFleetAccess(managerId, fleetId)).thenReturn(Mono.empty());
        when(accessScope.assertTripFleetAccess(managerId, foreignFleet)).thenReturn(Mono.error(TripException.forbidden()));
        when(positions.recentTracks(List.of(tripId), TripTracking.LIVE_TRACK)).thenReturn(Mono.just(Map.of(tripId, List.of(last))));

        StepVerifier.create(service.getLivePositions(managerId, null))
            .assertNext(row -> {
                assertEquals(tripId, row.tripId());
                assertEquals(List.of(last), row.track());
            })
            .verifyComplete();
    }

    @Test
    void livePositionsForAForeignFleetAreRefused() {
        UUID managerId = UUID.randomUUID();
        when(accessScope.assertTripFleetAccess(managerId, fleetId)).thenReturn(Mono.error(TripException.forbidden()));
        StepVerifier.create(service.getLivePositions(managerId, fleetId))
            .expectErrorSatisfies(e -> assertTripError(e, HttpStatus.FORBIDDEN, "TRP_009"))
            .verify();
        verify(positions, never()).liveTrips(any(), any());
    }

    // ── Règles ────────────────────────────────────────────────────────────────

    @Test
    void coordinateRules() {
        assertTrue(TripTracking.validCoordinates(-90.0, -180.0));
        assertTrue(TripTracking.validCoordinates(90.0, 180.0));
        assertTrue(TripTracking.validCoordinates(48.85, 2.35));
        assertFalse(TripTracking.validCoordinates(-90.1, 0.0));
        assertFalse(TripTracking.validCoordinates(0.0, 180.5));
        assertFalse(TripTracking.validCoordinates(Double.POSITIVE_INFINITY, 0.0));
        assertTrue(TripTracking.tooSoon(NOW.minusSeconds(14), NOW));
        assertFalse(TripTracking.tooSoon(NOW.minusSeconds(15), NOW));
        assertFalse(TripTracking.tooSoon(null, NOW));
        assertEquals(12.3, TripTracking.sanitizeSpeed(12.34));
        assertNull(TripTracking.sanitizeSpeed(-1.0));
    }
}
