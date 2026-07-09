package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.TripException;
import com.yowyob.fleet.domain.model.Driver;
import com.yowyob.fleet.domain.ports.in.ManageTripUseCase.CreateTripCommand;
import com.yowyob.fleet.domain.ports.out.DistanceCalculatorPort;
import com.yowyob.fleet.domain.ports.out.DriverPersistencePort;
import com.yowyob.fleet.domain.ports.out.ExternalGeofencePort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.RedisTelemetryAdapter;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.VehicleLocalEntity;
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
import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
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

    private TripService tripService;
    private UUID vehicleId;
    private UUID driverId;
    private CreateTripCommand command;

    @BeforeEach
    void setUp() {
        tripService = new TripService(
                tripRepository,
                detailRepository,
                submissionRepository,
                vehicleRepository,
                driverPersistence,
                operationalRepo,
                redisTelemetry,
                distanceCalculator,
                geofenceApi,
                db
        );
        vehicleId = UUID.randomUUID();
        driverId = UUID.randomUUID();
        command = new CreateTripCommand(
                vehicleId,
                driverId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.now(),
                LocalTime.of(8, 0),
                "Yaoundé",
                null,
                null,
                null,
                null,
                "Livraison",
                null,
                "XAF",
                null,
                null,
                List.of()
        );
    }

    @Test
    void tripException_codesForActiveConflicts() {
        assertEquals("TRP_002", TripException.driverOccupied().getBusinessCode());
        assertEquals(HttpStatus.CONFLICT, TripException.driverOccupied().getStatus());
        assertEquals("TRP_003", TripException.vehicleOccupied().getBusinessCode());
        assertEquals(HttpStatus.CONFLICT, TripException.vehicleOccupied().getStatus());
    }

    @Test
    void createTrip_rejectsUnavailableVehicle() {
        var vehicle = availableVehicle(vehicleId);
        vehicle.setStatus("MAINTENANCE");
        when(vehicleRepository.findById(vehicleId)).thenReturn(Mono.just(vehicle));
        when(driverPersistence.findById(driverId)).thenReturn(Mono.just(sampleDriver()));

        StepVerifier.create(tripService.createTrip(command))
                .expectErrorSatisfies(err -> {
                    assert err instanceof TripException;
                    assert "TRP_003".equals(((TripException) err).getBusinessCode());
                })
                .verify();
    }

    private static VehicleLocalEntity availableVehicle(UUID id) {
        var vehicle = new VehicleLocalEntity();
        vehicle.setId(id);
        vehicle.setStatus("AVAILABLE");
        return vehicle;
    }

    private Driver sampleDriver() {
        return new Driver(driverId, UUID.randomUUID(), "B1234567CM", "ACTIVE", null, null, null);
    }
}
