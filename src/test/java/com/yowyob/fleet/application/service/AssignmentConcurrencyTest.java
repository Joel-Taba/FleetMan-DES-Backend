package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.MissionException;
import com.yowyob.fleet.domain.model.Driver;
import com.yowyob.fleet.domain.model.Mission;
import com.yowyob.fleet.domain.model.Vehicle;
import com.yowyob.fleet.domain.ports.in.ManageMissionUseCase.CreateMissionCommand;
import com.yowyob.fleet.domain.ports.out.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssignmentConcurrencyTest {

    @Mock private MissionPersistencePort missionPersistence;
    @Mock private DeliveryProofPersistencePort proofPersistence;
    @Mock private VehiclePersistencePort vehiclePersistence;
    @Mock private DriverPersistencePort driverPersistence;
    @Mock private TiiBnTickMissionPort tiiBnTickPort;
    @Mock private SendNotificationPort notificationPort;

    private MissionService missionService;

    private UUID vehicleId;
    private UUID driverId;
    private UUID managerId;

    @BeforeEach
    void setUp() {
        missionService = new MissionService(
                missionPersistence,
                proofPersistence,
                vehiclePersistence,
                driverPersistence,
                tiiBnTickPort,
                notificationPort
        );

        vehicleId = UUID.randomUUID();
        driverId = UUID.randomUUID();
        managerId = UUID.randomUUID();
    }

    @Test
    @DisplayName("R6 : Refus strict d'affectation sur un véhicule en statut MAINTENANCE")
    void testCreateMission_RejectsVehicleInMaintenance() {
        Vehicle maintenanceVehicle = new Vehicle(
                vehicleId, UUID.randomUUID(), managerId, null, null,
                "LT-999-MAINT", "VIN123", "Toyota", "Hiace", 2021,
                "MANUAL", "DIESEL", 70.0, 15, 9.5,
                "White", "MAINTENANCE", null, null, null, List.of(),
                null, null, null, null, null
        );

        when(vehiclePersistence.getLocalDataById(vehicleId)).thenReturn(Mono.just(maintenanceVehicle));

        CreateMissionCommand cmd = new CreateMissionCommand(
                vehicleId,
                driverId,
                managerId,
                Mission.Type.DELIVERY,
                LocalDateTime.now().plusHours(1),
                LocalDateTime.now().plusHours(4),
                null, null, null, null, null, null, null
        );

        StepVerifier.create(missionService.createMission(cmd))
                .expectErrorMatches(throwable -> throwable instanceof MissionException
                        && "MSN_002".equals(((MissionException) throwable).getBusinessCode()))
                .verify();
    }

    @Test
    @DisplayName("R6 : Refus d'affectation concurrente lors d'un chevauchement d'horaires sur le même véhicule")
    void testCreateMission_RejectsOverlappingVehicleMission() {
        Vehicle availableVehicle = new Vehicle(
                vehicleId, UUID.randomUUID(), managerId, null, null,
                "LT-100-OK", "VIN456", "Toyota", "Yaris", 2022,
                "MANUAL", "PETROL", 45.0, 5, 6.5,
                "Grey", "AVAILABLE", null, null, null, List.of(),
                null, null, null, null, null
        );

        when(vehiclePersistence.getLocalDataById(vehicleId)).thenReturn(Mono.just(availableVehicle));
        when(missionPersistence.existsOverlappingMissionForVehicle(any(), any(), any(), any()))
                .thenReturn(Mono.just(true)); // Chevauchement détecté

        CreateMissionCommand cmd = new CreateMissionCommand(
                vehicleId,
                driverId,
                managerId,
                Mission.Type.DELIVERY,
                LocalDateTime.now().plusHours(1),
                LocalDateTime.now().plusHours(4),
                null, null, null, null, null, null, null
        );

        StepVerifier.create(missionService.createMission(cmd))
                .expectErrorMatches(throwable -> throwable instanceof MissionException
                        && "MSN_002".equals(((MissionException) throwable).getBusinessCode()))
                .verify();
    }

    @Test
    @DisplayName("R6 : Refus d'affectation sur un conducteur inactif")
    void testCreateMission_RejectsInactiveDriver() {
        Vehicle availableVehicle = new Vehicle(
                vehicleId, UUID.randomUUID(), managerId, null, null,
                "LT-100-OK", "VIN456", "Toyota", "Yaris", 2022,
                "MANUAL", "PETROL", 45.0, 5, 6.5,
                "Grey", "AVAILABLE", null, null, null, List.of(),
                null, null, null, null, null
        );

        Driver inactiveDriver = new Driver(
                driverId,
                null,
                "PERMIS-1234",
                false, // INACTIF
                null,
                null
        );

        when(vehiclePersistence.getLocalDataById(vehicleId)).thenReturn(Mono.just(availableVehicle));
        when(missionPersistence.existsOverlappingMissionForVehicle(any(), any(), any(), any()))
                .thenReturn(Mono.just(false));
        when(driverPersistence.findById(driverId)).thenReturn(Mono.just(inactiveDriver));

        CreateMissionCommand cmd = new CreateMissionCommand(
                vehicleId,
                driverId,
                managerId,
                Mission.Type.DELIVERY,
                LocalDateTime.now().plusHours(1),
                LocalDateTime.now().plusHours(4),
                null, null, null, null, null, null, null
        );

        StepVerifier.create(missionService.createMission(cmd))
                .expectErrorMatches(throwable -> throwable instanceof MissionException
                        && "MSN_003".equals(((MissionException) throwable).getBusinessCode()))
                .verify();
    }
}

