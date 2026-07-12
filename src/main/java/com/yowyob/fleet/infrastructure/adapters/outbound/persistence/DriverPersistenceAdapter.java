package com.yowyob.fleet.infrastructure.adapters.outbound.persistence;

import com.yowyob.fleet.domain.model.Driver;
import com.yowyob.fleet.domain.ports.out.DriverPersistencePort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.DriverEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.DriverR2dbcRepository;
import com.yowyob.fleet.infrastructure.mappers.DriverMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DriverPersistenceAdapter implements DriverPersistencePort {

    private final DriverR2dbcRepository repository;
    private final DriverMapper mapper;

    @Override
    public Mono<Driver> save(Driver driver) {
        return repository.findById(driver.userId())
                .map(existing -> {
                    existing.setLicenceNumber(driver.licenceNumber());
                    existing.setStatus(driver.status());
                    existing.setAssignedVehicleId(driver.assignedVehicleId());
                    existing.setFleetId(driver.fleetId());
                    return existing;
                })
                .switchIfEmpty(Mono.defer(() -> {
                    DriverEntity newEntity = mapper.toEntity(driver);
                    newEntity.markAsNew();
                    return Mono.just(newEntity);
                }))
                .flatMap(repository::save)
                .map(mapper::toDomain);
    }

    @Override
    public Mono<Void> updateVehicleAssignment(UUID userId, UUID vehicleId) {
        return repository.findById(userId)
                .flatMap(entity -> {
                    entity.setAssignedVehicleId(vehicleId);
                    return repository.save(entity);
                }).then();
    }

    @Override
    public Mono<Void> updateFleetAssignment(UUID driverId, UUID fleetId) {
        return repository.findById(driverId)
                .flatMap(entity -> {
                    entity.setFleetId(fleetId);
                    if (fleetId == null) {
                        entity.setAssignedVehicleId(null);
                    }
                    return repository.save(entity);
                }).then();
    }

    @Override
    public Mono<Driver> findById(UUID userId) {
        return repository.findById(userId).map(mapper::toDomain);
    }

    @Override
    public Mono<Driver> findByAssignedVehicleId(UUID vehicleId) {
        return repository.findByAssignedVehicleId(vehicleId).map(mapper::toDomain);
    }

    @Override
    public Flux<Driver> findAllByFleetId(UUID fleetId) {
        return repository.findByFleetId(fleetId).map(mapper::toDomain);
    }

    @Override
    public Flux<Driver> findAll() {
        return repository.findAll().map(mapper::toDomain);
    }

    @Override
    public Flux<Driver> findAllBySameCompanyAsUser(UUID userId) {
        return repository.findAllBySameCompanyAsUser(userId).map(mapper::toDomain);
    }
}