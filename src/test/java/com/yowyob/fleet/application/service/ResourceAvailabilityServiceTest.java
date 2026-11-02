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
import com.yowyob.fleet.domain.model.DocumentCatalog.TargetType;
import com.yowyob.fleet.domain.model.DocumentRules;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.DocumentEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.VehicleTypeEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.VehicleLocalEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.VehicleTypeR2dbcRepository;
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
    @Mock DocumentComplianceService documentCompliance;
    @Mock VehicleLocalR2dbcRepository vehicleRepository;
    @Mock VehicleTypeR2dbcRepository vehicleTypeRepository;
    @Mock com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.DriverAbsenceR2dbcRepository absenceRepository;

    private ResourceAvailabilityService service;
    private final UUID vehicle = UUID.randomUUID();
    private final UUID driver = UUID.randomUUID();
    private final UUID fleet = UUID.randomUUID();
    private final LocalDateTime start = LocalDateTime.of(2026, 11, 2, 8, 0);
    private final LocalDateTime end = LocalDateTime.of(2026, 11, 2, 12, 0);

    @BeforeEach
    void setUp() {
        service = new ResourceAvailabilityService(tripRepository, assignmentPort, accessScope, resourceLock,
                documentCompliance, vehicleRepository, vehicleTypeRepository, absenceRepository, null);
        when(resourceLock.lock(any(), any())).thenReturn(Mono.empty());
        when(documentCompliance.gaps(any(), any(), any())).thenReturn(Mono.just(java.util.List.of()));
        when(documentCompliance.licensesInForce(any())).thenReturn(Flux.empty());
        when(absenceRepository.findOverlapping(any(), any(), any())).thenReturn(Flux.empty());
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
        Driver inactive = new Driver(driver, fleet, "L1", "LEFT", null, null, null);
        Driver fleetless = new Driver(driver, null, "L1", "ACTIVE", null, null, null);
        StepVerifier.create(service.assertDriverEngageable(UUID.randomUUID(), inactive, TripException::driverInactive))
                .expectError(TripException.class).verify();
        StepVerifier.create(service.assertDriverEngageable(UUID.randomUUID(), fleetless, TripException::driverInactive))
                .expectError(TripException.class).verify();
    }

    private Assignment assignment(UUID vehicleId, UUID driverId) {
        return new Assignment(UUID.randomUUID(), null, fleet, vehicleId, driverId,
                start.minusHours(1), end.plusHours(1), Assignment.Status.PENDING,
                null, null, null);
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

    private DocumentEntity license(String categories, java.time.LocalDate expiry) {
        DocumentEntity d = new DocumentEntity();
        d.setDocType("DRIVING_LICENSE");
        d.setReviewStatus("APPROVED");
        d.setCurrent(true);
        d.setLicenseCategories(categories);
        d.setExpiryDate(expiry);
        return d;
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
        when(documentCompliance.licensesInForce(driverId)).thenReturn(Flux.empty());
        assertLicenseError(driverId, vehicleId, "DRV_009");
    }

    @Test
    void license_expiredBeforeSlotEnd_isRejected() {
        UUID driverId = UUID.randomUUID(), vehicleId = UUID.randomUUID();
        vehicleOfType(vehicleId, "B");
        when(documentCompliance.licensesInForce(driverId))
                .thenReturn(Flux.just(license("B", java.time.LocalDate.of(2026, 11, 1))));
        assertLicenseError(driverId, vehicleId, "DRV_010");
    }

    @Test
    void license_withoutRequiredCategory_isRejected() {
        UUID driverId = UUID.randomUUID(), vehicleId = UUID.randomUUID();
        vehicleOfType(vehicleId, "C");
        when(documentCompliance.licensesInForce(driverId))
                .thenReturn(Flux.just(license("B", null)));
        assertLicenseError(driverId, vehicleId, "DRV_011");
    }

    @Test
    void license_validAndCoveringCategory_isAccepted() {
        UUID driverId = UUID.randomUUID(), vehicleId = UUID.randomUUID();
        vehicleOfType(vehicleId, "C");
        when(documentCompliance.licensesInForce(driverId)).thenReturn(Flux.just(
                license("B", java.time.LocalDate.of(2020, 1, 1)),
                license("b; c", java.time.LocalDate.of(2030, 1, 1))));
        LocalDateTime start = LocalDateTime.of(2026, 11, 2, 8, 0);
        StepVerifier.create(service.assertDriverLicensed(driverId, vehicleId, start, start.plusHours(4)))
                .verifyComplete();
    }

    // ── Équipage qualifié : absences et conformité du véhicule ───────────────

    @Test
    void vehicle_withoutInsurance_isNotCompliant() {
        when(documentCompliance.gaps(TargetType.VEHICLE, vehicle, end.toLocalDate()))
                .thenReturn(Mono.just(java.util.List.of(new DocumentRules.Gap("INSURANCE", true, null))));
        StepVerifier.create(service.assertVehicleCompliant(vehicle, start, end))
                .expectErrorSatisfies(e -> {
                    assertEquals("VHC_013", ((com.yowyob.fleet.domain.exception.VehicleException) e).getBusinessCode());
                    org.junit.jupiter.api.Assertions.assertTrue(e.getMessage().contains("assurance absente"));
                })
                .verify();
    }

    @Test
    void vehicle_checkedAtSlotEnd() {
        LocalDateTime slotEnd = LocalDateTime.of(2026, 11, 5, 18, 0);
        when(documentCompliance.gaps(TargetType.VEHICLE, vehicle, slotEnd.toLocalDate())).thenReturn(Mono.just(java.util.List.of(
                new DocumentRules.Gap("TECHNICAL_CONTROL", false, java.time.LocalDate.of(2026, 11, 3)))));
        StepVerifier.create(service.assertVehicleCompliant(vehicle, start, slotEnd))
                .expectErrorSatisfies(e -> org.junit.jupiter.api.Assertions.assertTrue(
                        e.getMessage().contains("visite technique expirée le 2026-11-03")))
                .verify();
    }

    @Test
    void vehicle_withoutGap_isCompliant() {
        StepVerifier.create(service.assertVehicleCompliant(vehicle, start, end)).verifyComplete();
    }

    @Test
    void driver_missingPieceRequiredByCompany_isRejected_licenseHandledSeparately() {
        UUID driverId = UUID.randomUUID();
        when(documentCompliance.gaps(TargetType.DRIVER, driverId, end.toLocalDate())).thenReturn(Mono.just(java.util.List.of(
                new DocumentRules.Gap("DRIVING_LICENSE", true, null),
                new DocumentRules.Gap("MEDICAL_CERT", true, null))));
        StepVerifier.create(service.assertDriverFileCompliant(driverId, start, end))
                .expectErrorSatisfies(e -> {
                    assertEquals("DRV_021", ((DriverException) e).getBusinessCode());
                    org.junit.jupiter.api.Assertions.assertTrue(e.getMessage().contains("visite médicale"));
                    org.junit.jupiter.api.Assertions.assertFalse(e.getMessage().contains("permis"));
                })
                .verify();
    }

    @Test
    void driver_onLeaveDuringSlot_isRejected() {
        var leave = new com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.DriverAbsenceEntity(
                UUID.randomUUID(), driver, "LEAVE", java.time.LocalDate.of(2026, 11, 1), java.time.LocalDate.of(2026, 11, 15),
                null, null, null, null, null, false);
        when(absenceRepository.findOverlapping(eq(driver), eq(java.time.LocalDate.of(2026, 11, 2)), eq(java.time.LocalDate.of(2026, 11, 2))))
                .thenReturn(Flux.just(leave));
        StepVerifier.create(service.assertDriverPresent(driver, start, end))
                .expectErrorSatisfies(e -> {
                    assertEquals("DRV_012", ((DriverException) e).getBusinessCode());
                    org.junit.jupiter.api.Assertions.assertTrue(e.getMessage().contains("en congé du 2026-11-01 au 2026-11-15"));
                })
                .verify();
    }

    @Test
    void driver_withoutAbsence_isPresent() {
        StepVerifier.create(service.assertDriverPresent(driver, start, end)).verifyComplete();
    }
}
