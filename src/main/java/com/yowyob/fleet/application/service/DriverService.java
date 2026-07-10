package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.model.Driver;
import com.yowyob.fleet.domain.model.Vehicle;
import com.yowyob.fleet.domain.ports.in.AuthUseCase;
import com.yowyob.fleet.domain.ports.in.ManageDriverUseCase;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.domain.ports.out.DriverPersistencePort;
import com.yowyob.fleet.domain.ports.out.ExternalVehiclePort;
import com.yowyob.fleet.domain.ports.out.VehiclePersistencePort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.DriverRegistrationRequest;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.NotificationType;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.FleetR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.UserLocalR2dbcRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.security.SecurityProperties.User;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.yowyob.fleet.domain.ports.out.SendNotificationPort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.SendNotificationRequest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DriverService implements ManageDriverUseCase {

    private final DriverPersistencePort driverPersistencePort;
    private final VehiclePersistencePort vehiclePersistencePort;
    private final AuthPort authPort;
    private final FleetR2dbcRepository fleetRepository;
    private final ExternalVehiclePort externalVehiclePort;
    private final UserLocalR2dbcRepository userRepo; // Repository pour accéder aux données utilisateur locales

    private static final String SERVICE_NAME = "FLEET_MANAGEMENT";

    // --- 1. CRÉATION COMPLÈTE ---
    @Override
    public Mono<Driver> registerDriver(UUID fleetId, DriverRegistrationRequest request, UUID managerId) {
        return checkFleetOwnership(fleetId, managerId)
                .then(Mono.defer(() -> {
                    AuthUseCase.RegisterCommand command = new AuthUseCase.RegisterCommand(
                            request.username(), request.password(), request.email(), request.phone(),
                            request.firstName(), request.lastName(), List.of("FLEET_DRIVER"), null);
                    return authPort.registerInRemote(command);
                }))
                .flatMap(authRes -> {
                    Driver localDriver = new Driver(
                            authRes.user().id(),
                            fleetId,
                            request.licenceNumber(),
                            "ACTIVE",
                            null,
                            null);
                    return driverPersistencePort.save(localDriver);
                });
    }

    // --- 2. RECRUTEMENT ---
    @Override
    public Mono<Void> recruitDriver(UUID fleetId, String identifier, UUID managerId, String token) {
        return checkFleetOwnership(fleetId, managerId)
                .thenMany(authPort.getUsersByService(SERVICE_NAME, token))
                .filter(u -> isMatch(u, identifier))
                .filter(u -> u.roles().contains("FLEET_DRIVER"))
                .next()
                .switchIfEmpty(
                        Mono.error(new RuntimeException("Aucun chauffeur trouvé avec cet identifiant : " + identifier)))
                .flatMap(user -> driverPersistencePort.updateFleetAssignment(user.id(), fleetId));
    }

    private boolean isMatch(AuthPort.UserDetail user, String id) {
        return id.equalsIgnoreCase(user.email()) ||
                id.equalsIgnoreCase(user.username()) ||
                id.equals(user.phone());
    }

    // --- 3. LECTURE ---
    @Override
    public Flux<Driver> getDrivers(UUID fleetId, UUID requesterId, boolean isAdmin) {
        if (isAdmin) {
            return fleetId != null ? driverPersistencePort.findAllByFleetId(fleetId) : driverPersistencePort.findAll();
        }
        if (fleetId == null) {
            return Flux.error(new IllegalArgumentException("fleetId obligatoire pour les managers"));
        }
        return checkFleetOwnership(fleetId, requesterId)
                .thenMany(driverPersistencePort.findAllByFleetId(fleetId));
    }

    @Override
    public Mono<Driver> getDriverById(UUID userId) {
        return driverPersistencePort.findById(userId);
    }

    // --- 4. GESTION LIENS ---
    @Override
    public Mono<Void> removeDriverFromFleet(UUID fleetId, UUID driverId, UUID requesterId) {
        return checkFleetOwnership(fleetId, requesterId)
                .then(unassignVehicle(driverId, requesterId))
                .then(driverPersistencePort.updateFleetAssignment(driverId, null));
    }

    @Override
    @Transactional
    public Mono<Void> assignVehicle(UUID driverId, UUID targetVehicleId, UUID requesterId, String token) {
        return vehiclePersistencePort.getLocalDataById(targetVehicleId)
                .switchIfEmpty(Mono.error(new RuntimeException("Véhicule introuvable")))
                .flatMap(vehicle -> checkVehicleOwnership(vehicle, requesterId).thenReturn(vehicle))
                .flatMap(targetVehicle -> {

                    // 1. Nettoyage local (Driver précédent, etc.)
                    Mono<Void> clearOldVehicleOfDriver = driverPersistencePort.findById(driverId)
                            .flatMap(driver -> {
                                if (driver.assignedVehicleId() != null
                                        && !driver.assignedVehicleId().equals(targetVehicleId)) {
                                    return updateVehicleLink(driver.assignedVehicleId(), null);
                                }
                                return Mono.empty();
                            });

                    Mono<Void> clearOldDriverOfVehicle = driverPersistencePort.findByAssignedVehicleId(targetVehicleId)
                            .flatMap(oldDriver -> {
                                if (!oldDriver.userId().equals(driverId)) {
                                    return driverPersistencePort.updateVehicleAssignment(oldDriver.userId(), null);
                                }
                                return Mono.empty();
                            });

                    // 2. Synchronisation Distante (External API)
                    // On appelle l'API pour dire "Ce chauffeur conduit maintenant ce véhicule"
                    Mono<Void> remoteSync = externalVehiclePort.assignDriverRemote(targetVehicleId, driverId, token);

                    // 3. Exécution chainée
                    return clearOldVehicleOfDriver
                            .then(clearOldDriverOfVehicle)
                            .then(remoteSync) // <--- Appel distant ici
                            .then(updateVehicleLink(targetVehicleId, driverId))
                            .then(driverPersistencePort.updateVehicleAssignment(driverId, targetVehicleId));
                });
    }

    @Override
    @Transactional
    public Mono<Void> unassignVehicle(UUID driverId, UUID requesterId) {
        return driverPersistencePort.findById(driverId)
                .flatMap(driver -> {
                    if (driver.assignedVehicleId() == null)
                        return Mono.empty();
                    return updateVehicleLink(driver.assignedVehicleId(), null)
                            .then(driverPersistencePort.updateVehicleAssignment(driverId, null));
                });
    }

    // ... (ajouter les nouvelles méthodes à l'existant)

    public Mono<Driver> registerDriverWithPhoto(UUID fleetId, DriverRegistrationRequest request, UUID managerId,
            AuthUseCase.FileContent photo) {
        return checkFleetOwnership(fleetId, managerId)
                .then(Mono.defer(() -> {
                    // 1. Création Auth avec Photo
                    AuthUseCase.RegisterCommand cmd = new AuthUseCase.RegisterCommand(
                            request.username(), request.password(), request.email(), request.phone(),
                            request.firstName(), request.lastName(), List.of("FLEET_DRIVER"), photo);
                    return authPort.registerInRemote(cmd);
                }))
                .flatMap(authRes -> {
                    // 2. Profil local
                    Driver d = new Driver(authRes.user().id(), fleetId, request.licenceNumber(), "ACTIVE", null,
                            authRes.user().photoUrl());
                    return driverPersistencePort.save(d);
                });
    }

    public Flux<Driver> getDriversWithFilters(UUID fleetId, Boolean isAssigned, UUID requesterId) {
        // Listing de base (par flotte ou tous si admin)
        Flux<Driver> drivers = (fleetId != null) ? driverPersistencePort.findAllByFleetId(fleetId)
                : driverPersistencePort.findAll();

        // Filtre applicatif sur l'assignation
        if (isAssigned != null) {
            drivers = drivers.filter(d -> (isAssigned ? d.assignedVehicleId() != null : d.assignedVehicleId() == null));
        }
        return drivers;
    }

    @Override
    public Flux<com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.DriverResponse> getDriversEnriched(
            UUID fleetId, Boolean isAssigned, UUID requesterId) {
        return getDriversWithFilters(fleetId, isAssigned, requesterId).flatMap(this::enrichDriver);
    }

    @Override
    public Mono<com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.DriverResponse> getDriverEnriched(
            UUID userId) {
        return driverPersistencePort.findById(userId)
                .switchIfEmpty(Mono.error(new RuntimeException("Conducteur introuvable")))
                .flatMap(this::enrichDriver);
    }

    private Mono<com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.DriverResponse> enrichDriver(Driver driver) {
        return userRepo.findById(driver.userId())
                .map(user -> com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.DriverResponse.from(driver,
                        user))
                .defaultIfEmpty(
                        com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.DriverResponse.from(driver, null));
    }

    public Mono<Driver> searchDriver(String identifier) {
        // On utilise le repository UserLocal pour trouver l'ID via email/username
        return userRepo.findByUsername(identifier)
                .switchIfEmpty(userRepo.findAll().filter(u -> identifier.equalsIgnoreCase(u.getEmail())).next())
                .flatMap(user -> driverPersistencePort.findById(user.getId()));
    }

    // Helper mis à jour avec le nouveau constructeur Vehicle (23 champs)

    private Mono<Void> updateVehicleLink(UUID vehicleId, UUID driverId) {
        return vehiclePersistencePort.getLocalDataById(vehicleId)
                .flatMap(v -> {
                    Vehicle updated = new Vehicle(
                            v.id(),
                            v.fleetId(),
                            v.managerId(),
                            driverId, // Seul champ modifié
                            v.vehicleTypeId(),
                            v.licensePlate(),
                            v.vehicleSerialNumber(),
                            v.brand(),
                            v.model(),
                            v.manufacturingYear(),
                            v.transmissionType(),
                            v.fuelType(),
                            v.tankCapacity(),
                            v.totalSeatNumber(),
                            v.averageFuelConsumption(),
                            v.color(),
                            v.status(),
                            v.photoUrl(),
                            v.serialNumberPhotoUrl(),
                            v.registrationPhotoUrl(),
                            v.illustrationImages(),
                            v.financialParameters(),
                            v.maintenanceParameters(),
                            v.operationalParameters(),
                            v.geofenceRemoteId());
                    return vehiclePersistencePort.saveLocalData(updated);
                }).then();
    }

    private Mono<Void> checkVehicleOwnership(Vehicle vehicle, UUID requesterId) {
        if (vehicle.fleetId() != null) {
            return checkFleetOwnership(vehicle.fleetId(), requesterId);
        }
        if (vehicle.managerId() != null && vehicle.managerId().equals(requesterId)) {
            return Mono.empty();
        }
        return Mono.error(new AccessDeniedException("Ce véhicule ne vous appartient pas."));
    }

    private Mono<Void> checkFleetOwnership(UUID fleetId, UUID managerId) {
        log.info("🔍 [FLEET_OWNERSHIP] Vérification : fleetId={}, managerId={}", fleetId, managerId);
        return fleetRepository.existsByIdAndManagerId(fleetId, managerId)
                .flatMap(exists -> {
                    log.info("🔍 [FLEET_OWNERSHIP] Résultat existance : {} (fleetId={}, managerId={})", exists, fleetId,
                            managerId);
                    if (!exists)
                        return Mono.error(new AccessDeniedException("Cette flotte ne vous appartient pas."));
                    return Mono.empty();
                });
    }
}