package com.yowyob.fleet.infrastructure.adapters.outbound.persistence;

import com.yowyob.fleet.domain.model.MaintenancePlan;
import com.yowyob.fleet.domain.ports.out.MaintenancePlanPersistencePort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.MaintenancePlanEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.MaintenancePlanR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MaintenancePlanPersistenceAdapter implements MaintenancePlanPersistencePort {

    private final MaintenancePlanR2dbcRepository repository;

    @Override
    public Mono<MaintenancePlan> save(MaintenancePlan plan) {
        MaintenancePlanEntity entity = toEntity(plan);
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID());
            entity.setNew(true);
        }
        return repository.save(entity).map(this::toDomain);
    }

    @Override
    public Mono<MaintenancePlan> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Flux<MaintenancePlan> findAll() {
        return repository.findAll().map(this::toDomain);
    }

    @Override
    public Flux<MaintenancePlan> findByFleetId(UUID fleetId) {
        return repository.findByFleetId(fleetId).map(this::toDomain);
    }

    @Override
    public Flux<MaintenancePlan> findByVehicleId(UUID vehicleId) {
        return repository.findByVehicleId(vehicleId).map(this::toDomain);
    }

    @Override
    public Flux<MaintenancePlan> findByManagerId(UUID managerId) {
        return repository.findByManagerId(managerId).map(this::toDomain);
    }

    @Override
    public Flux<MaintenancePlan> findActiveByFleetId(UUID fleetId) {
        return repository.findActiveByFleetId(fleetId).map(this::toDomain);
    }

    @Override
    public Mono<MaintenancePlan> findEffectivePlan(UUID vehicleId, UUID fleetId,
                                                    MaintenancePlan.MaintenanceType type) {
        return repository.findEffectivePlan(vehicleId, fleetId, type.name()).map(this::toDomain);
    }

    @Override
    public Mono<Boolean> existsById(UUID id) {
        return repository.existsById(id);
    }

    @Override
    public Mono<Void> deleteById(UUID id) {
        return repository.deleteById(id);
    }

    private MaintenancePlan toDomain(MaintenancePlanEntity e) {
        MaintenancePlan.MaintenanceType type;
        try { type = MaintenancePlan.MaintenanceType.valueOf(e.getMaintenanceType()); }
        catch (IllegalArgumentException ex) { type = MaintenancePlan.MaintenanceType.OTHER; }

        MaintenancePlan.PlanScope scope;
        try { scope = MaintenancePlan.PlanScope.valueOf(e.getScope()); }
        catch (IllegalArgumentException ex) { scope = MaintenancePlan.PlanScope.FLEET; }

        return new MaintenancePlan(
                e.getId(), type, scope,
                e.getFleetId(), e.getVehicleId(), e.getManagerId(),
                e.getLabel(), e.getDescription(),
                e.getIntervalKm(), e.getIntervalDays(),
                e.getPreAlertKm(), e.getPreAlertDays(),
                e.isActive(), e.getCreatedAt(), e.getUpdatedAt()
        );
    }

    private MaintenancePlanEntity toEntity(MaintenancePlan p) {
        MaintenancePlanEntity e = new MaintenancePlanEntity();
        e.setId(p.getId());
        e.setMaintenanceType(p.getMaintenanceType().name());
        e.setScope(p.getScope().name());
        e.setFleetId(p.getFleetId());
        e.setVehicleId(p.getVehicleId());
        e.setManagerId(p.getManagerId());
        e.setLabel(p.getLabel());
        e.setDescription(p.getDescription());
        e.setIntervalKm(p.getIntervalKm());
        e.setIntervalDays(p.getIntervalDays());
        e.setPreAlertKm(p.getPreAlertKm());
        e.setPreAlertDays(p.getPreAlertDays());
        e.setActive(p.isActive());
        e.setCreatedAt(p.getCreatedAt() != null ? p.getCreatedAt() : LocalDateTime.now());
        e.setUpdatedAt(p.getUpdatedAt() != null ? p.getUpdatedAt() : LocalDateTime.now());
        return e;
    }
}
