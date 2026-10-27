package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.model.Incident;
import com.yowyob.fleet.domain.ports.out.AssignmentPersistencePort;
import com.yowyob.fleet.domain.ports.out.IncidentPersistencePort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.TripEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.VehicleLocalEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.TripR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.VehicleLocalR2dbcRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IncidentImpactServiceTest {

    @Mock VehicleLocalR2dbcRepository vehicleRepository;
    @Mock TripR2dbcRepository tripRepository;
    @Mock IncidentPersistencePort incidentPersistence;
    @Mock AssignmentPersistencePort assignmentPort;

    private IncidentImpactService service;
    private UUID vehicleId;
    private VehicleLocalEntity vehicle;

    @BeforeEach
    void setUp() {
        service = new IncidentImpactService(vehicleRepository, tripRepository, incidentPersistence, assignmentPort);
        vehicleId = UUID.randomUUID();
        vehicle = new VehicleLocalEntity();
        vehicle.setId(vehicleId);
        vehicle.setStatus("AVAILABLE");
        when(vehicleRepository.findById(vehicleId)).thenReturn(Mono.just(vehicle));
        when(vehicleRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(incidentPersistence.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(incidentPersistence.findByVehicleId(vehicleId)).thenReturn(Flux.empty());
        when(tripRepository.findInProgressTripsForVehicle(vehicleId)).thenReturn(Flux.empty());
        when(tripRepository.existsInProgressTripForVehicle(vehicleId, null)).thenReturn(Mono.just(false));
        when(tripRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
    }

    private Incident incident(Incident.Severity severity) {
        Incident incident = new Incident(UUID.randomUUID(), Incident.Type.ACCIDENT, "choc", severity,
                LocalDateTime.now(), null, BigDecimal.ZERO, "manager", vehicleId, "LT-892-CE", null, null);
        return incident;
    }

    @Test
    void seriousIncident_immobilizesVehicle_andInterruptsTripInProgress() {
        TripEntity trip = new TripEntity();
        trip.setId(UUID.randomUUID());
        trip.setVehicleId(vehicleId);
        trip.setStatus("DEPARTED");
        vehicle.setStatus("ON_TRIP");
        when(tripRepository.findInProgressTripsForVehicle(vehicleId)).thenReturn(Flux.just(trip));
        Incident incident = incident(Incident.Severity.CRITICAL);

        StepVerifier.create(service.applyImpact(incident))
                .assertNext(saved -> assertTrue(saved.isVehicleImmobilized()))
                .verifyComplete();

        assertEquals("MAINTENANCE", vehicle.getStatus());
        assertNotNull(trip.getInterruptedAt());
        assertEquals(incident.getId(), trip.getInterruptionIncidentId());
        assertTrue(trip.isInterrupted());
    }

    @Test
    void minorIncident_onlyRecorded() {
        Incident incident = incident(Incident.Severity.MEDIUM);

        StepVerifier.create(service.applyImpact(incident))
                .assertNext(saved -> assertFalse(saved.isVehicleImmobilized()))
                .verifyComplete();

        assertEquals("AVAILABLE", vehicle.getStatus());
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void vehicleAlreadyInPlannedMaintenance_isNeverReleasedByTheIncident() {
        vehicle.setStatus("MAINTENANCE");
        Incident incident = incident(Incident.Severity.HIGH);

        StepVerifier.create(service.applyImpact(incident))
                .assertNext(saved -> assertFalse(saved.isVehicleImmobilized()))
                .verifyComplete();
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void resolution_releasesVehicle_andResumesInterruptedTrip() {
        Incident incident = incident(Incident.Severity.CRITICAL);
        incident.setVehicleImmobilized(true);
        incident.updateStatus(Incident.Status.RESOLVED);
        vehicle.setStatus("MAINTENANCE");
        TripEntity trip = new TripEntity();
        trip.setId(UUID.randomUUID());
        trip.setVehicleId(vehicleId);
        trip.setStatus("DEPARTED");
        trip.setInterruptedAt(LocalDateTime.now().minusHours(1));
        when(tripRepository.findInProgressTripsForVehicle(vehicleId)).thenReturn(Flux.just(trip));
        when(tripRepository.existsInProgressTripForVehicle(vehicleId, null)).thenReturn(Mono.just(true));

        StepVerifier.create(service.applyImpact(incident))
                .assertNext(saved -> assertFalse(saved.isVehicleImmobilized()))
                .verifyComplete();

        assertEquals("ON_TRIP", vehicle.getStatus());
        assertNotNull(trip.getResumedAt());
        assertFalse(trip.isInterrupted());
    }

    @Test
    void resolution_keepsVehicleImmobilized_whileAnotherSeriousIncidentIsOpen() {
        Incident other = incident(Incident.Severity.HIGH);
        other.setVehicleImmobilized(true);
        Incident incident = incident(Incident.Severity.CRITICAL);
        incident.setVehicleImmobilized(true);
        incident.updateStatus(Incident.Status.CLOSED);
        vehicle.setStatus("MAINTENANCE");
        when(incidentPersistence.findByVehicleId(vehicleId)).thenReturn(Flux.just(incident, other));

        StepVerifier.create(service.applyImpact(incident))
                .assertNext(saved -> assertFalse(saved.isVehicleImmobilized()))
                .verifyComplete();

        assertEquals("MAINTENANCE", vehicle.getStatus());
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void secondSeriousIncident_sharesExistingImmobilization() {
        Incident first = incident(Incident.Severity.CRITICAL);
        first.setVehicleImmobilized(true);
        vehicle.setStatus("MAINTENANCE");
        when(incidentPersistence.findByVehicleId(vehicleId)).thenReturn(Flux.just(first));
        Incident second = incident(Incident.Severity.HIGH);

        StepVerifier.create(service.applyImpact(second))
                .assertNext(saved -> assertTrue(saved.isVehicleImmobilized()))
                .verifyComplete();
    }
}
