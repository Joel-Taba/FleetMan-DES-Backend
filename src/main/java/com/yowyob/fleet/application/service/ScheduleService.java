package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.PlanningException;
import com.yowyob.fleet.domain.model.Assignment;
import com.yowyob.fleet.domain.model.Schedule;
import com.yowyob.fleet.domain.ports.in.ManageScheduleUseCase;
import com.yowyob.fleet.domain.ports.out.AssignmentPersistencePort;
import com.yowyob.fleet.domain.ports.out.SchedulePersistencePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduleService implements ManageScheduleUseCase {

    private final SchedulePersistencePort schedulePersistencePort;
    private final AssignmentPersistencePort assignmentPersistencePort;
    private final org.springframework.r2dbc.core.DatabaseClient db;

    @Override
    public Mono<Schedule> createSchedule(CreateScheduleCommand cmd) {
        // Validation des dates
        if (cmd.endDate() != null && cmd.startDate() != null
                && cmd.endDate().isBefore(cmd.startDate())) {
            return Mono.error(PlanningException.invalidDates());
        }

        Schedule schedule = new Schedule(
                null,
                cmd.fleetId(),
                cmd.managerId(),
                cmd.title(),
                Schedule.PeriodType.valueOf(cmd.periodType()),
                cmd.startDate(),
                cmd.endDate(),
                Schedule.Status.DRAFT,
                cmd.notes(),
                null,
                cmd.managerId()
        );

        return schedulePersistencePort.save(schedule)
                .doOnSuccess(s -> log.info("Planning créé : {} [{}]", s.getTitle(), s.getId()));
    }

    @Override
    public Mono<Schedule> getById(UUID id) {
        return schedulePersistencePort.findById(id)
                .switchIfEmpty(Mono.error(PlanningException.scheduleNotFound(id)));
    }

    @Override
    public Flux<Schedule> getAllByManager(UUID managerId) {
        return schedulePersistencePort.findAllByManagerId(managerId);
    }

    @Override
    public Flux<Schedule> getAllByFleet(UUID fleetId) {
        return schedulePersistencePort.findAllByFleetId(fleetId);
    }

    @Override
    public Flux<Schedule> getByPeriod(UUID managerId, LocalDate start, LocalDate end) {
        return schedulePersistencePort.findByPeriod(managerId, start, end);
    }

    @Override
    public Mono<Schedule> update(UpdateScheduleCommand cmd) {
        return schedulePersistencePort.findById(cmd.scheduleId())
                .switchIfEmpty(Mono.error(PlanningException.scheduleNotFound(cmd.scheduleId())))
                .flatMap(schedule -> {
                    if (!schedule.isEditable()) {
                        return Mono.error(PlanningException.scheduleArchived(cmd.scheduleId()));
                    }
                    schedule.update(cmd.title(), cmd.notes());
                    return schedulePersistencePort.save(schedule);
                });
    }

    @Override
    public Mono<Schedule> publish(UUID id) {
        return schedulePersistencePort.findById(id)
                .switchIfEmpty(Mono.error(PlanningException.scheduleNotFound(id)))
                .flatMap(schedule -> {
                    schedule.publish();
                    return schedulePersistencePort.save(schedule);
                })
                .flatMap(saved -> notifyDrivers(saved.getId()).thenReturn(saved))
                .doOnSuccess(s -> log.info("Planning publié : {}", s.getId()));
    }

    /** Publication : chaque chauffeur du programme reçoit une notification avec ses postes. */
    private Mono<Long> notifyDrivers(UUID scheduleId) {
        return db.sql("""
                INSERT INTO fleet.notifications (id, user_id, title, message, type, is_read, created_at, data, tenant_id)
                SELECT gen_random_uuid(), x.driver_id, 'Programme publié',
                       'Le programme « ' || s.title || ' » est publié : ' || x.n || ' poste(s) vous sont attribués, '
                           || 'premier poste le ' || to_char(x.first_start, 'DD/MM/YYYY à HH24:MI') || '.',
                       'INFO', FALSE, :now,
                       json_build_object('type', 'SCHEDULE_PUBLISHED', 'scheduleId', s.id)::text, s.tenant_id
                FROM (SELECT driver_id, COUNT(*) AS n, MIN(start_datetime) AS first_start
                      FROM fleet.assignments
                      WHERE schedule_id = :scheduleId AND status IN ('PENDING', 'IN_PROGRESS')
                      GROUP BY driver_id) x
                JOIN fleet.schedules s ON s.id = :scheduleId
                JOIN fleet.users u ON u.id = x.driver_id
                """)
                .bind("scheduleId", scheduleId)
                .bind("now", java.time.LocalDateTime.now())
                .fetch().rowsUpdated();
    }

    @Override
    public Mono<Schedule> archive(UUID id) {
        return schedulePersistencePort.findById(id)
                .switchIfEmpty(Mono.error(PlanningException.scheduleNotFound(id)))
                // Archivage = « toutes affectations terminées » (doc §5.4) : un planning
                // archivé est figé, ses affectations encore actives deviendraient orphelines.
                .flatMap(schedule -> assignmentPersistencePort.findAllByScheduleId(id)
                        .any(Assignment::isActive)
                        .flatMap(hasActive -> {
                            if (Boolean.TRUE.equals(hasActive)) {
                                return Mono.<Schedule>error(PlanningException.scheduleHasActiveAssignments(id));
                            }
                            schedule.archive();
                            return schedulePersistencePort.save(schedule);
                        }));
    }

    @Override
    public Mono<Void> delete(UUID id) {
        return schedulePersistencePort.existsById(id)
                .flatMap(exists -> {
                    if (!exists) return Mono.error(PlanningException.scheduleNotFound(id));
                    return schedulePersistencePort.deleteById(id);
                });
    }
}
