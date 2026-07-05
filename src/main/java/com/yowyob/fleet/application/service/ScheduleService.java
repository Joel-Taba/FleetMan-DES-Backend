package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.PlanningException;
import com.yowyob.fleet.domain.model.Schedule;
import com.yowyob.fleet.domain.ports.in.ManageScheduleUseCase;
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
                .doOnSuccess(s -> log.info("Planning publié : {}", s.getId()));
    }

    @Override
    public Mono<Schedule> archive(UUID id) {
        return schedulePersistencePort.findById(id)
                .switchIfEmpty(Mono.error(PlanningException.scheduleNotFound(id)))
                .flatMap(schedule -> {
                    schedule.archive();
                    return schedulePersistencePort.save(schedule);
                });
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
