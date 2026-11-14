package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.MissionException;
import com.yowyob.fleet.domain.model.Mission;
import com.yowyob.fleet.domain.ports.in.ManageMissionUseCase;
import com.yowyob.fleet.domain.ports.out.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service applicatif pour la gestion des Missions de transport / livraison.
 * Responsabilités G4 :
 * - Ordonnancement et vérification des disponibilités (véhicule & conducteur).
 * - Prévention des conflits d'affectation et interdiction d'affectation en maintenance.
 * - Synchronisation avec le service TiiBnTick / Cœur central (G1).
 * - Respect des invariants du cahier des charges ENSPY 2026-2027.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MissionService implements ManageMissionUseCase {

    private final MissionPersistencePort missionPersistence;
    private final DeliveryProofPersistencePort proofPersistence;
    private final VehiclePersistencePort vehiclePersistence;
    private final DriverPersistencePort driverPersistence;
    private final TiiBnTickMissionPort tiiBnTickPort;
    private final SendNotificationPort notificationPort;

    @Override
    @Transactional
    public Mono<Mission> createMission(CreateMissionCommand cmd) {
        log.info("Création de mission : vehicule={}, conducteur={}, début={}",
                cmd.vehicleId(), cmd.driverId(), cmd.scheduledStart());

        // 1. Validation de l'existence et disponibilité du véhicule
        Mono<Void> validateVehicle = vehiclePersistence.getLocalDataById(cmd.vehicleId())
                .switchIfEmpty(Mono.error(MissionException.vehicleNotAvailable(cmd.vehicleId())))
                .flatMap(v -> {
                    // Interdiction d'affectation en maintenance
                    if ("MAINTENANCE".equalsIgnoreCase(v.status())) {
                        return Mono.error(MissionException.vehicleNotAvailable(cmd.vehicleId()));
                    }
                    return Mono.empty();
                })
                .then(missionPersistence.existsOverlappingMissionForVehicle(
                        cmd.vehicleId(),
                        cmd.scheduledStart(),
                        cmd.scheduledEnd() != null ? cmd.scheduledEnd() : cmd.scheduledStart().plusHours(4),
                        null))
                .flatMap(overlap -> {
                    if (Boolean.TRUE.equals(overlap)) {
                        return Mono.error(MissionException.vehicleNotAvailable(cmd.vehicleId()));
                    }
                    return Mono.empty();
                });

        // 2. Validation de l'existence et disponibilité du conducteur
        Mono<Void> validateDriver = driverPersistence.findById(cmd.driverId())
                .switchIfEmpty(Mono.error(MissionException.driverNotAvailable(cmd.driverId())))
                .flatMap(d -> {
                    if (!d.status()) {
                        return Mono.error(MissionException.driverNotAvailable(cmd.driverId()));
                    }
                    return missionPersistence.existsOverlappingMissionForDriver(
                            cmd.driverId(),
                            cmd.scheduledStart(),
                            cmd.scheduledEnd() != null ? cmd.scheduledEnd() : cmd.scheduledStart().plusHours(4),
                            null);
                })
                .flatMap(overlap -> {
                    if (Boolean.TRUE.equals(overlap)) {
                        return Mono.error(MissionException.driverNotAvailable(cmd.driverId()));
                    }
                    return Mono.empty();
                });

        // 3. Construction et persistance de la mission
        return Mono.when(validateVehicle, validateDriver)
                .then(Mono.defer(() -> {
                    Mission mission = new Mission(
                            cmd.vehicleId(),
                            cmd.driverId(),
                            cmd.fleetManagerId(),
                            cmd.type() != null ? cmd.type() : Mission.Type.DELIVERY,
                            cmd.scheduledStart(),
                            cmd.scheduledEnd(),
                            cmd.startLocationId(),
                            cmd.endLocationId(),
                            cmd.tenantId()
                    );

                    if (cmd.tiiBnTickDeliveryId() != null) {
                        mission.linkToDelivery(cmd.tiiBnTickDeliveryId(), cmd.tiiBnTickParcelId());
                    }
                    if (cmd.geofenceObjectId() != null) {
                        mission.linkToGeofenceObject(cmd.geofenceObjectId());
                    }

                    return missionPersistence.save(mission);
                }))
                .doOnSuccess(saved -> log.info("Mission créée avec succès (ID: {})", saved.getId()));
    }

    @Override
    public Mono<Mission> getById(UUID id) {
        return missionPersistence.findById(id)
                .switchIfEmpty(Mono.error(MissionException.missionNotFound(id)));
    }

    @Override
    @Transactional
    public Mono<Mission> startMission(UUID missionId, UUID driverId) {
        log.info("Démarrage de la mission {} par le conducteur {}", missionId, driverId);

        return missionPersistence.findById(missionId)
                .switchIfEmpty(Mono.error(MissionException.missionNotFound(missionId)))
                .flatMap(mission -> {
                    if (!mission.getDriverId().equals(driverId)) {
                        return Mono.error(MissionException.driverNotAvailable(driverId));
                    }

                    try {
                        mission.start();
                    } catch (IllegalStateException e) {
                        return Mono.error(MissionException.invalidStatusTransition(
                                mission.getStatus().name(), Mission.Status.ACTIVE.name()));
                    }

                    return missionPersistence.save(mission)
                            .flatMap(saved -> {
                                // Notification TiiBnTick si la mission est liée à une livraison G1
                                if (saved.getTiiBnTickDeliveryId() != null) {
                                    return tiiBnTickPort.notifyMissionStarted(
                                            saved.getTiiBnTickDeliveryId(),
                                            saved.getId(),
                                            saved.getDriverId(),
                                            saved.getVehicleId()
                                    ).thenReturn(saved);
                                }
                                return Mono.just(saved);
                            });
                });
    }

    @Override
    @Transactional
    public Mono<Mission> completeMission(UUID missionId, UUID driverId) {
        log.info("Clôture de la mission {} par le conducteur {}", missionId, driverId);

        return missionPersistence.findById(missionId)
                .switchIfEmpty(Mono.error(MissionException.missionNotFound(missionId)))
                .flatMap(mission -> {
                    if (!mission.getDriverId().equals(driverId)) {
                        return Mono.error(MissionException.driverNotAvailable(driverId));
                    }

                    // Invariant cahier des charges : clôture conditionnée à une preuve de livraison
                    return proofPersistence.findByMissionId(missionId)
                            .switchIfEmpty(Mono.error(MissionException.proofNotFound(missionId)))
                            .flatMap(proof -> {
                                try {
                                    mission.complete();
                                } catch (IllegalStateException e) {
                                    return Mono.error(MissionException.invalidStatusTransition(
                                            mission.getStatus().name(), Mission.Status.COMPLETED.name()));
                                }

                                return missionPersistence.save(mission)
                                        .flatMap(completed -> {
                                            if (completed.getTiiBnTickDeliveryId() != null) {
                                                return tiiBnTickPort.notifyMissionCompleted(
                                                        completed.getTiiBnTickDeliveryId(),
                                                        completed.getId(),
                                                        proof.getId()
                                                ).thenReturn(completed);
                                            }
                                            return Mono.just(completed);
                                        });
                            });
                });
    }

    @Override
    @Transactional
    public Mono<Mission> cancelMission(UUID missionId, String reason, UUID operatorId) {
        log.warn("Annulation de la mission {} par l'opérateur {}. Motif : {}", missionId, operatorId, reason);

        return missionPersistence.findById(missionId)
                .switchIfEmpty(Mono.error(MissionException.missionNotFound(missionId)))
                .flatMap(mission -> {
                    try {
                        mission.cancel(reason);
                    } catch (IllegalStateException e) {
                        return Mono.error(MissionException.missionAlreadyClosed(missionId));
                    }

                    return missionPersistence.save(mission)
                            .flatMap(cancelled -> {
                                if (cancelled.getTiiBnTickDeliveryId() != null) {
                                    return tiiBnTickPort.notifyMissionIncident(
                                            cancelled.getTiiBnTickDeliveryId(),
                                            cancelled.getId(),
                                            "MISSION_CANCELLED",
                                            reason
                                    ).thenReturn(cancelled);
                                }
                                return Mono.just(cancelled);
                            });
                });
    }

    @Override
    @Transactional
    public Mono<Mission> linkToTiiBnTickDelivery(UUID missionId, UUID deliveryId, String parcelId) {
        return missionPersistence.findById(missionId)
                .switchIfEmpty(Mono.error(MissionException.missionNotFound(missionId)))
                .flatMap(mission -> {
                    try {
                        mission.linkToDelivery(deliveryId, parcelId);
                    } catch (IllegalStateException e) {
                        return Mono.error(MissionException.externalDeliveryImmutable(missionId));
                    }
                    return missionPersistence.save(mission);
                });
    }

    @Override
    @Transactional
    public Mono<Mission> linkToGeofence(UUID missionId, String geofenceObjectId) {
        return missionPersistence.findById(missionId)
                .switchIfEmpty(Mono.error(MissionException.missionNotFound(missionId)))
                .flatMap(mission -> {
                    mission.linkToGeofenceObject(geofenceObjectId);
                    return missionPersistence.save(mission);
                });
    }

    @Override
    public Flux<Mission> getMissionsByManager(UUID fleetManagerId) {
        return missionPersistence.findByManagerId(fleetManagerId);
    }

    @Override
    public Flux<Mission> getMissionsByDriver(UUID driverId) {
        return missionPersistence.findByDriverId(driverId);
    }

    @Override
    public Flux<Mission> getMissionsByVehicle(UUID vehicleId) {
        return missionPersistence.findByVehicleId(vehicleId);
    }

    @Override
    public Flux<Mission> getMissionsByStatus(Mission.Status status, UUID fleetManagerId) {
        return missionPersistence.findByStatus(status, fleetManagerId);
    }
}
