package com.yowyob.fleet.application.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.fleet.domain.model.Vehicle;
import com.yowyob.fleet.domain.ports.in.ManageDriverUseCase;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.FleetResponse;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.sync.DeletedEntityRef;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.sync.SyncChange;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.DriverEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.FleetEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.VehicleLocalEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.DriverR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.FleetR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.VehicleLocalR2dbcRepository;
import com.yowyob.fleet.infrastructure.mappers.FleetMapper;
import com.yowyob.fleet.infrastructure.mappers.VehicleLocalMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SyncDeltaService {

    private final FleetR2dbcRepository fleetRepository;
    private final VehicleLocalR2dbcRepository vehicleRepository;
    private final DriverR2dbcRepository driverRepository;
    private final FleetMapper fleetMapper;
    private final VehicleLocalMapper vehicleMapper;
    private final ManageDriverUseCase driverUseCase;
    private final ObjectMapper objectMapper;

    public record DeltaPullResult(List<SyncChange> changes, List<DeletedEntityRef> deleted) {}

    public Mono<DeltaPullResult> pullManagerDelta(UUID managerId, Instant since) {
        Flux<SyncChange> fleetChanges = fleetRepository.findActiveByManagerIdUpdatedAfter(managerId, since)
                .map(this::fleetChange);

        Flux<SyncChange> vehicleChanges = vehicleRepository.findActiveByManagerIdUpdatedAfter(managerId, since)
                .map(this::vehicleChange);

        Flux<SyncChange> driverChanges = driverRepository.findActiveByManagerIdUpdatedAfter(managerId, since)
                .flatMap(entity -> driverUseCase.getDriverEnriched(entity.getUserId())
                        .map(driver -> new SyncChange(
                                "driver",
                                driver.userId(),
                                entity.getUpdatedAt() != null ? entity.getUpdatedAt() : Instant.now(),
                                asMap(driver)
                        )));

        Flux<DeletedEntityRef> deleted = Flux.merge(
                fleetRepository.findDeletedByManagerIdSince(managerId, since)
                        .map(entity -> deletedRef("fleet", entity.getId(), entity.getDeletedAt())),
                vehicleRepository.findDeletedByManagerIdSince(managerId, since)
                        .map(entity -> deletedRef("vehicle", entity.getId(), entity.getDeletedAt())),
                driverRepository.findDeletedByManagerIdSince(managerId, since)
                        .map(entity -> deletedRef("driver", entity.getUserId(), entity.getDeletedAt()))
        );

        return Flux.merge(fleetChanges, vehicleChanges, driverChanges)
                .collectList()
                .zipWith(deleted.collectList())
                .map(tuple -> new DeltaPullResult(tuple.getT1(), tuple.getT2()));
    }

    private SyncChange fleetChange(FleetEntity entity) {
        FleetResponse response = fleetMapper.toResponse(fleetMapper.toDomain(entity));
        Instant updatedAt = entity.getUpdatedAt() != null ? entity.getUpdatedAt() : Instant.now();
        return new SyncChange("fleet", entity.getId(), updatedAt, asMap(response));
    }

    private SyncChange vehicleChange(VehicleLocalEntity entity) {
        Vehicle vehicle = vehicleMapper.toDomain(entity, null, null, null);
        Instant updatedAt = entity.getUpdatedAt() != null ? entity.getUpdatedAt() : Instant.now();
        return new SyncChange("vehicle", entity.getId(), updatedAt, asMap(vehicle));
    }

    private DeletedEntityRef deletedRef(String entityType, UUID entityId, Instant deletedAt) {
        return new DeletedEntityRef(entityType, entityId, deletedAt != null ? deletedAt : Instant.now());
    }

    private Map<String, Object> asMap(Object payload) {
        if (payload == null) {
            return Map.of();
        }
        return objectMapper.convertValue(payload, new TypeReference<Map<String, Object>>() {});
    }
}
