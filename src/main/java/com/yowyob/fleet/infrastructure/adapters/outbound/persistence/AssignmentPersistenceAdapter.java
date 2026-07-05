package com.yowyob.fleet.infrastructure.adapters.outbound.persistence;

import com.yowyob.fleet.domain.model.Assignment;
import com.yowyob.fleet.domain.ports.out.AssignmentPersistencePort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.AssignmentEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.AssignmentR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AssignmentPersistenceAdapter implements AssignmentPersistencePort {

    private final AssignmentR2dbcRepository repository;

    @Override
    public Mono<Assignment> save(Assignment assignment) {
        AssignmentEntity entity = toEntity(assignment);
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID());
            entity.setNew(true);
        }
        return repository.save(entity).map(this::toDomain);
    }

    @Override
    public Mono<Assignment> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Flux<Assignment> findAllByManagerId(UUID managerId) {
        return repository.findAllByManagerId(managerId).map(this::toDomain);
    }

    @Override
    public Flux<Assignment> findAllByScheduleId(UUID scheduleId) {
        return repository.findByScheduleId(scheduleId).map(this::toDomain);
    }

    @Override
    public Flux<Assignment> findByVehicleId(UUID vehicleId) {
        return repository.findByVehicleId(vehicleId).map(this::toDomain);
    }

    @Override
    public Flux<Assignment> findByDriverId(UUID driverId) {
        return repository.findByDriverId(driverId).map(this::toDomain);
    }

    @Override
    public Flux<Assignment> findByDriverIdAndDate(UUID driverId, LocalDate date) {
        return repository.findByDriverIdAndDate(driverId, date).map(this::toDomain);
    }

    @Override
    public Flux<Assignment> findByDateRange(UUID managerId, LocalDate start, LocalDate end) {
        return repository.findByDateRangeAndManagerId(managerId, start, end).map(this::toDomain);
    }

    @Override
    public Flux<Assignment> findConflictingByVehicle(UUID vehicleId,
                                                      LocalDateTime start,
                                                      LocalDateTime end,
                                                      UUID excludeId) {
        return repository.findConflictingByVehicle(vehicleId, start, end, excludeId)
                .map(this::toDomain);
    }

    @Override
    public Flux<Assignment> findConflictingByDriver(UUID driverId,
                                                     LocalDateTime start,
                                                     LocalDateTime end,
                                                     UUID excludeId) {
        return repository.findConflictingByDriver(driverId, start, end, excludeId)
                .map(this::toDomain);
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

    private Assignment toDomain(AssignmentEntity e) {
        return new Assignment(
                e.getId(),
                e.getScheduleId(),
                e.getFleetId(),
                e.getVehicleId(),
                e.getDriverId(),
                e.getMissionId(),
                e.getStartDatetime(),
                e.getEndDatetime(),
                e.getStatus() != null
                        ? Assignment.Status.valueOf(e.getStatus())
                        : Assignment.Status.PENDING,
                e.getStartLocation(),
                e.getEndLocation(),
                e.getEstimatedKm(),
                e.getActualKm(),
                e.getNotes(),
                e.getCreatedAt()
        );
    }

    private AssignmentEntity toEntity(Assignment a) {
        AssignmentEntity e = new AssignmentEntity();
        e.setId(a.getId());
        e.setScheduleId(a.getScheduleId());
        e.setFleetId(a.getFleetId());
        e.setVehicleId(a.getVehicleId());
        e.setDriverId(a.getDriverId());
        e.setMissionId(a.getMissionId());
        e.setStartDatetime(a.getStartDatetime());
        e.setEndDatetime(a.getEndDatetime());
        e.setStatus(a.getStatus().name());
        e.setStartLocation(a.getStartLocation());
        e.setEndLocation(a.getEndLocation());
        e.setEstimatedKm(a.getEstimatedKm());
        e.setActualKm(a.getActualKm());
        e.setNotes(a.getNotes());
        e.setCreatedAt(a.getCreatedAt() != null ? a.getCreatedAt() : LocalDateTime.now());
        e.setUpdatedAt(LocalDateTime.now());
        return e;
    }
}
