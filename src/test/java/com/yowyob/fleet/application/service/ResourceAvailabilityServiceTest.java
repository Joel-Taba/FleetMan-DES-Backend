package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.PlanningException;
import com.yowyob.fleet.domain.exception.TripException;
import com.yowyob.fleet.domain.model.Assignment;
import com.yowyob.fleet.domain.model.Driver;
import com.yowyob.fleet.domain.ports.out.AssignmentPersistencePort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.TripEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.TripR2dbcRepository;
import com.yowyob.fleet.domain.ports.out.ResourceLockPort;
import com.yowyob.fleet.domain.exception.DriverException;
import com.yowyob.fleet.domain.model.DriverDocument;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.VehicleTypeEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.VehicleLocalEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.VehicleTypeR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.VehicleLocalR2dbcRepository;
import com.yowyob.fleet.domain.ports.out.DocumentPersistencePort;
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

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** Règle commune des créneaux (doc GESTION_TRAJETS §6.5) entre trajets et affectations. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResourceAvailabilityServiceTest {

    @Mock TripR2dbcRepository tripRepository;
    @Mock AssignmentPersistencePort assignmentPort;
    @Mock AccessScopeService accessScope;
    @Mock ResourceLockPort resourceLock;
    @Mock DocumentPersistencePort documentPort;
    @Mock VehicleLocalR2dbcRepository vehicleRepository;
    @Mock VehicleTypeR2dbcRepository vehicleTypeRepository;

    private ResourceAvailabilityService service;
    private final UUID vehicle = UUID.randomUUID();
    private final UUID driver = UUID.randomUUID();
    private final UUID fleet = UUID.randomUUID();
    private final LocalDateTime start = LocalDateTime.of(2026, 11, 2, 8, 0);
    private final LocalDateTime end = LocalDateTime.of(2026, 11, 2, 12, 0);

    @BeforeEach
    void setUp() {
        service = new ResourceAvailabilityService(tripRepository, assignmentPort, accessScope, resourceLock,
                documentPort, vehicleRepository, vehicleTypeRepository);
        when(resourceLock.lock(any(), any())).thenReturn(Mono.empty());
        when(tripRepository.existsOverlappingTripForVehicle(any(), any(), any(), any(), any())).thenReturn(Mono.just(false));
        when(tripRepository.existsOverlappingTripForDriver(any(), any(), any(), any(), any())).thenReturn(Mono.just(false));
        when(tripRepository.findOverlappingTripsForResources(any(), any(), any(), any(), any(), any())).thenReturn(Flux.empty());
        when(assignmentPort.findConflictingByVehicle(any(), any(), any(), any())).thenReturn(Flux.empty());
        when(assignmentPort.findConflictingByDriver(any(), any(), any(), any())).thenReturn(Flux.empty());
    }

    @Test
    void tripSlot_free_whenNothingOverlaps() {
        StepVerifier.create(service.assertTripSlotFree(vehicle, driver, start, end, null)).verifyComplete();
    }

    @Test
    void tripSlot_conflicts_withOverlappingTripOfSameVehicle() {
        when(tripRepository.existsOverlappingTripForVehicle(eq(vehicle), any(), any(), any(), any())).thenReturn(Mono.just(true));
        StepVerifier.create(service.assertTripSlotFree(vehicle, driver, start, end, null))
                .expectErrorSatisfies(e -> assertEquals("TRP_013", ((TripException) e).getBusinessCode()))
                .verify();
    }

    @Test
    void tripSlot_fitsInsideAssignmentOfSamePair() {
        // Le trajet du même binôme s'inscrit dans le cadre de son affectation (§7.1).
        when(assignmentPort.findConflictingByVehicle(eq(vehicle), any(), any(), any()))
                .thenReturn(Flux.just(assignment(vehicle, driver)));
        when(assignmentPort.findConflictingByDriver(eq(driver), any(), any(), any()))
                .thenReturn(Flux.just(assignment(vehicle, driver)));
        StepVerifier.create(service.assertTripSlotFree(vehicle, driver, start, end, null)).verifyComplete();
    }

    @Test
    void tripSlot_conflicts_whenVehicleAssignedToAnotherDriver() {
        when(assignmentPort.findConflictingByVehicle(eq(vehicle), any(), any(), any()))
                .thenReturn(Flux.just(assignment(vehicle, UUID.randomUUID())));
        StepVerifier.create(service.assertTripSlotFree(vehicle, driver, start, end, null))
                .expectErrorSatisfies(e -> assertEquals("TRP_013", ((TripException) e).getBusinessCode()))
                .verify();
    }

    @Test
    void assignmentSlot_conflicts_withTripOfSameVehicleAndOtherDriver() {
        TripEntity trip = new TripEntity();
        trip.setVehicleId(vehicle);
        trip.setDriverId(UUID.randomUUID());
        when(tripRepository.findOverlappingTripsForResources(any(), any(), any(), any(), any(), any()))
                .thenReturn(Flux.just(trip));
        StepVerifier.create(service.assertAssignmentSlotFree(vehicle, driver, start, end, null))
                .expectErrorSatisfies(e -> assertEquals("PLN_006", ((PlanningException) e).getBusinessCode()))
                .verify();
    }

    @Test
    void inactiveOrFleetlessDriver_isNotEngageable() {
        Driver inactive = new Driver(driver, fleet, "L1", "INACTIVE", null, null, null);
        Driver fleetless = new Driver(driver, null, "L1", "ACTIVE", null, null, null);
        StepVerifier.create(service.assertDriverEngageable(UUID.randomUUID(), inactive, TripException::driverInactive))
                .expectError(TripException.class).verify();
        StepVerifier.create(service.assertDriverEngageable(UUID.randomUUID(), fleetless, TripException::driverInactive))
                .expectError(TripException.class).verify();
    }

    private Assignment assignment(UUID vehicleId, UUID driverId) {
        return new Assignment(UUID.randomUUID(), null, fleet, vehicleId, driverId, null,
                start.minusHours(1), end.plusHours(1), Assignment.Status.PENDING,
                null, null, null, null, null, null);
    }

    @Test
    void slotChecks_lockVehicleAndDriverBeforeReading() {
        UUID vehicleId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        when(assignmentPort.findConflictingByVehicle(any(), any(), any(), any())).thenReturn(Flux.empty());
        when(assignmentPort.findConflictingByDriver(any(), any(), any(), any())).thenReturn(Flux.empty());
        LocalDateTime start = LocalDateTime.of(2026, 11, 2, 8, 0);

        StepVerifier.create(service.assertTripSlotFree(vehicleId, driverId, start, start.plusHours(4), null))
                .verifyComplete();
        StepVerifier.create(service.assertAssignmentSlotFree(vehicleId, driverId, start, start.plusHours(4), null))
                .verifyComplete();

        var order = org.mockito.Mockito.inOrder(resourceLock, tripRepository);
        order.verify(resourceLock).lock(vehicleId, driverId);
        order.verify(tripRepository).existsOverlappingTripForVehicle(any(), any(), any(), any(), any());
        org.mockito.Mockito.verify(resourceLock, org.mockito.Mockito.times(2)).lock(vehicleId, driverId);
    }

    // ── Permis de conduire ────────────────────────────────────────────────────

    private void vehicleOfType(UUID vehicleId, String requiredCategory) {
        VehicleLocalEntity vehicle = new VehicleLocalEntity();
        vehicle.setId(vehicleId);
        UUID typeId = UUID.randomUUID();
        vehicle.setVehicleTypeId(typeId);
        VehicleTypeEntity type = new VehicleTypeEntity();
        type.setId(typeId);
        type.setRequiredLicenseCategory(requiredCategory);
        when(vehicleRepository.findById(vehicleId)).thenReturn(Mono.just(vehicle));
        when(vehicleTypeRepository.findById(typeId)).thenReturn(Mono.just(type));
    }

    private DriverDocument license(UUID driverId, String categories, java.time.LocalDate expiry) {
        return new DriverDocument(UUID.randomUUID(), driverId, DriverDocument.DocType.DRIVING_LICENSE,
                "P-1", categories, "MINTRANS", null, expiry, null, null, null, null,
                DriverDocument.Status.VALID, null, LocalDateTime.now(), LocalDateTime.now());
    }

    private void assertLicenseError(UUID driverId, UUID vehicleId, String code) {
        LocalDateTime start = LocalDateTime.of(2026, 11, 2, 8, 0);
        StepVerifier.create(service.assertDriverLicensed(driverId, vehicleId, start, start.plusHours(4)))
                .expectErrorSatisfies(e -> {
                    org.junit.jupiter.api.Assertions.assertInstanceOf(DriverException.class, e);
                    org.junit.jupiter.api.Assertions.assertEquals(code, ((DriverException) e).getBusinessCode());
                })
                .verify();
    }

    @Test
    void license_missing_isRejected() {
        UUID driverId = UUID.randomUUID(), vehicleId = UUID.randomUUID();
        vehicleOfType(vehicleId, "B");
        when(documentPort.findDriverDocsByDriverId(driverId)).thenReturn(Flux.empty());
        assertLicenseError(driverId, vehicleId, "DRV_009");
    }

    @Test
    void license_expiredBeforeSlotEnd_isRejected() {
        UUID driverId = UUID.randomUUID(), vehicleId = UUID.randomUUID();
        vehicleOfType(vehicleId, "B");
        when(documentPort.findDriverDocsByDriverId(driverId))
                .thenReturn(Flux.just(license(driverId, "B", java.time.LocalDate.of(2026, 11, 1))));
        assertLicenseError(driverId, vehicleId, "DRV_010");
    }

    @Test
    void license_withoutRequiredCategory_isRejected() {
        UUID driverId = UUID.randomUUID(), vehicleId = UUID.randomUUID();
        vehicleOfType(vehicleId, "C");
        when(documentPort.findDriverDocsByDriverId(driverId))
                .thenReturn(Flux.just(license(driverId, "B", null)));
        assertLicenseError(driverId, vehicleId, "DRV_011");
    }

    @Test
    void license_validAndCoveringCategory_isAccepted() {
        UUID driverId = UUID.randomUUID(), vehicleId = UUID.randomUUID();
        vehicleOfType(vehicleId, "C");
        when(documentPort.findDriverDocsByDriverId(driverId)).thenReturn(Flux.just(
                license(driverId, "B", java.time.LocalDate.of(2020, 1, 1)),
                license(driverId, "b; c", java.time.LocalDate.of(2030, 1, 1))));
        LocalDateTime start = LocalDateTime.of(2026, 11, 2, 8, 0);
        StepVerifier.create(service.assertDriverLicensed(driverId, vehicleId, start, start.plusHours(4)))
                .verifyComplete();
    }
}
