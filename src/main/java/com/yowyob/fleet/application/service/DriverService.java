package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.model.Driver;
import com.yowyob.fleet.domain.exception.DriverException;
import com.yowyob.fleet.domain.exception.PlanningException;
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
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.FleetManagerR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.FleetR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.UserLocalR2dbcRepository;

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
    private final FleetManagerR2dbcRepository managerRepo;
    private final ExternalVehiclePort externalVehiclePort;
    private final ExternalActorPort externalActorPort;
    private final UserLocalR2dbcRepository userRepo;
    private final PlanLimitGuard planLimitGuard;
    private final com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.TripR2dbcRepository tripRepository;
    private final AccessScopeService accessScope;
    private final ResourceAvailabilityService availability;
    private final com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.DriverR2dbcRepository driverRepository;
    private final DriverAbsenceService absenceService;
    private final org.springframework.r2dbc.core.DatabaseClient db;
    private final AppNotifier notifier;

    private static final String SERVICE_NAME = "FLEET_MANAGEMENT";

    /** Mot de passe initial d'un chauffeur créé sans mot de passe (variable DRIVER_DEFAULT_PASSWORD). */
    @Value("${application.driver.default-password:}")
    private String driverDefaultPassword;

    @Value("${application.kernel.tenant-id:}")
    private String defaultTenantId;

    // --- 1. CRÉATION COMPLÈTE ---
    @Override
    public Mono<Driver> registerDriver(UUID fleetId, DriverRegistrationRequest request, UUID managerId) {
        return planLimitGuard.assertCanCreateDriver(managerId)
                .then(accessScope.assertFleetAccess(managerId, fleetId))
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
                    return driverPersistencePort.save(localDriver)
                            .flatMap(saved -> driverPersistencePort
                                    .updateEmployment(saved.userId(), "ACTIVE", java.time.LocalDate.now(), null, null)
                                    .thenReturn(saved));
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
        return accessScope.assertFleetAccess(managerId, fleetId)
                .thenMany(authPort.getUsersByService(SERVICE_NAME, token))
                .filter(u -> isMatch(u, identifier))
                .filter(u -> u.roles().contains("FLEET_DRIVER"))
                .next()
                .switchIfEmpty(
                        Mono.error(new RuntimeException("Aucun chauffeur trouvé avec cet identifiant : " + identifier)))
                .flatMap(user -> attachDriverToFleet(user.id(), fleetId, managerId));
    }

    /**
     * Rattache un chauffeur existant à une flotte. Un chauffeur sans flotte consomme une
     * place du plan (comme une création) ; un chauffeur déjà rattaché ne peut qu'être
     * transféré entre flottes du périmètre, sans trajet ni affectation en cours, et perd
     * son véhicule de l'ancienne flotte. Avant : recruter « volait » le chauffeur d'une
     * autre entreprise et contournait la limite de chauffeurs du plan.
     */
    @Override
    public Mono<Void> attachDriverToFleet(UUID driverId, UUID fleetId, UUID managerId) {
        return accessScope.assertFleetAccess(managerId, fleetId)
                .then(driverPersistencePort.findById(driverId))
                .switchIfEmpty(Mono.error(new RuntimeException("Chauffeur introuvable ou non enregistré")))
                .flatMap(driver -> {
                    if (fleetId.equals(driver.fleetId())) {
                        return Mono.<Void>empty();
                    }
                    if (driver.fleetId() != null) {
                        return accessScope.assertFleetAccess(managerId, driver.fleetId())
                                .onErrorMap(e -> DriverException.alreadyInAnotherFleet())
                                .then(assertNoOpenCommitments(driverId))
                                .then(unassignVehicle(driverId, managerId))
                                .then(driverPersistencePort.updateFleetAssignment(driverId, fleetId));
                    }
                    return planLimitGuard.assertCanCreateDriver(managerId)
                            .then(driverPersistencePort.updateFleetAssignment(driverId, fleetId));
                });
    }

    private Mono<Void> assertNoOpenCommitments(UUID driverId) {
        return availability.driverHasOpenCommitments(driverId)
                .flatMap(open -> Boolean.TRUE.equals(open)
                        ? Mono.<Void>error(DriverException.openCommitments())
                        : Mono.<Void>empty());
    }

    private boolean isMatch(AuthPort.UserDetail user, String id) {
        return id.equalsIgnoreCase(user.email()) ||
                id.equalsIgnoreCase(user.username()) ||
                id.equals(user.phone());
    }

    // --- 3. LECTURE ---
    @Override
    public Flux<Driver> getDrivers(UUID fleetId, UUID requesterId, boolean isAdmin) {
        if (fleetId != null) {
            return accessScope.assertFleetAccess(requesterId, fleetId)
                    .thenMany(driverPersistencePort.findAllByFleetId(fleetId));
        }
        if (isAdmin) {
            return driverPersistencePort.findAllByOrganizationAdminId(requesterId);
        }
        return driverPersistencePort.findAllByManagerId(requesterId);
    }

    @Override
    public Mono<Driver> getDriverById(UUID userId) {
        return driverPersistencePort.findById(userId);
    }

    // --- 4. GESTION LIENS ---
    @Override
    public Mono<Void> removeDriverFromFleet(UUID fleetId, UUID driverId, UUID requesterId) {
        // Le chauffeur doit être dans CETTE flotte et libre de tout trajet ou affectation
        // non terminés (règle 8.3) — sinon trajets/affectations pointeraient hors flotte.
        return accessScope.assertFleetAccess(requesterId, fleetId)
                .then(driverPersistencePort.findById(driverId))
                .switchIfEmpty(Mono.error(new RuntimeException("Conducteur introuvable")))
                .flatMap(driver -> fleetId.equals(driver.fleetId())
                        ? assertNoOpenCommitments(driverId)
                        : Mono.<Void>error(DriverException.notInFleet()))
                .then(unassignVehicle(driverId, requesterId))
                .then(driverPersistencePort.updateFleetAssignment(driverId, null));
    }

    @Override
    @Transactional
    public Mono<Void> assignVehicle(UUID driverId, UUID targetVehicleId, UUID requesterId, String token) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        return vehiclePersistencePort.getLocalDataById(targetVehicleId)
                .switchIfEmpty(Mono.error(com.yowyob.fleet.domain.exception.VehicleException.notFound(targetVehicleId)))
                .flatMap(vehicle -> accessScope.assertFleetAccess(requesterId, vehicle.fleetId()).thenReturn(vehicle))
                .flatMap(vehicle -> {
                    // Un véhicule retiré ou immobilisé ne peut pas devenir le véhicule attitré
                    // (un passage à l'atelier n'empêche pas, lui, de garder son titulaire).
                    if (com.yowyob.fleet.domain.model.VehicleLifecycle.RETIRED.equals(vehicle.status())
                            || com.yowyob.fleet.domain.model.VehicleLifecycle.IMMOBILIZED.equals(vehicle.status())) {
                        return Mono.<Vehicle>error(com.yowyob.fleet.domain.exception.VehicleException.notDispatchable(
                                com.yowyob.fleet.domain.model.VehicleLifecycle.label(vehicle.status())));
                    }
                    return Mono.just(vehicle);
                })
                // Chauffeur du périmètre, dans les effectifs et de la même flotte ; permis couvrant
                // la catégorie du véhicule ; véhicule en règle ; aucune réaffectation pendant un
                // trajet en cours du chauffeur ou du véhicule (règle 8.3 « verrouillage de course »).
                .flatMap(vehicle -> driverPersistencePort.findById(driverId)
                        .switchIfEmpty(Mono.error(DriverException.notFound()))
                        .flatMap(driver -> {
                            if (!java.util.Objects.equals(driver.fleetId(), vehicle.fleetId())) {
                                return Mono.<Void>error(DriverException.notInFleet());
                            }
                            return availability.assertDriverEngageable(
                                    requesterId, driver, () -> "LEFT".equals(driver.status())
                                            ? DriverException.left() : PlanningException.driverInactive(driverId));
                        })
                        .then(availability.assertDriverLicensed(driverId, vehicle.id(), now, now))
                        .then(availability.assertVehicleCompliant(vehicle.id(), now, now))
                        .then(assertNoTripInProgress(driverId, vehicle.id()))
                        .thenReturn(vehicle))
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

                    // 3. Exécution chainée (états avant changement gardés pour prévenir les chauffeurs)
                    Mono<UUID> previousVehicle = driverPersistencePort.findById(driverId)
                            .flatMap(d -> Mono.justOrEmpty(d.assignedVehicleId()))
                            .defaultIfEmpty(NO_ID);
                    Mono<UUID> previousDriver = driverPersistencePort.findByAssignedVehicleId(targetVehicleId)
                            .map(Driver::userId)
                            .defaultIfEmpty(NO_ID);
                    return Mono.zip(previousVehicle, previousDriver).flatMap(before -> clearOldVehicleOfDriver
                            .then(clearOldDriverOfVehicle)
                            .then(remoteSync) // <--- Appel distant ici
                            .then(updateVehicleLink(targetVehicleId, driverId))
                            .then(driverPersistencePort.updateVehicleAssignment(driverId, targetVehicleId))
                            .then(notifyRegularVehicle(driverId, targetVehicleId, before.getT1(), before.getT2(), requesterId)));
                });
    }

    private static final UUID NO_ID = new UUID(0, 0);

    /**
     * Véhicule attitré : le chauffeur est prévenu de son nouveau véhicule (et de celui qu'il rend),
     * l'ancien titulaire du véhicule de son retrait. Rien si rien ne change.
     */
    private Mono<Void> notifyRegularVehicle(UUID driverId, UUID vehicleId, UUID previousVehicleId, UUID previousDriverId,
                                            UUID requesterId) {
        if (vehicleId.equals(previousVehicleId)) return Mono.empty();
        return accessScope.resolveLocalUserId(requesterId).defaultIfEmpty(NO_ID).flatMap(actor -> Mono.zip(
                        plate(vehicleId), NO_ID.equals(previousVehicleId) ? Mono.just("") : plate(previousVehicleId))
                .flatMap(p -> notifier.toUser(driverId, actor, "Véhicule attitré",
                                "Le véhicule " + p.getT1() + " vous est attribué comme véhicule attitré"
                                        + (p.getT2().isEmpty() ? "" : " (il remplace " + p.getT2() + ")") + ".",
                                AppNotifier.Level.INFO, "VEHICLE_ASSIGNED", AppNotifier.refs("vehicleId", vehicleId, "driverId", driverId))
                        .then(NO_ID.equals(previousDriverId) || previousDriverId.equals(driverId) ? Mono.<Void>empty()
                                : notifier.toUser(previousDriverId, actor, "Véhicule attitré retiré",
                                        "Le véhicule " + p.getT1() + " ne vous est plus attribué : il a été confié à un autre chauffeur.",
                                        AppNotifier.Level.WARNING, "VEHICLE_UNASSIGNED",
                                        AppNotifier.refs("vehicleId", vehicleId, "driverId", previousDriverId)))));
    }

    private Mono<String> plate(UUID vehicleId) {
        return db.sql("SELECT license_plate FROM fleet.vehicles WHERE id = :id")
                .bind("id", vehicleId)
                .map((row, meta) -> row.get("license_plate", String.class))
                .one()
                .defaultIfEmpty("-");
    }

    
    @Override
    @Transactional
    public Mono<Void> unassignVehicle(UUID driverId, UUID requesterId) {
        return driverPersistencePort.findById(driverId)
                .flatMap(driver -> {
                    if (driver.assignedVehicleId() == null)
                        return Mono.empty();
                    UUID vehicleId = driver.assignedVehicleId();
                    return assertNoTripInProgress(driverId, vehicleId)
                            .then(updateVehicleLink(vehicleId, null))
                            .then(driverPersistencePort.updateVehicleAssignment(driverId, null))
                            .then(accessScope.resolveLocalUserId(requesterId).defaultIfEmpty(NO_ID))
                            .flatMap(actor -> plate(vehicleId).flatMap(p -> notifier.toUser(driverId, actor,
                                    "Véhicule attitré retiré", "Le véhicule " + p + " ne vous est plus attribué comme véhicule attitré.",
                                    AppNotifier.Level.WARNING, "VEHICLE_UNASSIGNED",
                                    AppNotifier.refs("vehicleId", vehicleId, "driverId", driverId))));
                });
    }

    /** Règle 8.3 : pas de (dés)affectation pendant un trajet en cours du chauffeur ou du véhicule. */
    private Mono<Void> assertNoTripInProgress(UUID driverId, UUID vehicleId) {
        return tripRepository.existsInProgressTripForDriver(driverId, null)
                .flatMap(driverBusy -> Boolean.TRUE.equals(driverBusy)
                        ? Mono.just(true)
                        : tripRepository.existsInProgressTripForVehicle(vehicleId, null))
                .flatMap(busy -> Boolean.TRUE.equals(busy)
                        ? Mono.<Void>error(DriverException.ongoingTripConflict())
                        : Mono.<Void>empty());
    }

    // ... (ajouter les nouvelles méthodes à l'existant)

    public Mono<Driver> registerDriverWithPhoto(UUID fleetId, DriverRegistrationRequest request, UUID managerId, AuthUseCase.FileContent photo) {
        // Même limite de plan que registerDriver (contournée par la variante avec photo).
        return planLimitGuard.assertCanCreateDriver(managerId)
                .then(accessScope.assertFleetAccess(managerId, fleetId))
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
        Flux<Driver> drivers = accessScope.isOrganizationAdmin(requesterId)
                .flatMapMany(isAdmin -> {
                    if (fleetId != null) {
                        return accessScope.assertFleetAccess(requesterId, fleetId)
                                .thenMany(driverPersistencePort.findAllByFleetId(fleetId));
                    }
                    return Boolean.TRUE.equals(isAdmin)
                            ? driverPersistencePort.findAllByOrganizationAdminId(requesterId)
                            : driverPersistencePort.findAllByManagerId(requesterId);
                });

        if (isAssigned != null) {
            drivers = drivers.filter(d -> (isAssigned ? d.assignedVehicleId() != null : d.assignedVehicleId() == null));
        }
        return drivers;
    }

    @Override
    public Flux<com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.DriverResponse> getDriversEnriched(
            UUID fleetId, Boolean isAssigned, UUID requesterId) {
        // Un seul aller-retour pour récupérer TOUS les chauffeurs actuellement
        // mobilisés par un trajet (peu importe le nombre de chauffeurs listés),
        // au lieu d'une requête par chauffeur (N+1).
        return Mono.zip(
                        tripRepository.findActiveTripDriverIds().collect(java.util.stream.Collectors.toSet()),
                        getDriversWithFilters(fleetId, isAssigned, requesterId).collectList())
                .flatMapMany(t -> absenceService
                        .openAbsencesByDriver(t.getT2().stream().map(Driver::userId).toList(), java.time.LocalDate.now())
                        .flatMapMany(absences -> Flux.fromIterable(t.getT2())
                                .flatMap(driver -> enrichDriver(driver, t.getT1().contains(driver.userId()),
                                        absences.getOrDefault(driver.userId(), List.of())))));
    }

    @Override
    public Mono<com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.DriverResponse> getDriverEnriched(UUID userId) {
        return driverPersistencePort.findById(userId)
                .switchIfEmpty(Mono.error(DriverException.notFound()))
                .flatMap(driver -> Mono.zip(
                                tripRepository.existsInProgressTripForDriver(userId, null),
                                absenceService.openAbsencesByDriver(List.of(userId), java.time.LocalDate.now()))
                        .flatMap(t -> enrichDriver(driver, Boolean.TRUE.equals(t.getT1()),
                                t.getT2().getOrDefault(userId, List.of()))));
    }

    private Mono<com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.DriverResponse> enrichDriver(
            Driver driver, boolean onActiveTrip, List<com.yowyob.fleet.domain.model.DriverAbsence> absences) {
        Mono<String> fleetName = driver.fleetId() == null
                ? Mono.just("")
                : fleetRepository.findById(driver.fleetId())
                        .map(f -> f.getName() != null ? f.getName() : "")
                        .defaultIfEmpty("");
        Mono<String> vehicleLabel = driver.assignedVehicleId() == null
                ? Mono.just("")
                : vehiclePersistencePort.getLocalDataById(driver.assignedVehicleId())
                        .map(v -> {
                            String make = v.brand() != null ? v.brand() : "";
                            String model = v.model() != null ? v.model() : "";
                            String plate = v.licensePlate() != null ? v.licensePlate() : "";
                            String name = (make + " " + model).trim();
                            if (!plate.isBlank()) {
                                return name.isBlank() ? plate : name + " (" + plate + ")";
                            }
                            return name;
                        })
                        .defaultIfEmpty("");
        return Mono.zip(
                        userRepo.findById(driver.userId()).defaultIfEmpty(new UserLocalEntity()),
                        fleetName,
                        vehicleLabel,
                        driverRepository.findById(driver.userId())
                                .defaultIfEmpty(new com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.DriverEntity())
                )
                .map(t -> {
                    var entity = t.getT4();
                    UserLocalEntity user = t.getT1().getId() != null ? t.getT1() : null;
                    String fleet = t.getT2() != null && !t.getT2().isBlank() ? t.getT2() : null;
                    String vehicle = t.getT3() != null && !t.getT3().isBlank() ? t.getT3() : null;
                    return DriverResponse.from(
                            driver,
                            user,
                            user != null ? user.getPhone() : null,
                            onActiveTrip,
                            fleet,
                            vehicle
                    ).withEmployment(entity.getHireDate(), entity.getLeftAt(), entity.getLeftReason(), absences);
                });
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

    @Override
    public Mono<DriverResponse> createDriverForManager(ManagerDriverCreateRequest request, UUID managerId) {
        if (isBlank(request.firstName()) || isBlank(request.lastName()) || isBlank(request.licenceNumber())) {
            return Mono.error(new IllegalArgumentException("Prénom, nom et numéro de permis sont obligatoires."));
        }
        // Un chauffeur doit pouvoir être joint pendant une mission : plus de numéro factice
        // partagé (+237600000000) qui envoyait les appels et SMS de tous au même endroit.
        if (isBlank(request.phone())) {
            return Mono.error(DriverException.phoneRequired());
        }
        if (request.hireDate() != null && request.hireDate().isAfter(java.time.LocalDate.now().plusMonths(3))) {
            return Mono.error(new IllegalArgumentException("La date d'embauche ne peut pas dépasser trois mois à venir."));
        }
        String first = request.firstName().trim();
        String last = request.lastName().trim();
        String licence = normalizeLicence(request.licenceNumber());
        String baseUsername = (request.username() != null && !request.username().isBlank())
                ? request.username().trim()
                : (first + "." + last).toLowerCase().replaceAll("[^a-z0-9.]", "")
                    + "." + UUID.randomUUID().toString().substring(0, 6);
        // Le Kernel exige une adresse : à défaut, une adresse technique non routable.
        String email = (request.email() != null && !request.email().isBlank())
                ? request.email().trim()
                : baseUsername + "@fleetman.local";
        String phone = request.phone().trim();
        String password = (request.password() != null && !request.password().isBlank())
                ? request.password()
                : driverDefaultPassword;
        if (password == null || password.isBlank()) {
            return Mono.error(new IllegalArgumentException(
                    "Mot de passe du chauffeur obligatoire (aucun mot de passe par défaut configuré)"));
        }
        DriverRegistrationRequest registration = new DriverRegistrationRequest(
                baseUsername, password, email, phone, first, last, licence);
        java.time.LocalDate hireDate = request.hireDate() != null ? request.hireDate() : java.time.LocalDate.now();
        return accessScope.assertFleetAccess(managerId, request.fleetId())
                // Vérifié avant de créer le compte Kernel, pour ne pas laisser de compte orphelin.
                .then(assertLicenceFree(licence, null))
                .then(registerDriver(request.fleetId(), registration, managerId))
                .flatMap(d -> syncLocalPhone(d.userId(), phone)
                        .then(driverPersistencePort.updateEmployment(d.userId(), "ACTIVE", hireDate, null, null))
                        .then(getDriverEnriched(d.userId())));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String normalizeLicence(String licence) {
        return licence == null ? null : licence.trim().replaceAll("\\s+", " ").toUpperCase(java.util.Locale.ROOT);
    }

    private Mono<Void> assertLicenceFree(String licence, UUID excludeDriverId) {
        return driverRepository.findByLicenceNumberNormalized(licence)
                .filter(d -> !d.getUserId().equals(excludeDriverId))
                .hasElement()
                .flatMap(taken -> Boolean.TRUE.equals(taken)
                        ? Mono.<Void>error(DriverException.licenseConflict())
                        : Mono.<Void>empty());
    }

    private Mono<Void> syncLocalPhone(UUID userId, String phone) {
        return userRepo.findById(userId)
                .filter(u -> !phone.equals(u.getPhone()))
                .flatMap(u -> {
                    u.setPhone(phone);
                    u.setNewRecord(false);
                    return userRepo.save(u);
                })
                .then();
    }

    @Override
    public Mono<DriverResponse> updateDriverForManager(UUID userId, ManagerDriverUpdateRequest request, UUID managerId) {
        return driverPersistencePort.findById(userId)
                .switchIfEmpty(Mono.error(DriverException.notFound()))
                .flatMap(driver -> {
                    Mono<Void> ownership = driver.fleetId() != null
                            ? accessScope.assertFleetAccess(managerId, driver.fleetId())
                            : Mono.empty();
                    return ownership.then(Mono.defer(() -> {
                        // Statut contractuel : « INACTIVE » (ancien écran) ou LEFT = départ ce jour,
                        // ACTIVE sur un chauffeur parti = réintégration. Congés et suspensions
                        // passent par les absences datées.
                        String requested = request.status() == null ? null : request.status().trim().toUpperCase(java.util.Locale.ROOT);
                        boolean leaving = ("LEFT".equals(requested) || "INACTIVE".equals(requested)) && !"LEFT".equals(driver.status());
                        boolean returning = "ACTIVE".equals(requested) && "LEFT".equals(driver.status());
                        if (requested != null && !leaving && !returning && !List.of("ACTIVE", "LEFT", "INACTIVE").contains(requested)) {
                            return Mono.<DriverResponse>error(new IllegalArgumentException(
                                    "Statut invalide : utilisez les absences pour un congé ou une suspension."));
                        }
                        boolean moving = request.fleetId() != null && !request.fleetId().equals(driver.fleetId());
                        if (moving && "LEFT".equals(driver.status()) && !returning) {
                            return Mono.<DriverResponse>error(DriverException.left());
                        }
                        if (request.phone() != null && request.phone().isBlank()) {
                            return Mono.<DriverResponse>error(DriverException.phoneRequired());
                        }
                        String licence = request.licenceNumber() != null ? normalizeLicence(request.licenceNumber()) : driver.licenceNumber();
                        Mono<Void> licenceCheck = licence != null && !licence.equals(driver.licenceNumber())
                                ? assertLicenceFree(licence, userId)
                                : Mono.empty();
                        // Un chauffeur changé de flotte ne peut laisser derrière lui des trajets ou
                        // affectations non terminés, ni garder le véhicule de son ancienne flotte.
                        Mono<Void> fleetMove = moving
                                ? assertNoOpenCommitments(userId).then(unassignVehicle(userId, managerId))
                                    .then(accessScope.assertFleetAccess(managerId, request.fleetId()))
                                    .then(driverPersistencePort.updateFleetAssignment(userId, request.fleetId()))
                                : Mono.empty();
                        Mono<Void> save = Mono.defer(() -> driverPersistencePort.findById(userId)
                                .flatMap(current -> driverPersistencePort.save(new Driver(
                                        current.userId(),
                                        current.fleetId(),
                                        licence,
                                        current.status(),
                                        current.assignedVehicleId(),
                                        request.photoUrl() != null ? request.photoUrl() : current.photoUrl(),
                                        current.kernelActorId())))
                                .then());
                        // authPort.updateUserProfile n'est pas implémenté côté Kernel : la copie
                        // locale (fleet.users) est la source de vérité pour ces champs.
                        Mono<Void> profileUpdate = needsProfileUpdate(request)
                                ? syncLocalUserNames(userId, request)
                                : Mono.empty();
                        Mono<Void> contract = leaving
                                ? leaveCompany(userId, java.time.LocalDate.now(), null, managerId).then()
                                : returning ? reinstate(userId, managerId).then() : Mono.empty();
                        // Instantané avant modification : le chauffeur n'est prévenu que des changements réels
                        // (le formulaire du gestionnaire renvoie tous les champs à chaque enregistrement).
                        return getDriverEnriched(userId).flatMap(before -> licenceCheck
                                .then(fleetMove)
                                .then(save)
                                .then(profileUpdate)
                                .then(contract)
                                .then(getDriverEnriched(userId))
                                .flatMap(after -> notifyRecordChange(before, after, managerId).thenReturn(after)));
                    }));
                });
    }

    /** Prévient le chauffeur d'un changement de flotte ou d'une mise à jour de sa fiche par le gestionnaire. */
    private Mono<Void> notifyRecordChange(com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.DriverResponse before,
                                          com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.DriverResponse after,
                                          UUID actorId) {
        UUID driverId = after.userId();
        Mono<Void> fleet = !java.util.Objects.equals(before.fleetId(), after.fleetId()) && after.fleetId() != null
                ? notifier.toUser(driverId, actorId, "Changement de flotte",
                        "Vous êtes désormais rattaché à la flotte " + (after.fleetName() != null ? after.fleetName() : "") + ".",
                        AppNotifier.Level.WARNING, "DRIVER_FLEET_CHANGED", AppNotifier.refs("driverId", driverId))
                : Mono.empty();
        List<String> changed = new java.util.ArrayList<>();
        if (!java.util.Objects.equals(before.licenceNumber(), after.licenceNumber())) changed.add("numéro de permis");
        if (!java.util.Objects.equals(before.firstName(), after.firstName())
                || !java.util.Objects.equals(before.lastName(), after.lastName())) changed.add("nom");
        if (!java.util.Objects.equals(before.email(), after.email())) changed.add("adresse email");
        if (!java.util.Objects.equals(before.phone(), after.phone())) changed.add("téléphone");
        if (!java.util.Objects.equals(before.photoUrl(), after.photoUrl())) changed.add("photo");
        Mono<Void> record = changed.isEmpty()
                ? Mono.empty()
                : notifier.toUser(driverId, actorId, "Fiche mise à jour",
                        "Votre gestionnaire a mis à jour votre fiche : " + String.join(", ", changed) + ".",
                        AppNotifier.Level.INFO, "DRIVER_PROFILE_UPDATED", AppNotifier.refs("driverId", driverId));
        return fleet.then(record);
    }

    @Override
    @Transactional
    public Mono<DriverResponse> leaveCompany(UUID driverId, java.time.LocalDate leftAt, String reason, UUID actorId) {
        java.time.LocalDate date = leftAt != null ? leftAt : java.time.LocalDate.now();
        if (date.isAfter(java.time.LocalDate.now())) {
            return Mono.error(DriverException.invalidDeparture("enregistrez le départ le jour même ou après coup"));
        }
        return requireDriverInScope(driverId, actorId)
                .flatMap(driver -> {
                    if ("LEFT".equals(driver.status())) {
                        return Mono.<Void>error(DriverException.left());
                    }
                    return assertNoOpenCommitments(driverId)
                            .then(unassignVehicle(driverId, actorId))
                            .then(driverPersistencePort.updateEmployment(driverId, "LEFT", null, date,
                                    reason == null || reason.isBlank() ? null : reason.trim()));
                })
                .then(notifier.toUser(driverId, actorId, "Départ enregistré",
                        "Votre départ de l'entreprise est enregistré au "
                                + date.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ".",
                        AppNotifier.Level.WARNING, "DRIVER_LEFT", AppNotifier.refs("driverId", driverId)))
                .then(getDriverEnriched(driverId))
                .doOnSuccess(d -> log.info("👋 Chauffeur {} sorti des effectifs le {}", driverId, date));
    }

    @Override
    @Transactional
    public Mono<DriverResponse> reinstate(UUID driverId, UUID actorId) {
        return requireDriverInScope(driverId, actorId)
                .flatMap(driver -> {
                    if (!"LEFT".equals(driver.status())) {
                        return Mono.<Void>empty();
                    }
                    // La place est décomptée sur le plan du gestionnaire de la flotte.
                    return fleetRepository.findById(driver.fleetId())
                            .map(f -> f.getManagerId() != null ? f.getManagerId() : actorId)
                            .defaultIfEmpty(actorId)
                            .flatMap(planLimitGuard::assertCanCreateDriver)
                            .then(driverPersistencePort.updateEmployment(driverId, "ACTIVE",
                                    java.time.LocalDate.now(), null, null))
                            .then(notifier.toUser(driverId, actorId, "Réintégration",
                                    "Vous faites de nouveau partie des effectifs de l'entreprise.",
                                    AppNotifier.Level.SUCCESS, "DRIVER_REINSTATED", AppNotifier.refs("driverId", driverId)));
                })
                .then(getDriverEnriched(driverId));
    }

    private Mono<Driver> requireDriverInScope(UUID driverId, UUID actorId) {
        return driverPersistencePort.findById(driverId)
                .switchIfEmpty(Mono.error(DriverException.notFound()))
                .flatMap(driver -> driver.fleetId() == null
                        ? Mono.<Driver>error(DriverException.notInFleet())
                        : accessScope.assertFleetAccess(actorId, driver.fleetId()).thenReturn(driver));
    }

    @Override
    public Mono<Void> deleteDriverForManager(UUID userId, UUID callerId) {
        return driverPersistencePort.findById(userId)
                .switchIfEmpty(Mono.error(new RuntimeException("Conducteur introuvable")))
                .flatMap(driver -> {
                    Mono<Void> ownership = driver.fleetId() != null
                            ? accessScope.assertFleetAccess(callerId, driver.fleetId())
                            : Mono.error(new AccessDeniedException(
                                    "Ce chauffeur n'est rattaché à aucune flotte de votre organisation."));
                    Mono<Void> unassign = driver.assignedVehicleId() != null
                            ? unassignVehicle(userId, callerId).onErrorResume(e -> Mono.empty())
                            : Mono.empty();
                    // Un chauffeur qui a travaillé sort des effectifs par un départ (historique
                    // conservé) : la suppression est réservée aux fiches créées par erreur.
                    Mono<Void> history = driverHasHistory(userId)
                            .flatMap(used -> Boolean.TRUE.equals(used)
                                    ? Mono.<Void>error(DriverException.hasHistory())
                                    : Mono.<Void>empty());
                    return ownership
                            .then(assertNoOpenCommitments(userId))
                            .then(history)
                            .then(unassign)
                            .then(driverPersistencePort.softDelete(userId))
                            .then(softDeleteUser(userId));
                })
                .doOnSuccess(v -> log.info("🗑️ Chauffeur {} supprimé (soft delete)", userId));
    }

    private Mono<Boolean> driverHasHistory(UUID driverId) {
        return db.sql("""
                        SELECT EXISTS (SELECT 1 FROM fleet.trips WHERE driver_id = :id)
                            OR EXISTS (SELECT 1 FROM fleet.assignments WHERE driver_id = :id)
                            OR EXISTS (SELECT 1 FROM fleet.fuel_recharges WHERE driver_id = :id)
                            OR EXISTS (SELECT 1 FROM fleet.expenses WHERE driver_id = :id)
                            OR EXISTS (SELECT 1 FROM fleet.incidents WHERE driver_id = :id) AS used
                        """)
                .bind("id", driverId)
                .map((row, meta) -> Boolean.TRUE.equals(row.get("used", Boolean.class)))
                .one()
                .defaultIfEmpty(false);
    }

    private Mono<Void> softDeleteUser(UUID userId) {
        return userRepo.findById(userId)
                .flatMap(user -> {
                    if (user.getDeletedAt() != null) {
                        return Mono.just(user);
                    }
                    user.setDeletedAt(Instant.now());
                    user.setActive(false);
                    user.setNewRecord(false);
                    return userRepo.save(user);
                })
                .then();
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
                    if (request.phone() != null) user.setPhone(request.phone());
                    user.setNewRecord(false);
                    return userRepo.save(user);
                }).then();
    }
}