package com.yowyob.fleet.infrastructure.adapters.outbound.persistence;

import com.yowyob.fleet.domain.model.Coordinates;
import com.yowyob.fleet.domain.model.Maintenance;
import com.yowyob.fleet.domain.ports.out.MaintenancePersistencePort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.MaintenanceEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.MaintenanceR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Adapter de persistance pour les Maintenances.
 * Implémente MaintenancePersistencePort via Spring Data R2DBC.
 * Assure la conversion Entity ↔ Domain.
 */
@Component
@RequiredArgsConstructor
public class MaintenancePersistenceAdapter implements MaintenancePersistencePort {

    private final MaintenanceR2dbcRepository repository;

    @Override
    public Mono<Maintenance> save(Maintenance maintenance) {
        MaintenanceEntity entity = toEntity(maintenance);
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID());
            entity.setNew(true);
        }
        return repository.save(entity).map(this::toDomain);
    }

    @Override
    public Mono<Maintenance> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Flux<Maintenance> findAll() {
        return repository.findAll().map(this::toDomain);
    }

    @Override
    public Flux<Maintenance> findAllByManagerId(UUID managerId) {
        return repository.findAllByManagerId(managerId).map(this::toDomain);
    }

    @Override
    public Flux<Maintenance> findByVehicleId(UUID vehicleId) {
        return repository.findByVehicleId(vehicleId).map(this::toDomain);
    }

    @Override
    public Flux<Maintenance> findByDriverId(UUID driverId) {
        return repository.findByDriverId(driverId).map(this::toDomain);
    }

    @Override
    public Flux<Maintenance> findByDateRange(LocalDateTime start, LocalDateTime end) {
        return repository.findByDateRange(start, end).map(this::toDomain);
    }

    @Override
    public Mono<Long> countByDriverId(UUID driverId) {
        return repository.countByDriverId(driverId);
    }

    @Override
    public Mono<Boolean> existsById(UUID id) {
        return repository.existsById(id);
    }

    @Override
    public Mono<Void> deleteById(UUID id) {
        return repository.deleteById(id);
    }

    // ── Conversion Entity → Domain ────────────────────────────────────────────

    private Maintenance toDomain(MaintenanceEntity e) {
        Coordinates coords = (e.getLongitude() != null && e.getLatitude() != null)
                ? new Coordinates(e.getLongitude(), e.getLatitude())
                : null;

        return new Maintenance(
                e.getId(),
                e.getSubject(),
                e.getCost(),
                e.getDateTime(),
                e.getReport(),
                coords,
                e.getLocationName(),
                e.getVehicleId(),
                e.getVehicleRegistration(),
                e.getDriverId(),
                e.getDriverFullName()
        );
    }

    // ── Conversion Domain → Entity ────────────────────────────────────────────

    private MaintenanceEntity toEntity(Maintenance m) {
        MaintenanceEntity entity = new MaintenanceEntity();
        entity.setId(m.getId());
        entity.setSubject(m.getSubject());
        entity.setCost(m.getCost());
        entity.setDateTime(m.getDateTime());
        entity.setReport(m.getReport());
        entity.setLocationName(m.getLocationName());
        entity.setVehicleId(m.getVehicleId());
        entity.setVehicleRegistration(m.getVehicleRegistrationNumber());
        entity.setDriverId(m.getDriverId());
        entity.setDriverFullName(m.getDriverFullName());

        if (m.getLocation() != null) {
            entity.setLongitude(m.getLocation().longitude());
            entity.setLatitude(m.getLocation().latitude());
        }
        return entity;
    }
}
