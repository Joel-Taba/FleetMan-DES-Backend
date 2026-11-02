package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.TripException;
import com.yowyob.fleet.domain.model.Driver;
import com.yowyob.fleet.domain.ports.in.ManageTripUseCase.CreateTripCommand;
import com.yowyob.fleet.domain.ports.out.DistanceCalculatorPort;
import com.yowyob.fleet.domain.ports.out.DriverPersistencePort;
import com.yowyob.fleet.domain.ports.out.ExternalGeofencePort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.RedisTelemetryAdapter;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.VehicleLocalEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.FuelRechargeR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.OperationalParameterR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.TripDetailR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.TripMissionSubmissionR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.TripR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.VehicleLocalR2dbcRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TripServiceConflictTest {

    @Mock TripR2dbcRepository tripRepository;
    @Mock TripDetailR2dbcRepository detailRepository;
    @Mock TripMissionSubmissionR2dbcRepository submissionRepository;
    @Mock VehicleLocalR2dbcRepository vehicleRepository;
    @Mock DriverPersistencePort driverPersistence;
    @Mock OperationalParameterR2dbcRepository operationalRepo;
    @Mock RedisTelemetryAdapter redisTelemetry;
    @Mock DistanceCalculatorPort distanceCalculator;
    @Mock ExternalGeofencePort geofenceApi;
    @Mock DatabaseClient db;
    @Mock AccessScopeService accessScope;
    @Mock ResourceAvailabilityService availability;
    @Mock FuelRechargeR2dbcRepository fuelRechargeRepository;

    private TripService tripService;
    private UUID vehicleId;
    private UUID driverId;
    private UUID fleetId;

    @BeforeEach
    void setUp() {
        tripService = new TripService(
                tripRepository, detailRepository, submissionRepository, vehicleRepository,
                driverPersistence, operationalRepo, redisTelemetry, distanceCalculator,
                geofenceApi, db, accessScope, availability, fuelRechargeRepository
        );
        vehicleId = UUID.randomUUID();
        driverId = UUID.randomUUID();
        fleetId = UUID.randomUUID();
        when(accessScope.assertTripFleetAccess(any(), any())).thenReturn(Mono.empty());
        when(availability.assertDriverEngageable(any(), any(), any())).thenReturn(Mono.empty());
        when(availability.assertCrewQualified(any(), any(), any(), any())).thenReturn(Mono.empty());
        when(availability.assertFleetOperable(any())).thenReturn(Mono.empty());
        when(driverPersistence.findById(driverId)).thenReturn(Mono.just(sampleDriver()));
    }

    @Test
    void tripException_codesForActiveConflicts() {
        assertEquals("TRP_002", TripException.driverOccupied().getBusinessCode());
        assertEquals(HttpStatus.CONFLICT, TripException.driverOccupied().getStatus());
        assertEquals("TRP_003", TripException.vehicleOccupied().getBusinessCode());
        assertEquals("TRP_013", TripException.vehicleSlotConflict().getBusinessCode());
        assertEquals(HttpStatus.CONFLICT, TripException.vehicleSlotConflict().getStatus());
    }

    @Test
    void createTrip_rejectsVehicleInMaintenance() {
        var vehicle = vehicle("MAINTENANCE");
        when(vehicleRepository.findById(vehicleId)).thenReturn(Mono.just(vehicle));

        StepVerifier.create(tripService.createTrip(command(fleetId, LocalDateTime.now().plusDays(1).withHour(18))))
                .expectErrorSatisfies(err -> assertEquals("TRP_004", ((TripException) err).getBusinessCode()))
                .verify();
    }

    @Test
    void createTrip_rejectsReturnBeforeDeparture() {
        StepVerifier.create(tripService.createTrip(command(fleetId, LocalDateTime.now().minusDays(1))))
                .expectErrorSatisfies(err -> assertEquals("TRP_015", ((TripException) err).getBusinessCode()))
                .verify();
        verifyNoInteractions(vehicleRepository);
    }

    @Test
    void createTrip_rejectsVehicleOfAnotherFleet() {
        when(vehicleRepository.findById(vehicleId)).thenReturn(Mono.just(vehicle("AVAILABLE")));

        StepVerifier.create(tripService.createTrip(command(UUID.randomUUID(), LocalDateTime.now().plusDays(1).withHour(18))))
                .expectErrorSatisfies(err -> assertEquals("TRP_019", ((TripException) err).getBusinessCode()))
                .verify();
    }

    @Test
    void createTrip_rejectsOverlappingSlot_evenIfVehicleCurrentlyAvailable() {
        when(vehicleRepository.findById(vehicleId)).thenReturn(Mono.just(vehicle("AVAILABLE")));
        when(availability.assertTripSlotFree(any(), any(), any(), any(), any()))
                .thenReturn(Mono.error(TripException.vehicleSlotConflict()));

        StepVerifier.create(tripService.createTrip(command(fleetId, LocalDateTime.now().plusDays(1).withHour(18))))
                .expectErrorSatisfies(err -> assertEquals("TRP_013", ((TripException) err).getBusinessCode()))
                .verify();
    }

    @Test
    void createTrip_checksSlotForVehicleCurrentlyOnTrip_insteadOfRefusingIt() {
        // Véhicule en trajet aujourd'hui : il reste planifiable demain si le créneau est libre.
        when(vehicleRepository.findById(vehicleId)).thenReturn(Mono.just(vehicle("ON_TRIP")));
        when(availability.assertTripSlotFree(any(), any(), any(), any(), any()))
                .thenReturn(Mono.error(TripException.driverSlotConflict()));

        StepVerifier.create(tripService.createTrip(command(fleetId, LocalDateTime.now().plusDays(1).withHour(18))))
                .expectErrorSatisfies(err -> assertEquals("TRP_014", ((TripException) err).getBusinessCode()))
                .verify();
        verify(availability).assertTripSlotFree(any(), any(), any(), any(), any());
    }

    private CreateTripCommand command(UUID tripFleetId, LocalDateTime scheduledReturn) {
        return new CreateTripCommand(
                vehicleId, driverId, tripFleetId, UUID.randomUUID(),
                LocalDate.now().plusDays(1), LocalTime.of(8, 0),
                "Yaoundé", null, null, null, null,
                "Livraison", "Douala", null, null,
                scheduledReturn, List.of());
    }

    private VehicleLocalEntity vehicle(String status) {
        var vehicle = new VehicleLocalEntity();
        vehicle.setId(vehicleId);
        vehicle.setFleetId(fleetId);
        vehicle.setStatus(status);
        return vehicle;
    }

    private Driver sampleDriver() {
        return new Driver(driverId, fleetId, "B1234567CM", "ACTIVE", null, null, null);
    }
}
