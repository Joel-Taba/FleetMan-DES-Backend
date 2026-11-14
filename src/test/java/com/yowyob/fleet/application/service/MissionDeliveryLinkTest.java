package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.MissionException;
import com.yowyob.fleet.domain.model.DeliveryProof;
import com.yowyob.fleet.domain.model.Mission;
import com.yowyob.fleet.domain.ports.in.ManageDeliveryProofUseCase;
import com.yowyob.fleet.domain.ports.in.ManageMissionUseCase;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MissionDeliveryLinkTest {

    @Mock private MissionPersistencePort missionPersistence;
    @Mock private DeliveryProofPersistencePort proofPersistence;
    @Mock private VehiclePersistencePort vehiclePersistence;
    @Mock private DriverPersistencePort driverPersistence;
    @Mock private TiiBnTickMissionPort tiiBnTickPort;
    @Mock private SendNotificationPort notificationPort;

    private MissionService missionService;
    private DeliveryProofService proofService;

    private UUID vehicleId;
    private UUID driverId;
    private UUID managerId;
    private UUID missionId;
    private UUID deliveryId;

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

        proofService = new DeliveryProofService(
                proofPersistence,
                missionPersistence,
                tiiBnTickPort
        );

        vehicleId = UUID.randomUUID();
        driverId = UUID.randomUUID();
        managerId = UUID.randomUUID();
        missionId = UUID.randomUUID();
        deliveryId = UUID.randomUUID();
    }

    @Test
    @DisplayName("R5 Chaîne complète : Démarrage de mission et notification TiiBnTick")
    void testMissionStart_Success() {
        Mission mission = new Mission(
                vehicleId,
                driverId,
                managerId,
                Mission.Type.DELIVERY,
                LocalDateTime.now().plusHours(1),
                LocalDateTime.now().plusHours(3),
                null,
                null,
                null
        );
        mission.linkToDelivery(deliveryId, "QR-12345");

        when(missionPersistence.findById(mission.getId())).thenReturn(Mono.just(mission));
        when(missionPersistence.save(any(Mission.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(tiiBnTickPort.notifyMissionStarted(any(), any(), any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(missionService.startMission(mission.getId(), driverId))
                .assertNext(started -> {
                    assertEquals(Mission.Status.ACTIVE, started.getStatus());
                    assertNotNull(started.getActualStart());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("R5/R9 : Clôture impossible si aucune preuve de livraison n'a été déposée")
    void testMissionComplete_FailsWithoutProof() {
        Mission mission = new Mission(
                vehicleId,
                driverId,
                managerId,
                Mission.Type.DELIVERY,
                LocalDateTime.now().plusHours(1),
                LocalDateTime.now().plusHours(3),
                null,
                null,
                null
        );
        mission.start(); // Active

        when(missionPersistence.findById(mission.getId())).thenReturn(Mono.just(mission));
        when(proofPersistence.findByMissionId(mission.getId())).thenReturn(Mono.empty());

        StepVerifier.create(missionService.completeMission(mission.getId(), driverId))
                .expectErrorMatches(throwable -> throwable instanceof MissionException
                        && "MSN_006".equals(((MissionException) throwable).getBusinessCode()))
                .verify();
    }

    @Test
    @DisplayName("R9 Confiance : Détection d'altération de preuve (HMAC invalide)")
    void testProofIntegrity_TamperedDetection() {
        UUID proofId = UUID.randomUUID();
        DeliveryProof proof = new DeliveryProof(missionId, driverId, DeliveryProof.ProofType.PHOTO, null);
        proof.setIntegrityHash("hash-original-correct");

        when(proofPersistence.findById(proofId)).thenReturn(Mono.just(proof));
        when(proofPersistence.save(any(DeliveryProof.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(proofService.verifyProofIntegrity(proofId, "hash-falsifie"))
                .expectErrorMatches(throwable -> throwable instanceof MissionException
                        && "MSN_009".equals(((MissionException) throwable).getBusinessCode()))
                .verify();

        assertEquals(DeliveryProof.ProofStatus.TAMPERED, proof.getStatus());
    }
}

