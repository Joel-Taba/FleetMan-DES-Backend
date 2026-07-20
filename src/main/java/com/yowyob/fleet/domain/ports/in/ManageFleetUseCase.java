package com.yowyob.fleet.domain.ports.in;

import com.yowyob.fleet.domain.model.Driver;
import com.yowyob.fleet.domain.model.Fleet;
import com.yowyob.fleet.domain.model.Vehicle;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.DriverRegistrationRequest;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.FleetStatsResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Port d'entrée pour la gestion intégrale des flottes (Module 10).
 * Orchestre les Flottes, leur Parc et leurs Chauffeurs.
 */
public interface ManageFleetUseCase {

    // --- 10a. ADMINISTRATION CRUD ---
    Mono<Fleet> createFleet(Fleet fleet, UUID managerId);
    /** Création par un administrateur : managerId optionnel (flotte assignée plus tard). */
    Mono<Fleet> createFleetAsAdmin(Fleet fleet, UUID managerId);
    Mono<Fleet> getFleetById(UUID fleetId, UUID requesterId, boolean isAdmin);
    Flux<Fleet> getFleets(UUID requesterId, boolean isAdmin);
    Mono<Fleet> updateFleet(UUID fleetId, Fleet fleet, UUID requesterId, boolean isAdmin);
    Mono<Void> deleteFleet(UUID fleetId, UUID requesterId, boolean isAdmin);
    Mono<FleetStatsResponse> getFleetStatistics(UUID fleetId, UUID requesterId, boolean isAdmin);
    /** Assigne (ou réassigne) une ou plusieurs flottes existantes à un gestionnaire. */
    Mono<Void> assignFleetsToManager(java.util.List<UUID> fleetIds, UUID managerId);

    // --- 10b. GESTION DU PARC (VEHICULES) ---
    Flux<Vehicle> getFleetVehicles(UUID fleetId, UUID requesterId);
    Mono<Void> assignVehicle(UUID fleetId, UUID vehicleId, UUID requesterId);
    Mono<Void> detachVehicle(UUID fleetId, UUID vehicleId, UUID requesterId);

    // --- 10c. GESTION DES CHAUFFEURS ---
    Flux<Driver> getFleetDrivers(UUID fleetId, UUID requesterId);
    Mono<Void> recruitDriver(UUID fleetId, String identifier, UUID managerId);
    Mono<Driver> registerDriverInFleet(UUID fleetId, DriverRegistrationRequest request, UUID managerId);
    Mono<Void> detachDriver(UUID fleetId, UUID driverId, UUID requesterId);
}