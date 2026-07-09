package com.yowyob.fleet.domain.ports.in;

import com.yowyob.fleet.domain.model.Driver;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.DriverRegistrationRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

// Remplace le contenu de ManageDriverUseCase par ceci
public interface ManageDriverUseCase {
    Mono<Driver> registerDriver(UUID fleetId, DriverRegistrationRequest request, UUID managerId);
    
    // --- AJOUTS TACHE 8 ---
    Mono<Driver> registerDriverWithPhoto(UUID fleetId, DriverRegistrationRequest request, UUID managerId, AuthUseCase.FileContent photo);
    Flux<Driver> getDriversWithFilters(UUID fleetId, Boolean isAssigned, UUID requesterId);
    Flux<com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.DriverResponse> getDriversEnriched(UUID fleetId, Boolean isAssigned, UUID requesterId);
    Mono<com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.DriverResponse> getDriverEnriched(UUID userId);
    Mono<Driver> searchDriver(String identifier);
    // ----------------------

    Mono<Void> recruitDriver(UUID fleetId, String identifier, UUID managerId, String token);
    Mono<Driver> getDriverById(UUID userId);
    Flux<Driver> getDrivers(UUID fleetId, UUID requesterId, boolean isAdmin);
    Mono<Void> assignVehicle(UUID userId, UUID vehicleId, UUID requesterId, String token);
    Mono<Void> unassignVehicle(UUID userId, UUID requesterId);
    Mono<Void> removeDriverFromFleet(UUID fleetId, UUID driverId, UUID requesterId);

    Mono<com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.DriverResponse> createDriverForManager(
            com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.ManagerDriverCreateRequest request,
            UUID managerId);

    Mono<com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.DriverResponse> updateDriverForManager(
            UUID userId,
            com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.ManagerDriverUpdateRequest request,
            UUID managerId);
}