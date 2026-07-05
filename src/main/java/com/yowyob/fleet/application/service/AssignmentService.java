package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.PlanningException;
import com.yowyob.fleet.domain.model.Assignment;
import com.yowyob.fleet.domain.ports.in.ManageAssignmentUseCase;
import com.yowyob.fleet.domain.ports.out.AssignmentPersistencePort;
import com.yowyob.fleet.domain.ports.out.DriverPersistencePort;
import com.yowyob.fleet.domain.ports.out.VehiclePersistencePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AssignmentService implements ManageAssignmentUseCase {

    private final AssignmentPersistencePort assignmentPersistencePort;
    private final VehiclePersistencePort vehiclePersistencePort;
    private final DriverPersistencePort driverPersistencePort;

    @Override
    public Mono<Assignment> createAssignment(CreateAssignmentCommand cmd) {
        // 1. Vérifier l'existence du véhicule
        return vehiclePersistencePort.getLocalDataById(cmd.vehicleId())
                .switchIfEmpty(Mono.error(PlanningException.vehicleNotFound(cmd.vehicleId())))
                .flatMap(vehicle -> {
                    // 2. Vérifier que le véhicule est disponible (pas en maintenance)
                    if ("MAINTENANCE".equals(vehicle.status())) {
                        return Mono.error(PlanningException.vehicleNotAvailable(cmd.vehicleId()));
                    }
                    // 3. Vérifier l'existence du conducteur
                    return driverPersistencePort.findById(cmd.driverId())
                            .switchIfEmpty(Mono.error(PlanningException.driverNotFound(cmd.driverId())));
                })
                .flatMap(driver -> {
                    // 4. Vérifier que le conducteur est actif
                    if ("INACTIVE".equals(driver.status())) {
                        return Mono.error(PlanningException.driverInactive(cmd.driverId()));
                    }
                    // 5. Détecter les conflits véhicule
                    return assignmentPersistencePort
                            .findConflictingByVehicle(cmd.vehicleId(),
                                    cmd.startDatetime(), cmd.endDatetime(), null)
                            .hasElements()
                            .flatMap(hasVehicleConflict -> {
                                if (hasVehicleConflict) {
                                    return Mono.error(PlanningException.vehicleConflict(
                                            cmd.vehicleId(),
                                            cmd.startDatetime().toString(),
                                            cmd.endDatetime().toString()));
                                }
                                // 6. Détecter les conflits conducteur
                                return assignmentPersistencePort
                                        .findConflictingByDriver(cmd.driverId(),
                                                cmd.startDatetime(), cmd.endDatetime(), null)
                                        .hasElements();
                            })
                            .flatMap(hasDriverConflict -> {
                                if (hasDriverConflict) {
                                    return Mono.error(PlanningException.driverConflict(
                                            cmd.driverId(),
                                            cmd.startDatetime().toString(),
                                            cmd.endDatetime().toString()));
                                }
                                // 7. Créer l'affectation
                                Assignment assignment = new Assignment(
                                        null,
                                        cmd.scheduleId(),
                                        cmd.fleetId(),
                                        cmd.vehicleId(),
                                        cmd.driverId(),
                                        cmd.missionId(),
                                        cmd.startDatetime(),
                                        cmd.endDatetime(),
                                        Assignment.Status.PENDING,
                                        cmd.startLocation(),
                                        cmd.endLocation(),
                                        cmd.estimatedKm(),
                                        null,
                                        cmd.notes(),
                                        null
                                );
                                return assignmentPersistencePort.save(assignment);
                            });
                })
                .doOnSuccess(a -> log.info("Affectation créée : véhicule={}, conducteur={}, début={}",
                        a.getVehicleId(), a.getDriverId(), a.getStartDatetime()));
    }

    @Override
    public Mono<Assignment> getById(UUID id) {
        return assignmentPersistencePort.findById(id)
                .switchIfEmpty(Mono.error(PlanningException.assignmentNotFound(id)));
    }

    @Override
    public Flux<Assignment> getAllByManager(UUID managerId) {
        return assignmentPersistencePort.findAllByManagerId(managerId);
    }

    @Override
    public Flux<Assignment> getAllBySchedule(UUID scheduleId) {
        return assignmentPersistencePort.findAllByScheduleId(scheduleId);
    }

    @Override
    public Flux<Assignment> getByVehicle(UUID vehicleId) {
        return assignmentPersistencePort.findByVehicleId(vehicleId);
    }

    @Override
    public Flux<Assignment> getByDriver(UUID driverId) {
        return assignmentPersistencePort.findByDriverId(driverId);
    }

    @Override
    public Flux<Assignment> getByDriverToday(UUID driverId) {
        return assignmentPersistencePort.findByDriverIdAndDate(driverId, LocalDate.now());
    }

    @Override
    public Flux<Assignment> getByDateRange(UUID managerId, LocalDate start, LocalDate end) {
        return assignmentPersistencePort.findByDateRange(managerId, start, end);
    }

    @Override
    public Flux<Assignment> getConflicts(UUID managerId) {
        // Retourne les affectations actives qui se chevauchent pour le même véhicule ou conducteur
        return assignmentPersistencePort.findAllByManagerId(managerId)
                .filter(Assignment::isActive)
                .filterWhen(a -> assignmentPersistencePort
                        .findConflictingByVehicle(a.getVehicleId(),
                                a.getStartDatetime(), a.getEndDatetime(), a.getId())
                        .hasElements()
                        .map(hasConflict -> hasConflict)
                );
    }

    @Override
    public Mono<Boolean> checkVehicleAvailability(UUID vehicleId,
                                                   LocalDateTime start,
                                                   LocalDateTime end,
                                                   UUID excludeAssignmentId) {
        return assignmentPersistencePort
                .findConflictingByVehicle(vehicleId, start, end, excludeAssignmentId)
                .hasElements()
                .map(hasConflict -> !hasConflict); // true = disponible
    }

    @Override
    public Mono<Boolean> checkDriverAvailability(UUID driverId,
                                                  LocalDateTime start,
                                                  LocalDateTime end,
                                                  UUID excludeAssignmentId) {
        return assignmentPersistencePort
                .findConflictingByDriver(driverId, start, end, excludeAssignmentId)
                .hasElements()
                .map(hasConflict -> !hasConflict); // true = disponible
    }

    @Override
    public Mono<Assignment> updateStatus(UUID id, Assignment.Status newStatus, BigDecimal actualKm) {
        return assignmentPersistencePort.findById(id)
                .switchIfEmpty(Mono.error(PlanningException.assignmentNotFound(id)))
                .flatMap(assignment -> {
                    switch (newStatus) {
                        case IN_PROGRESS -> assignment.start();
                        case COMPLETED   -> assignment.complete(actualKm);
                        case CANCELLED   -> assignment.cancel(null);
                        case NO_SHOW     -> assignment.markNoShow();
                        default -> throw new IllegalArgumentException(
                                "Transition vers " + newStatus + " non supportée via cet endpoint.");
                    }
                    return assignmentPersistencePort.save(assignment);
                });
    }

    @Override
    public Mono<Void> delete(UUID id) {
        return assignmentPersistencePort.existsById(id)
                .flatMap(exists -> {
                    if (!exists) return Mono.error(PlanningException.assignmentNotFound(id));
                    return assignmentPersistencePort.deleteById(id);
                });
    }
}
