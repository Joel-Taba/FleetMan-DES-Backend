package com.yowyob.fleet.infrastructure.adapters.outbound.persistence;

import com.yowyob.fleet.domain.model.Mission;
import com.yowyob.fleet.domain.ports.out.MissionPersistencePort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.MissionEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.MissionR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MissionPersistenceAdapter implements MissionPersistencePort {

    private final MissionR2dbcRepository repository;

    @Override
    public Mono<Mission> save(Mission mission) {
        MissionEntity entity = toEntity(mission);
        return repository.existsById(entity.getId())
                .flatMap(exists -> {
                    entity.setNew(!exists);
                    return repository.save(entity);
                })
                .map(this::toDomain);
    }

    @Override
    public Mono<Mission> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Flux<Mission> findAll() {
        return repository.findAll().map(this::toDomain);
    }

    @Override
    public Flux<Mission> findByManagerId(UUID fleetManagerId) {
        return repository.findByFleetManagerIdOrderByScheduledStartDesc(fleetManagerId).map(this::toDomain);
    }

    @Override
    public Flux<Mission> findByDriverId(UUID driverId) {
        return repository.findByDriverIdOrderByScheduledStartDesc(driverId).map(this::toDomain);
    }

    @Override
    public Flux<Mission> findByVehicleId(UUID vehicleId) {
        return repository.findByVehicleIdOrderByScheduledStartDesc(vehicleId).map(this::toDomain);
    }

    @Override
    public Flux<Mission> findByStatus(Mission.Status status, UUID fleetManagerId) {
        return repository.findByStatusAndFleetManagerIdOrderByScheduledStartDesc(status.name(), fleetManagerId)
                .map(this::toDomain);
    }

    @Override
    public Flux<Mission> findActiveMissions(UUID fleetManagerId) {
        return repository.findActiveMissions(fleetManagerId).map(this::toDomain);
    }

    @Override
    public Mono<Boolean> existsOverlappingMissionForVehicle(UUID vehicleId, LocalDateTime start, LocalDateTime end, UUID excludeMissionId) {
        return repository.existsOverlappingForVehicle(vehicleId, start, end, excludeMissionId)
                .defaultIfEmpty(false);
    }

    @Override
    public Mono<Boolean> existsOverlappingMissionForDriver(UUID driverId, LocalDateTime start, LocalDateTime end, UUID excludeMissionId) {
        return repository.existsOverlappingForDriver(driverId, start, end, excludeMissionId)
                .defaultIfEmpty(false);
    }

    @Override
    public Mono<Long> countByStatus(Mission.Status status, UUID fleetManagerId) {
        return repository.countByStatusAndFleetManagerId(status.name(), fleetManagerId);
    }

    @Override
    public Mono<Void> deleteById(UUID id) {
        return repository.deleteById(id);
    }

    // ── Mappings ──────────────────────────────────────────────────────────────

    private MissionEntity toEntity(Mission domain) {
        MissionEntity entity = new MissionEntity();
        entity.setId(domain.getId());
        entity.setVehicleId(domain.getVehicleId());
        entity.setDriverId(domain.getDriverId());
        entity.setFleetManagerId(domain.getFleetManagerId());
        entity.setTiiBnTickDeliveryId(domain.getTiiBnTickDeliveryId());
        entity.setTiiBnTickParcelId(domain.getTiiBnTickParcelId());
        entity.setStatus(domain.getStatus() != null ? domain.getStatus().name() : Mission.Status.PENDING.name());
        entity.setType(domain.getType() != null ? domain.getType().name() : Mission.Type.DELIVERY.name());
        entity.setScheduledStart(domain.getScheduledStart());
        entity.setScheduledEnd(domain.getScheduledEnd());
        entity.setActualStart(domain.getActualStart());
        entity.setActualEnd(domain.getActualEnd());
        entity.setStartLocationId(domain.getStartLocationId());
        entity.setEndLocationId(domain.getEndLocationId());
        entity.setGeofenceObjectId(domain.getGeofenceObjectId());
        entity.setNotes(domain.getNotes());
        entity.setCancelReason(domain.getCancelReason());
        entity.setCreatedAt(domain.getCreatedAt() != null ? domain.getCreatedAt() : LocalDateTime.now());
        entity.setTenantId(domain.getTenantId());
        return entity;
    }

    private Mission toDomain(MissionEntity entity) {
        Mission.Status status = entity.getStatus() != null ? Mission.Status.valueOf(entity.getStatus()) : Mission.Status.PENDING;
        Mission.Type type = entity.getType() != null ? Mission.Type.valueOf(entity.getType()) : Mission.Type.DELIVERY;

        return new Mission(
                entity.getId(),
                entity.getVehicleId(),
                entity.getDriverId(),
                entity.getFleetManagerId(),
                entity.getTiiBnTickDeliveryId(),
                entity.getTiiBnTickParcelId(),
                status,
                type,
                entity.getScheduledStart(),
                entity.getScheduledEnd(),
                entity.getActualStart(),
                entity.getActualEnd(),
                entity.getStartLocationId(),
                entity.getEndLocationId(),
                entity.getGeofenceObjectId(),
                entity.getNotes(),
                entity.getCancelReason(),
                entity.getCreatedAt(),
                entity.getTenantId()
        );
    }
}

