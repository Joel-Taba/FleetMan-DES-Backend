package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.model.Driver;
import com.yowyob.fleet.domain.model.Vehicle;
import com.yowyob.fleet.domain.ports.in.AuthUseCase;
import com.yowyob.fleet.domain.ports.in.ManageDriverUseCase;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.domain.ports.out.DriverPersistencePort;
import com.yowyob.fleet.domain.ports.out.ExternalActorPort;
import com.yowyob.fleet.domain.ports.out.ExternalVehiclePort;
import com.yowyob.fleet.domain.ports.out.VehiclePersistencePort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.DriverRegistrationRequest;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.DriverResponse;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.ManagerDriverCreateRequest;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.ManagerDriverUpdateRequest;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.NotificationType;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.UserLocalEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.FleetR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.UserLocalR2dbcRepository;
import com.yowyob.fleet.infrastructure.config.bootstrap.DemoTestAccounts;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.SecurityProperties.User;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.yowyob.fleet.domain.ports.out.SendNotificationPort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.SendNotificationRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final ExternalActorPort externalActorPort;
    private final UserLocalR2dbcRepository userRepo;
    private final PlanLimitGuard planLimitGuard;

    private static final String SERVICE_NAME = "FLEET_MANAGEMENT";

    @Value("${application.kernel.tenant-id:}")
    private String defaultTenantId;

    // --- 1. CRÉATION COMPLÈTE ---
    @Override
    public Mono<Driver> registerDriver(UUID fleetId, DriverRegistrationRequest request, UUID managerId) {
        return planLimitGuard.assertCanCreateDriver(managerId)
                .then(checkFleetOwnership(fleetId, managerId))
                .then(Mono.defer(() -> {
                    AuthUseCase.RegisterCommand command = new AuthUseCase.RegisterCommand(
                            request.username(), request.password(), request.email(), request.phone(),
                            request.firstName(), request.lastName(), List.of("FLEET_DRIVER"), null);
                    return authPort.registerInRemote(command);
                }))
                .flatMap(authRes -> saveDriverWithKernelActor(
                        fleetId, request, authRes));
    }

    private Mono<Driver> saveDriverWithKernelActor(
            UUID fleetId, DriverRegistrationRequest request, AuthPort.AuthResponse authRes) {

        UUID kernelUserId = authRes.user().id();
        Mono<Optional<UUID>> actorIdMono = fleetRepository.findById(fleetId)
                .flatMap(fleet -> {
                    if (!externalActorPort.isEnabled() || fleet.getKernelOrganizationId() == null) {
                        return Mono.empty();
                    }
                    return externalActorPort.provisionDriverActor(
                            kernelUserId,
                            request.email(),
                            request.firstName(),
                            request.lastName(),
                            fleet.getKernelOrganizationId(),
                            "FLEET_DRIVER");
                })
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());

        return ensureLocalUserSynced(authRes.user(), request)
                .zipWith(actorIdMono)
                .flatMap(tuple -> {
                    UUID localUserId = tuple.getT1();
                    Optional<UUID> actorIdOpt = tuple.getT2();
                    Driver localDriver = new Driver(
                            localUserId,
                            fleetId,
                            request.licenceNumber(),
                            "ACTIVE",
                            null,
                            authRes.user().photoUrl(),
                            actorIdOpt.orElse(null));
                    return driverPersistencePort.save(localDriver);
                });
    }

    private Mono<UUID> ensureLocalUserSynced(AuthPort.UserDetail remoteUser, DriverRegistrationRequest request) {
        return userRepo.findByKernelId(remoteUser.id())
                .switchIfEmpty(userRepo.findById(remoteUser.id()))
                .flatMap(local -> updateLocalUser(local, remoteUser, request).map(UserLocalEntity::getId))
                .switchIfEmpty(Mono.defer(() ->
                        userRepo.findByEmail(request.email())
                                .flatMap(existing -> updateLocalUser(existing, remoteUser, request).map(UserLocalEntity::getId))
                                .switchIfEmpty(createLocalUser(remoteUser, request).map(UserLocalEntity::getId))
                ));
    }

    private Mono<UserLocalEntity> updateLocalUser(UserLocalEntity local, AuthPort.UserDetail remoteUser, DriverRegistrationRequest request) {
        local.setUsername(remoteUser.username() != null ? remoteUser.username() : request.username());
        local.setEmail(request.email());
        local.setFirstName(request.firstName());
        local.setLastName(request.lastName());
        local.setPhotoUrl(remoteUser.photoUrl());
        local.setKernelId(remoteUser.id());
        local.setLastLoginAt(Instant.now());
        if (local.getTenantId() == null) {
            local.setTenantId(resolveTenantId());
        }
        local.setNewRecord(false);
        return userRepo.save(local);
    }

    private Mono<UserLocalEntity> createLocalUser(AuthPort.UserDetail remoteUser, DriverRegistrationRequest request) {
        UserLocalEntity created = UserLocalEntity.builder()
                .id(remoteUser.id())
                .username(remoteUser.username() != null ? remoteUser.username() : request.username())
                .email(request.email())
                .firstName(request.firstName())
                .lastName(request.lastName())
                .photoUrl(remoteUser.photoUrl())
                .isActive(true)
                .lastLoginAt(Instant.now())
                .kernelId(remoteUser.id())
                .tenantId(resolveTenantId())
                .build();
        created.setNewRecord(true);
        return userRepo.save(created);
    }

    private UUID resolveTenantId() {
        if (defaultTenantId == null || defaultTenantId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(defaultTenantId);
        } catch (IllegalArgumentException e) {
            log.warn("⚠️ Tenant ID invalide dans la configuration: {}", defaultTenantId);
            return null;
        }
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
                .flatMap(vehicle -> checkFleetOwnership(vehicle.fleetId(), requesterId).thenReturn(vehicle))
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

    public Mono<Driver> registerDriverWithPhoto(UUID fleetId, DriverRegistrationRequest request, UUID managerId, AuthUseCase.FileContent photo) {
        return checkFleetOwnership(fleetId, managerId)
                .then(Mono.defer(() -> {
                    // 1. Création Auth avec Photo
                    AuthUseCase.RegisterCommand cmd = new AuthUseCase.RegisterCommand(
                        request.username(), request.password(), request.email(), request.phone(),
                        request.firstName(), request.lastName(), List.of("FLEET_DRIVER"), photo
                    );
                    return authPort.registerInRemote(cmd);
                }))
                .flatMap(authRes -> saveDriverWithKernelActor(fleetId, request, authRes));
    }

    public Flux<Driver> getDriversWithFilters(UUID fleetId, Boolean isAssigned, UUID requesterId) {
        // Listing de base (par flotte ou tous si admin)
        Flux<Driver> drivers = (fleetId != null) ? driverPersistencePort.findAllByFleetId(fleetId) : driverPersistencePort.findAll();
        
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
    public Mono<com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.DriverResponse> getDriverEnriched(UUID userId) {
        return driverPersistencePort.findById(userId)
                .switchIfEmpty(Mono.error(new RuntimeException("Conducteur introuvable")))
                .flatMap(this::enrichDriver);
    }

    private Mono<com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.DriverResponse> enrichDriver(Driver driver) {
        return userRepo.findById(driver.userId())
                .map(user -> com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.DriverResponse.from(driver, user))
                .defaultIfEmpty(com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.DriverResponse.from(driver, null));
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
                        v.geofenceRemoteId(),
                        v.kernelResourceId());
                return vehiclePersistencePort.saveLocalData(updated);
            }).then();
}

    private Mono<Void> checkFleetOwnership(UUID fleetId, UUID managerId) {
        return fleetRepository.existsByIdAndManagerId(fleetId, managerId)
                .flatMap(exists -> {
                    if (!exists)
                        return Mono.error(new AccessDeniedException("Cette flotte ne vous appartient pas."));
                    return Mono.empty();
                });
    }

    @Override
    public Mono<DriverResponse> createDriverForManager(ManagerDriverCreateRequest request, UUID managerId) {
        if (request.firstName() == null || request.lastName() == null || request.licenceNumber() == null) {
            return Mono.error(new IllegalArgumentException("firstName, lastName et licenceNumber sont obligatoires"));
        }
        String first = request.firstName().trim();
        String last = request.lastName().trim();
        String baseUsername = (first + "." + last)
                .toLowerCase()
                .replaceAll("[^a-z0-9.]", "")
                + "." + UUID.randomUUID().toString().substring(0, 6);
        String email = (request.email() != null && !request.email().isBlank())
                ? request.email().trim()
                : baseUsername + "@fleetman.local";
        String phone = (request.phone() != null && !request.phone().isBlank())
                ? request.phone().trim()
                : "+237600000000";
        DriverRegistrationRequest registration = new DriverRegistrationRequest(
                baseUsername,
                DemoTestAccounts.DEMO_PASSWORD,
                email,
                phone,
                first,
                last,
                request.licenceNumber().trim()
        );
        return registerDriver(request.fleetId(), registration, managerId)
                .flatMap(d -> getDriverEnriched(d.userId()));
    }

    @Override
    public Mono<DriverResponse> updateDriverForManager(UUID userId, ManagerDriverUpdateRequest request, UUID managerId) {
        return driverPersistencePort.findById(userId)
                .switchIfEmpty(Mono.error(new RuntimeException("Conducteur introuvable")))
                .flatMap(driver -> {
                    Mono<Void> ownership = driver.fleetId() != null
                            ? checkFleetOwnership(driver.fleetId(), managerId)
                            : Mono.empty();
                    return ownership.then(Mono.defer(() -> {
                        Mono<Void> fleetMove = Mono.empty();
                        if (request.fleetId() != null && !request.fleetId().equals(driver.fleetId())) {
                            fleetMove = checkFleetOwnership(request.fleetId(), managerId)
                                    .then(driverPersistencePort.updateFleetAssignment(userId, request.fleetId()));
                        }
                        Driver next = new Driver(
                                driver.userId(),
                                request.fleetId() != null ? request.fleetId() : driver.fleetId(),
                                request.licenceNumber() != null ? request.licenceNumber() : driver.licenceNumber(),
                                request.status() != null ? request.status() : driver.status(),
                                driver.assignedVehicleId(),
                                request.photoUrl() != null ? request.photoUrl() : driver.photoUrl(),
                                driver.kernelActorId()
                        );
                        Mono<Void> profileUpdate = needsProfileUpdate(request)
                                ? authPort.updateUserProfile(userId, "fake-token",
                                        new AuthUseCase.UpdateProfileCommand(
                                                request.firstName(),
                                                request.lastName(),
                                                request.phone(),
                                                request.email()
                                        )).then(syncLocalUserNames(userId, request))
                                : Mono.empty();
                        return fleetMove
                                .then(driverPersistencePort.save(next))
                                .then(profileUpdate)
                                .then(getDriverEnriched(userId));
                    }));
                });
    }

    private boolean needsProfileUpdate(ManagerDriverUpdateRequest request) {
        return request.firstName() != null || request.lastName() != null
                || request.email() != null || request.phone() != null;
    }

    private Mono<Void> syncLocalUserNames(UUID userId, ManagerDriverUpdateRequest request) {
        return userRepo.findById(userId)
                .flatMap(user -> {
                    if (request.firstName() != null) user.setFirstName(request.firstName());
                    if (request.lastName() != null) user.setLastName(request.lastName());
                    if (request.email() != null) user.setEmail(request.email());
                    user.setNewRecord(false);
                    return userRepo.save(user);
                }).then();
    }
}