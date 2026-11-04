package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.PlanningException;
import com.yowyob.fleet.domain.model.Assignment;
import com.yowyob.fleet.domain.model.Driver;
import com.yowyob.fleet.domain.model.Schedule;
import com.yowyob.fleet.domain.ports.in.ManageAssignmentUseCase;
import com.yowyob.fleet.domain.ports.out.AssignmentPersistencePort;
import com.yowyob.fleet.domain.ports.out.DriverPersistencePort;
import com.yowyob.fleet.domain.ports.out.SchedulePersistencePort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.VehicleLocalEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.TripR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.VehicleLocalR2dbcRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AssignmentService implements ManageAssignmentUseCase {

    private final AssignmentPersistencePort assignmentPersistencePort;
    private final VehicleLocalR2dbcRepository vehicleRepository;
    private final DriverPersistencePort driverPersistencePort;
    private final SchedulePersistencePort schedulePersistencePort;
    private final TripR2dbcRepository tripRepository;
    private final ResourceAvailabilityService availability;
    private final org.springframework.r2dbc.core.DatabaseClient db;
    private final WorkRulesService workRules;

    @Override
    @Transactional
    public Mono<Assignment> createAssignment(CreateAssignmentCommand cmd) {
        if (cmd.startDatetime() == null || cmd.endDatetime() == null
                || !cmd.endDatetime().isAfter(cmd.startDatetime())) {
            return Mono.error(PlanningException.invalidDates());
        }
        // 1. Véhicule existant, non supprimé, disponible (pas en maintenance)
        return requireUsableVehicle(cmd.vehicleId())
                .flatMap(vehicle -> {
                    // 2. L'affectation appartient à la flotte de son véhicule
                    UUID fleetId = cmd.fleetId() != null ? cmd.fleetId() : vehicle.getFleetId();
                    if (vehicle.getFleetId() == null || !vehicle.getFleetId().equals(fleetId)) {
                        return Mono.<Assignment>error(PlanningException.vehicleNotInFleet(cmd.vehicleId()));
                    }
                    // 3. Conducteur existant et actif — 4. planning compatible —
                    // 5. créneau libre (affectations ET trajets)
                    return availability.assertFleetOperable(fleetId)
                            .then(requireActiveDriver(cmd.driverId()))
                            .then(checkSchedule(cmd.scheduleId(), fleetId, cmd.startDatetime(), cmd.endDatetime()))
                            .then(availability.assertCrewQualified(
                                    cmd.driverId(), cmd.vehicleId(), cmd.startDatetime(), cmd.endDatetime()))
                            .then(availability.assertAssignmentSlotFree(
                                    cmd.vehicleId(), cmd.driverId(),
                                    cmd.startDatetime(), cmd.endDatetime(), null))
                            .then(workRules.enforce(fleetId, cmd.driverId(), cmd.startDatetime(), cmd.endDatetime(),
                                    null, cmd.workRuleOverride()))
                            .flatMap(override -> Mono.defer(() -> {
                                // 6. Créer l'affectation
                                Assignment assignment = new Assignment(
                                        null,
                                        cmd.scheduleId(),
                                        fleetId,
                                        cmd.vehicleId(),
                                        cmd.driverId(),
                                        cmd.startDatetime(),
                                        cmd.endDatetime(),
                                        Assignment.Status.PENDING,
                                        null,
                                        cmd.notes(),
                                        null
                                );
                                return assignmentPersistencePort.save(assignment)
                                        .flatMap(saved -> workRules.recordOverride(saved.getId(), override)
                                                .then(notifyNewShift(saved, cmd.changeReason()))
                                                .thenReturn(saved));
                            }));
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
        // Affectations actives qui se chevauchent pour le même véhicule OU le même
        // conducteur (doc §6.5) — seuls les conflits véhicule étaient remontés.
        return assignmentPersistencePort.findAllByManagerId(managerId)
                .filter(Assignment::isActive)
                .filterWhen(a -> assignmentPersistencePort
                        .findConflictingByVehicle(a.getVehicleId(),
                                a.getStartDatetime(), a.getEndDatetime(), a.getId())
                        .hasElements()
                        .flatMap(vehicleConflict -> Boolean.TRUE.equals(vehicleConflict)
                                ? Mono.just(true)
                                : assignmentPersistencePort
                                        .findConflictingByDriver(a.getDriverId(),
                                                a.getStartDatetime(), a.getEndDatetime(), a.getId())
                                        .hasElements())
                );
    }

    @Override
    public Mono<Boolean> checkVehicleAvailability(UUID vehicleId,
                                                   LocalDateTime start,
                                                   LocalDateTime end,
                                                   UUID excludeAssignmentId) {
        // Disponible = aucune affectation NI aucun trajet non terminé sur le créneau.
        return assignmentPersistencePort
                .findConflictingByVehicle(vehicleId, start, end, excludeAssignmentId)
                .hasElements()
                .flatMap(conflict -> Boolean.TRUE.equals(conflict)
                        ? Mono.just(true)
                        : tripRepository.existsOverlappingTripForVehicle(
                                vehicleId, start, end, LocalDateTime.now(), null))
                .map(conflict -> !conflict); // true = disponible
    }

    @Override
    public Mono<Boolean> checkDriverAvailability(UUID driverId,
                                                  LocalDateTime start,
                                                  LocalDateTime end,
                                                  UUID excludeAssignmentId) {
        return assignmentPersistencePort
                .findConflictingByDriver(driverId, start, end, excludeAssignmentId)
                .hasElements()
                .flatMap(conflict -> Boolean.TRUE.equals(conflict)
                        ? Mono.just(true)
                        : tripRepository.existsOverlappingTripForDriver(
                                driverId, start, end, LocalDateTime.now(), null))
                .map(conflict -> !conflict); // true = disponible
    }

    @Override
    @Transactional
    public Mono<Assignment> updateStatus(UUID id, Assignment.Status newStatus, BigDecimal actualKm, String reason) {
        if (newStatus != Assignment.Status.CANCELLED && newStatus != Assignment.Status.NO_SHOW) {
            return Mono.error(PlanningException.manualStatusNotAllowed(String.valueOf(newStatus)));
        }
        return assignmentPersistencePort.findById(id)
                .switchIfEmpty(Mono.error(PlanningException.assignmentNotFound(id)))
                .flatMap(assignment -> countTrips(id, "('DEPARTED', 'ARRIVED')")
                        .zipWith(newStatus == Assignment.Status.CANCELLED
                                ? workRules.requireReasonIfPublished(assignment.getScheduleId(), reason)
                                : Mono.just(""))
                        .flatMap(t -> {
                            long inProgress = t.getT1();
                            String why = t.getT2();
                            if (inProgress > 0) {
                                return Mono.<Assignment>error(PlanningException.shiftHasTripInProgress(id));
                            }
                            try {
                                if (newStatus == Assignment.Status.CANCELLED) {
                                    assignment.cancel(null);
                                } else {
                                    assignment.markNoShow();
                                }
                            } catch (IllegalStateException e) {
                                return Mono.<Assignment>error(PlanningException.invalidStatusTransition(e.getMessage()));
                            }
                            // Les trajets planifiés du poste restent planifiés mais n'y sont plus rattachés.
                            return db.sql("UPDATE fleet.trips SET assignment_id = NULL "
                                            + "WHERE assignment_id = :id AND status = 'SCHEDULED'")
                                    .bind("id", id)
                                    .fetch().rowsUpdated()
                                    .then(assignmentPersistencePort.save(assignment))
                                    .flatMap(saved -> newStatus != Assignment.Status.CANCELLED
                                            ? Mono.just(saved)
                                            : workRules.recordCancel(id, why)
                                                    .then(why.isEmpty() ? Mono.empty() : workRules.notifyDriver(saved.getDriverId(),
                                                            "Poste annulé", "Votre poste " + WorkRulesService.describe(saved)
                                                                    + " est annulé : " + why, saved.getScheduleId(), saved.getId()))
                                                    .thenReturn(saved));
                        }));
    }

    @Override
    public Mono<Long> countLinkedTrips(UUID assignmentId) {
        return countTrips(assignmentId, null);
    }

    private Mono<Long> countTrips(UUID assignmentId, String statuses) {
        String sql = "SELECT COUNT(*) AS n FROM fleet.trips WHERE assignment_id = :id AND deleted_at IS NULL"
                + (statuses != null ? " AND status IN " + statuses : "");
        return db.sql(sql).bind("id", assignmentId)
                .map(row -> row.get("n", Long.class))
                .one()
                .defaultIfEmpty(0L);
    }

    @Override
    public Mono<Boolean> isVisibleToDriver(Assignment assignment) {
        if (assignment.getScheduleId() == null) {
            return Mono.just(true);
        }
        return schedulePersistencePort.findById(assignment.getScheduleId())
                .map(s -> !s.isDraft())
                .defaultIfEmpty(true);
    }

    @Override
    @Transactional
    public Mono<Assignment> updateShift(UUID id, ShiftChange change) {
        return assignmentPersistencePort.findById(id)
                .switchIfEmpty(Mono.error(PlanningException.assignmentNotFound(id)))
                .flatMap(existing -> {
                    if (existing.getStatus() != Assignment.Status.PENDING) {
                        return Mono.error(PlanningException.invalidStatusTransition(
                                "Seul un poste à venir peut être modifié."));
                    }
                    UUID nextVehicle = change.vehicleId() != null ? change.vehicleId() : existing.getVehicleId();
                    UUID nextDriver = change.driverId() != null ? change.driverId() : existing.getDriverId();
                    LocalDateTime nextStart = change.startDatetime() != null ? change.startDatetime() : existing.getStartDatetime();
                    LocalDateTime nextEnd = change.endDatetime() != null ? change.endDatetime() : existing.getEndDatetime();
                    if (!nextEnd.isAfter(nextStart)) {
                        return Mono.error(PlanningException.invalidDates());
                    }
                    boolean changed = !nextVehicle.equals(existing.getVehicleId()) || !nextDriver.equals(existing.getDriverId())
                            || !nextStart.equals(existing.getStartDatetime()) || !nextEnd.equals(existing.getEndDatetime());
                    if (!changed) {
                        return Mono.just(existing);
                    }
                    return workRules.requireReasonIfPublished(existing.getScheduleId(), change.reason())
                            .flatMap(reason -> requireUsableVehicle(nextVehicle)
                                    .flatMap(v -> {
                                        if (!existing.getFleetId().equals(v.getFleetId())) {
                                            return Mono.<Void>error(PlanningException.vehicleNotInFleet(nextVehicle));
                                        }
                                        return requireActiveDriver(nextDriver);
                                    })
                                    .then(checkSchedule(existing.getScheduleId(), existing.getFleetId(), nextStart, nextEnd))
                                    .then(availability.assertCrewQualified(nextDriver, nextVehicle, nextStart, nextEnd))
                                    .then(availability.assertAssignmentSlotFree(nextVehicle, nextDriver, nextStart, nextEnd, id))
                                    .then(workRules.enforceChange(existing.getFleetId(), nextDriver, nextStart, nextEnd, id,
                                            change.workRuleOverride(), existing.getDriverId(),
                                            existing.getStartDatetime(), existing.getEndDatetime()))
                                    .flatMap(override -> {
                                        Assignment updated = new Assignment(
                                                existing.getId(), existing.getScheduleId(), existing.getFleetId(),
                                                nextVehicle, nextDriver, nextStart, nextEnd, existing.getStatus(),
                                                existing.getActualKm(), existing.getNotes(), existing.getCreatedAt());
                                        return assignmentPersistencePort.save(updated)
                                                .flatMap(saved -> workRules.recordOverride(id, override)
                                                        .then(reason.isEmpty() ? Mono.empty() : notifyChange(existing, saved, reason))
                                                        .thenReturn(saved));
                                    }));
                });
    }

    /** Planning publié : le chauffeur est prévenu du nouveau poste ; à défaut de motif, un message neutre. */
    private Mono<Void> notifyNewShift(Assignment saved, String reason) {
        return workRules.isPublished(saved.getScheduleId()).flatMap(published -> {
            if (!published) return Mono.<Void>empty();
            String why = reason != null && !reason.isBlank() ? " : " + reason.trim() : "";
            return workRules.recordChange(saved.getId(), reason != null && !reason.isBlank() ? reason.trim() : "Poste ajouté")
                    .then(workRules.notifyDriver(saved.getDriverId(), "Nouveau poste",
                            "Un poste vous est attribué " + WorkRulesService.describe(saved) + why + ".",
                            saved.getScheduleId(), saved.getId()));
        });
    }

    private Mono<Void> notifyChange(Assignment before, Assignment after, String reason) {
        Mono<Void> record = workRules.recordChange(after.getId(), reason);
        if (!before.getDriverId().equals(after.getDriverId())) {
            return record
                    .then(workRules.notifyDriver(before.getDriverId(), "Poste retiré",
                            "Le poste " + WorkRulesService.describe(before) + " ne vous est plus attribué : " + reason,
                            before.getScheduleId(), before.getId()))
                    .then(workRules.notifyDriver(after.getDriverId(), "Nouveau poste",
                            "Un poste vous est attribué " + WorkRulesService.describe(after) + " : " + reason,
                            after.getScheduleId(), after.getId()));
        }
        return record.then(workRules.notifyDriver(after.getDriverId(), "Poste modifié",
                "Votre poste " + WorkRulesService.describe(before) + " devient " + WorkRulesService.describe(after) + " : " + reason,
                after.getScheduleId(), after.getId()));
    }

    @Override
    public Mono<Void> delete(UUID id, String reason) {
        return assignmentPersistencePort.findById(id)
                .switchIfEmpty(Mono.error(PlanningException.assignmentNotFound(id)))
                .flatMap(a -> {
                    // Une affectation commencée ou terminée fait partie de l'historique
                    // (kilométrage réel, suivi) : on l'annule, on ne la supprime pas.
                    if (a.getStatus() == Assignment.Status.IN_PROGRESS
                            || a.getStatus() == Assignment.Status.COMPLETED) {
                        return Mono.error(PlanningException.assignmentNotDeletable(id));
                    }
                    return workRules.requireReasonIfPublished(a.getScheduleId(), reason)
                            .flatMap(why -> assignmentPersistencePort.deleteById(id)
                                    .then(why.isEmpty() || a.getStatus() != Assignment.Status.PENDING ? Mono.<Void>empty()
                                            : workRules.notifyDriver(a.getDriverId(), "Poste supprimé",
                                                    "Le poste " + WorkRulesService.describe(a) + " est supprimé : " + why,
                                                    a.getScheduleId(), a.getId())));
                });
    }

    // ── Règles communes création / réaffectation ─────────────────────────────

    private Mono<VehicleLocalEntity> requireUsableVehicle(UUID vehicleId) {
        return vehicleRepository.findById(vehicleId)
                .filter(v -> v.getDeletedAt() == null)
                .switchIfEmpty(Mono.error(PlanningException.vehicleNotFound(vehicleId)))
                .flatMap(v -> !com.yowyob.fleet.domain.model.VehicleLifecycle.isDispatchable(v.getStatus())
                        ? Mono.<VehicleLocalEntity>error(PlanningException.vehicleNotAvailable(vehicleId, v.getStatus()))
                        : Mono.just(v));
    }

    /** Conducteur dans les effectifs (pas LEFT) et rattaché à une flotte. */
    private Mono<Void> requireActiveDriver(UUID driverId) {
        return driverPersistencePort.findById(driverId)
                .switchIfEmpty(Mono.error(PlanningException.driverNotFound(driverId)))
                .flatMap((Driver driver) -> !"ACTIVE".equals(driver.status()) || driver.fleetId() == null
                        ? Mono.<Void>error(PlanningException.driverInactive(driverId))
                        : Mono.<Void>empty());
    }

    /**
     * Une affectation rattachée à un planning doit appartenir à sa flotte, se situer dans
     * sa période, et le planning ne doit pas être archivé (« données figées »).
     */
    private Mono<Void> checkSchedule(UUID scheduleId, UUID fleetId,
                                     LocalDateTime start, LocalDateTime end) {
        if (scheduleId == null) return Mono.empty();
        return schedulePersistencePort.findById(scheduleId)
                .switchIfEmpty(Mono.error(PlanningException.scheduleNotFound(scheduleId)))
                .flatMap(schedule -> {
                    if (schedule.getStatus() == Schedule.Status.ARCHIVED) {
                        return Mono.<Void>error(PlanningException.scheduleArchived(scheduleId));
                    }
                    LocalDateTime periodStart = schedule.getStartDate().atStartOfDay();
                    LocalDateTime periodEnd = schedule.getEndDate().plusDays(1).atStartOfDay();
                    if (!fleetId.equals(schedule.getFleetId())
                            || start.isBefore(periodStart) || end.isAfter(periodEnd)) {
                        return Mono.<Void>error(PlanningException.scheduleMismatch());
                    }
                    return Mono.<Void>empty();
                });
    }
}
