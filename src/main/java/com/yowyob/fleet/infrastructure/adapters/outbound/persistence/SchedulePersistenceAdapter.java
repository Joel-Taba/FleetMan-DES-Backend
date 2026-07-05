package com.yowyob.fleet.infrastructure.adapters.outbound.persistence;

import com.yowyob.fleet.domain.model.Schedule;
import com.yowyob.fleet.domain.ports.out.SchedulePersistencePort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.ScheduleEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.ScheduleR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SchedulePersistenceAdapter implements SchedulePersistencePort {

    private final ScheduleR2dbcRepository repository;

    @Override
    public Mono<Schedule> save(Schedule schedule) {
        ScheduleEntity entity = toEntity(schedule);
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID());
            entity.setNew(true);
        }
        return repository.save(entity).map(this::toDomain);
    }

    @Override
    public Mono<Schedule> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Flux<Schedule> findAllByManagerId(UUID managerId) {
        return repository.findAllByManagerId(managerId).map(this::toDomain);
    }

    @Override
    public Flux<Schedule> findAllByFleetId(UUID fleetId) {
        return repository.findByFleetId(fleetId).map(this::toDomain);
    }

    @Override
    public Flux<Schedule> findByPeriod(UUID managerId, LocalDate start, LocalDate end) {
        return repository.findByPeriodAndManagerId(managerId, start, end).map(this::toDomain);
    }

    @Override
    public Mono<Boolean> existsById(UUID id) {
        return repository.existsById(id);
    }

    @Override
    public Mono<Void> deleteById(UUID id) {
        return repository.deleteById(id);
    }

    // ── Conversion Entity ↔ Domain ────────────────────────────────────────────

    private Schedule toDomain(ScheduleEntity e) {
        return new Schedule(
                e.getId(),
                e.getFleetId(),
                e.getManagerId(),
                e.getTitle(),
                Schedule.PeriodType.valueOf(e.getPeriodType()),
                e.getStartDate(),
                e.getEndDate(),
                Schedule.Status.valueOf(e.getStatus()),
                e.getNotes(),
                e.getCreatedAt(),
                e.getCreatedBy()
        );
    }

    private ScheduleEntity toEntity(Schedule s) {
        ScheduleEntity e = new ScheduleEntity();
        e.setId(s.getId());
        e.setFleetId(s.getFleetId());
        e.setManagerId(s.getManagerId());
        e.setTitle(s.getTitle());
        e.setPeriodType(s.getPeriodType().name());
        e.setStartDate(s.getStartDate());
        e.setEndDate(s.getEndDate());
        e.setStatus(s.getStatus().name());
        e.setNotes(s.getNotes());
        e.setCreatedAt(s.getCreatedAt() != null ? s.getCreatedAt() : LocalDateTime.now());
        e.setUpdatedAt(LocalDateTime.now());
        e.setCreatedBy(s.getCreatedBy());
        return e;
    }
}
