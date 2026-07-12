package com.yowyob.fleet.domain.ports.out;

import com.yowyob.fleet.domain.model.Driver;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface DriverPersistencePort {
    Mono<Driver> save(Driver driver);

    Mono<Driver> findById(UUID userId);

    Mono<Driver> findByAssignedVehicleId(UUID vehicleId);

    Flux<Driver> findAllByFleetId(UUID fleetId);

    Mono<Void> updateVehicleAssignment(UUID userId, UUID vehicleId);

    Mono<Void> updateFleetAssignment(UUID driverId, UUID fleetId);

    Flux<Driver> findAll();

    Flux<Driver> findAllBySameCompanyAsUser(UUID userId);
}