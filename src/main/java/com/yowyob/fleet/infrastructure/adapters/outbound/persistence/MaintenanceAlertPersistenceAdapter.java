package com.yowyob.fleet.infrastructure.adapters.outbound.persistence;

import com.yowyob.fleet.domain.model.MaintenanceAlert;
import com.yowyob.fleet.domain.model.MaintenancePlan;
import com.yowyob.fleet.domain.ports.out.MaintenanceAlertPersistencePort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.MaintenanceAlertEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.MaintenanceAlertR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MaintenanceAlertPersistenceAdapter implements MaintenanceAlertPersistencePort {

    private final MaintenanceAlertR2dbcRepository repository;

    @Override
    public Mono<MaintenanceAlert> save(MaintenanceAlert alert) {
        MaintenanceAlertEntity entity = toEntity(alert);
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID());
            entity.setNew(true);
        }
        return repository.save(entity).map(this::toDomain);
    }

    @Override
    public Mono<MaintenanceAlert> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Flux<MaintenanceAlert> findAll() {
        return repository.findAll().map(this::toDomain);
    }

    @Override
    public Flux<MaintenanceAlert> findActiveByManagerId(UUID managerId) {
        return repository.findActiveByManagerId(managerId).map(this::toDomain);
    }

    @Override
    public Flux<MaintenanceAlert> findUrgentByManagerId(UUID managerId) {
        return repository.findUrgentByManagerId(managerId).map(this::toDomain);
    }

    @Override
    public Flux<MaintenanceAlert> findByVehicleId(UUID vehicleId) {
        return repository.findByVehicleId(vehicleId).map(this::toDomain);
    }

    @Override
    public Flux<MaintenanceAlert> findActiveByFleetId(UUID fleetId) {
        return repository.findActiveByFleetId(fleetId).map(this::toDomain);
    }

    @Override
    public Mono<MaintenanceAlert> findActiveByVehicleAndType(UUID vehicleId, String maintenanceType) {
        return repository.findActiveByVehicleAndType(vehicleId, maintenanceType).map(this::toDomain);
    }

    @Override
    public Mono<Boolean> existsById(UUID id) {
        return repository.existsById(id);
    }

    @Override
    public Mono<Void> deleteById(UUID id) {
        return repository.deleteById(id);
    }

    private MaintenanceAlert toDomain(MaintenanceAlertEntity e) {
        MaintenancePlan.MaintenanceType mtype;
        try { mtype = MaintenancePlan.MaintenanceType.valueOf(e.getMaintenanceType()); }
        catch (IllegalArgumentException ex) { mtype = MaintenancePlan.MaintenanceType.OTHER; }

        MaintenanceAlert.AlertStatus status;
        try { status = MaintenanceAlert.AlertStatus.valueOf(e.getStatus()); }
        catch (IllegalArgumentException ex) { status = MaintenanceAlert.AlertStatus.UPCOMING; }

        MaintenanceAlert.TriggerType trigger;
        try { trigger = MaintenanceAlert.TriggerType.valueOf(e.getTriggerType()); }
        catch (IllegalArgumentException ex) { trigger = MaintenanceAlert.TriggerType.DATE; }

        return new MaintenanceAlert(
                e.getId(), e.getPlanId(), mtype,
                e.getVehicleId(), e.getVehicleRegistration(),
                e.getFleetId(), e.getManagerId(),
                status, trigger,
                e.getLastMaintenanceKm(), e.getTargetKm(),
                e.getCurrentKm(), e.getKmRemaining(),
                e.getLastMaintenanceDate(), e.getTargetDate(), e.getDaysRemaining(),
                e.getResolvedByMaintenanceId(), e.getResolvedAt(),
                e.getCreatedAt(), e.getUpdatedAt()
        );
    }

    private MaintenanceAlertEntity toEntity(MaintenanceAlert a) {
        MaintenanceAlertEntity e = new MaintenanceAlertEntity();
        e.setId(a.getId());
        e.setPlanId(a.getPlanId());
        e.setMaintenanceType(a.getMaintenanceType() != null ? a.getMaintenanceType().name() : null);
        e.setVehicleId(a.getVehicleId());
        e.setVehicleRegistration(a.getVehicleRegistration());
        e.setFleetId(a.getFleetId());
        e.setManagerId(a.getManagerId());
        e.setStatus(a.getStatus() != null ? a.getStatus().name() : MaintenanceAlert.AlertStatus.UPCOMING.name());
        e.setTriggerType(a.getTriggerType() != null ? a.getTriggerType().name() : null);
        e.setLastMaintenanceKm(a.getLastMaintenanceKm());
        e.setTargetKm(a.getTargetKm());
        e.setCurrentKm(a.getCurrentKm());
        e.setKmRemaining(a.getKmRemaining());
        e.setLastMaintenanceDate(a.getLastMaintenanceDate());
        e.setTargetDate(a.getTargetDate());
        e.setDaysRemaining(a.getDaysRemaining());
        e.setResolvedByMaintenanceId(a.getResolvedByMaintenanceId());
        e.setResolvedAt(a.getResolvedAt());
        e.setCreatedAt(a.getCreatedAt() != null ? a.getCreatedAt() : LocalDateTime.now());
        e.setUpdatedAt(a.getUpdatedAt() != null ? a.getUpdatedAt() : LocalDateTime.now());
        return e;
    }
}
