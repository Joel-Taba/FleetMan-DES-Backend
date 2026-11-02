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
    Flux<Driver> findAllByManagerId(UUID managerId);
    Flux<Driver> findAllByOrganizationAdminId(UUID adminId);
    Mono<Void> updateVehicleAssignment(UUID userId, UUID vehicleId);
    Mono<Void> updateFleetAssignment(UUID driverId, UUID fleetId);
    Flux<Driver> findAll();
    Mono<Void> softDelete(UUID userId);

    /** Statut contractuel (ACTIVE / LEFT), date d'embauche et départ. */
    Mono<Void> updateEmployment(UUID userId, String status, java.time.LocalDate hireDate,
                                java.time.LocalDate leftAt, String leftReason);
}
