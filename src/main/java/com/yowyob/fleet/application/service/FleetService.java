package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.FleetException;
import com.yowyob.fleet.domain.exception.VehicleException;
import com.yowyob.fleet.domain.model.Driver;
import com.yowyob.fleet.domain.model.Fleet;
import com.yowyob.fleet.domain.model.Vehicle;
import com.yowyob.fleet.domain.ports.in.ManageDriverUseCase;
import com.yowyob.fleet.domain.ports.in.ManageFleetUseCase;
import com.yowyob.fleet.domain.ports.out.*;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.DriverRegistrationRequest;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.FleetStatsResponse;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.FleetEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.DriverR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.FleetR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.TripR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.VehicleLocalR2dbcRepository;
import com.yowyob.fleet.infrastructure.config.KernelTokenHolder;
import com.yowyob.fleet.infrastructure.mappers.FleetMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FleetService implements ManageFleetUseCase {

    private final FleetR2dbcRepository repository;
    private final FleetMapper mapper;
    
    // Ports pour l'orchestration
    private final VehiclePersistencePort vehiclePersistence;
    private final DriverPersistencePort driverPersistence;
    private final GeofencePersistencePort geofencePersistence;
    private final ExternalGeofencePort externalGeofenceApi;
    private final ManageDriverUseCase driverUseCase; // Pour le register direct
    
    // Repositories pour stats
    private final VehicleLocalR2dbcRepository vehicleRepo;
    private final DriverR2dbcRepository driverRepo;
    private final TripR2dbcRepository tripRepo;
    private final PlanLimitGuard planLimitGuard;
    private final ExternalOrganizationPort externalOrganizationPort;
    private final KernelTokenHolder kernelTokenHolder;
    private final ExpensePersistencePort expensePersistence;
    private final AccessScopeService accessScope;
    private final ResourceAvailabilityService availability;
    private final ResourceAccessService resourceAccess;
    private final CompanyScopeService companyScope;
    private final com.yowyob.fleet.domain.ports.in.ManageBudgetUseCase budgetUseCase;
    private final org.springframework.r2dbc.core.DatabaseClient db;

    @Value("${application.kernel.organization-sync:true}")
    private boolean organizationSync;

    @Value("${application.kernel.business-actor-id:}")
    private String businessActorId;

    @Value("${application.kernel.service:COMMERCIAL}")
    private String kernelServiceCode;

    // ========================================================================
    // --- 10a. ADMINISTRATION CRUD ---
    // ========================================================================

    @Override
    @Transactional
    public Mono<Fleet> createFleet(Fleet fleet, UUID managerId) {
        return planLimitGuard.assertCanCreateFleet(managerId)
                .then(Mono.defer(() -> {
                    if (!organizationSync || !externalOrganizationPort.isEnabled()) {
                        return saveFleetLocally(fleet, managerId, null);
                    }
                    return kernelTokenHolder.getValidAccessToken()
                            .flatMap(token -> provisionKernelOrganization(fleet, token))
                            .flatMap(orgId -> saveFleetLocally(fleet, managerId, orgId));
                }));
    }

    private Mono<UUID> provisionKernelOrganization(Fleet fleet, String ownerToken) {
        UUID actorId = resolveBusinessActorId();
        String code = toOrganizationCode(fleet.name());
        var command = new ExternalOrganizationPort.CreateOrganizationCommand(
                actorId,
                code,
                fleet.name(),
                fleet.name(),
                kernelServiceCode
        );
        return externalOrganizationPort.createOrganization(command, ownerToken)
                .flatMap(org -> externalOrganizationPort
                        .approveOrganization(org.id(), "FleetMan fleet provisioning", ownerToken)
                        .then(subscribeDefaultServices(org.id(), ownerToken))
                        .thenReturn(org.id()));
    }

    private Mono<Void> subscribeDefaultServices(UUID organizationId, String ownerToken) {
        return externalOrganizationPort.subscribeService(organizationId, "COMMERCIAL", ownerToken)
                .then(externalOrganizationPort.subscribeService(organizationId, "RESOURCE", ownerToken));
    }

    private Mono<Fleet> saveFleetLocally(Fleet fleet, UUID managerId, UUID kernelOrganizationId) {
        return saveFleetLocally(fleet, managerId, kernelOrganizationId, managerId);
    }

    private Mono<Fleet> saveFleetLocally(Fleet fleet, UUID managerId, UUID kernelOrganizationId, UUID createdBy) {
        FleetEntity entity = mapper.toEntity(fleet);
        entity.setId(UUID.randomUUID());
        entity.setManagerId(managerId);
        entity.setKernelOrganizationId(kernelOrganizationId);
        entity.setCreatedBy(createdBy);
        entity.setCreatedAt(Instant.now());
        entity.setNew(true);
        return repository.save(entity).map(mapper::toDomain);
    }

    private UUID resolveBusinessActorId() {
        if (businessActorId == null || businessActorId.isBlank()) {
            throw FleetException.invalidResourceStatus(
                    "application.kernel.business-actor-id requis pour créer une organisation Kernel");
        }
        return UUID.fromString(businessActorId);
    }

    private static String toOrganizationCode(String fleetName) {
        String normalized = fleetName.toUpperCase()
                .replaceAll("[^A-Z0-9]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        if (normalized.isBlank()) {
            normalized = "FLEET";
        }
        if (normalized.length() > 16) {
            normalized = normalized.substring(0, 16);
        }
        return normalized + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }

    /**
     * Création par l'administrateur de l'entreprise : la flotte est confiée d'emblée à un
     * gestionnaire actif de l'entreprise, dans la limite du plan, avec un nom unique dans
     * l'entreprise (une flotte sans gestionnaire n'était pilotée par personne).
     */
    @Override
    @Transactional
    public Mono<Fleet> createFleetAsAdmin(Fleet fleet, UUID managerId, UUID createdByAdminId) {
        if (managerId == null) {
            return Mono.error(FleetException.managerRequired());
        }
        return companyScope.requireOwnCompany(createdByAdminId)
                .flatMap(companyId -> assertActiveManagerOfCompany(managerId, companyId)
                        .then(assertNameFree(companyId, fleet.name(), null))
                        .then(planLimitGuard.assertCanCreateFleet(companyId))
                        .then(Mono.defer(() -> {
                            if (!organizationSync || !externalOrganizationPort.isEnabled()) {
                                return Mono.<java.util.Optional<UUID>>just(java.util.Optional.empty());
                            }
                            return kernelTokenHolder.getValidAccessToken()
                                    .flatMap(token -> provisionKernelOrganization(fleet, token))
                                    .map(java.util.Optional::of);
                        }))
                        .flatMap(orgId -> saveFleetLocally(fleet, managerId, orgId.orElse(null), createdByAdminId)
                                .flatMap(saved -> repository.findById(saved.id())
                                        .flatMap(entity -> {
                                            entity.setCompanyId(companyId);
                                            entity.setNew(false);
                                            return repository.save(entity);
                                        })
                                        // L'enveloppe récurrente crée le budget du mois de la flotte.
                                        .flatMap(entity -> budgetUseCase.ensureFleetEnvelope(entity.getId()).thenReturn(entity))
                                        .map(mapper::toDomain))));
    }

    /** Gestionnaire de l'entreprise, compte actif (ni suspendu, ni clôturé). */
    private Mono<Void> assertActiveManagerOfCompany(UUID managerId, UUID companyId) {
        return db.sql("""
                        SELECT fm.created_by_admin_id AS company_id, fm.account_status, u.is_active
                        FROM fleet.fleet_managers fm JOIN fleet.users u ON u.id = fm.user_id
                        WHERE fm.user_id = :id AND u.deleted_at IS NULL
                        """)
                .bind("id", managerId)
                .fetch().one()
                .switchIfEmpty(Mono.error(FleetException.managerNotInCompany()))
                .flatMap(row -> {
                    if (!companyId.equals(row.get("company_id"))) {
                        return Mono.<Void>error(FleetException.managerNotInCompany());
                    }
                    boolean active = "ACTIVE".equals(String.valueOf(row.get("account_status")))
                            && Boolean.TRUE.equals(row.get("is_active"));
                    return active ? Mono.<Void>empty() : Mono.<Void>error(FleetException.managerNotActive());
                });
    }

    /** Nom de flotte unique dans l'entreprise (sans tenir compte de la casse). */
    private Mono<Void> assertNameFree(UUID companyId, String name, UUID excludeFleetId) {
        if (name == null || name.isBlank()) {
            return Mono.error(FleetException.invalidResourceStatus("le nom de la flotte est obligatoire"));
        }
        return repository.findAllByOrganizationAdminId(companyId)
                .filter(f -> !f.getId().equals(excludeFleetId))
                .any(f -> f.getName() != null && f.getName().trim().equalsIgnoreCase(name.trim()))
                .flatMap(taken -> Boolean.TRUE.equals(taken)
                        ? Mono.<Void>error(FleetException.nameTaken(name.trim()))
                        : Mono.<Void>empty());
    }

    /**
     * Transfert contrôlé : flottes de l'entreprise de l'admin, confiées à un gestionnaire actif de
     * cette même entreprise, sans trajet ni affectation ouverts. Véhicules et chauffeurs suivent
     * leur flotte, les plannings non archivés aussi ; dépenses et budgets passés restent attachés
     * à l'ancien gestionnaire (historique). L'ancien « désassigner » laissait des flottes sans
     * responsable et a été retiré.
     */
    @Override
    @Transactional
    public Mono<Void> assignFleetsToManager(java.util.List<UUID> fleetIds, UUID managerId, UUID adminId) {
        return companyScope.requireOwnCompany(adminId)
                .flatMap(companyId -> assertActiveManagerOfCompany(managerId, companyId)
                        .thenMany(repository.findAllById(fleetIds))
                        .filter(f -> f.getDeletedAt() == null)
                        .collectList()
                        .flatMap(fleets -> {
                            if (fleets.size() != new java.util.HashSet<>(fleetIds).size()) {
                                return Mono.<Void>error(FleetException.notFound(null));
                            }
                            return Flux.fromIterable(fleets)
                                    .concatMap(fleet -> transferOne(fleet, managerId, companyId))
                                    .then();
                        }));
    }

    private Mono<Void> transferOne(FleetEntity fleet, UUID targetManagerId, UUID companyId) {
        if (!companyId.equals(fleet.getCompanyId())) {
            return Mono.error(FleetException.accessDenied());
        }
        if (targetManagerId.equals(fleet.getManagerId())) {
            return Mono.error(FleetException.sameManager());
        }
        return fleetHasOpenCommitments(fleet.getId())
                .flatMap(open -> {
                    if (Boolean.TRUE.equals(open)) {
                        return Mono.<Void>error(FleetException.openCommitments(fleet.getName()));
                    }
                    UUID previous = fleet.getManagerId();
                    fleet.setManagerId(targetManagerId);
                    fleet.setNew(false);
                    return repository.save(fleet)
                            .then(vehicleRepo.reassignManagerForFleet(fleet.getId(), targetManagerId))
                            .then(db.sql("""
                                            UPDATE fleet.schedules SET manager_id = :m
                                            WHERE fleet_id = :f AND status <> 'ARCHIVED'
                                            """)
                                    .bind("m", targetManagerId).bind("f", fleet.getId())
                                    .fetch().rowsUpdated())
                            .doOnSuccess(n -> log.info("🔁 Flotte {} transférée de {} à {}",
                                    fleet.getName(), previous, targetManagerId))
                            .then();
                });
    }

    /** Trajet non terminé ou affectation en cours / à venir rattachés à la flotte. */
    private Mono<Boolean> fleetHasOpenCommitments(UUID fleetId) {
        return db.sql("""
                        SELECT EXISTS (SELECT 1 FROM fleet.trips WHERE fleet_id = :f
                                       AND deleted_at IS NULL AND status IN ('SCHEDULED', 'DEPARTED', 'ARRIVED'))
                            OR EXISTS (SELECT 1 FROM fleet.assignments WHERE fleet_id = :f
                                       AND (status = 'IN_PROGRESS' OR (status = 'PENDING' AND end_datetime > NOW()))) AS busy
                        """)
                .bind("f", fleetId)
                .map((row, meta) -> Boolean.TRUE.equals(row.get("busy", Boolean.class)))
                .one()
                .defaultIfEmpty(false);
    }

    @Override
    public Mono<Fleet> getFleetById(UUID id, UUID requesterId, boolean isAdmin) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(FleetException.notFound(id)))
                .filter(f -> f.getDeletedAt() == null)
                .switchIfEmpty(Mono.error(FleetException.notFound(id)))
                .flatMap(f -> accessScope.assertFleetAccess(requesterId, id).thenReturn(f))
                .flatMap(this::enrichFromKernelIfLinked)
                .map(mapper::toDomain);
    }

    /** Vérifie le statut Kernel si la flotte est liée à une organisation. */
    private Mono<FleetEntity> enrichFromKernelIfLinked(FleetEntity entity) {
        if (entity.getKernelOrganizationId() == null || !externalOrganizationPort.isEnabled()) {
            return Mono.just(entity);
        }
        return kernelTokenHolder.getValidAccessToken()
                .flatMap(token -> externalOrganizationPort
                        .getOrganization(entity.getKernelOrganizationId(), token)
                        .doOnNext(org -> log.debug(
                                "🏢 Flotte {} ↔ org Kernel {} ({})",
                                entity.getId(), org.id(), org.governanceStatus()))
                        .thenReturn(entity))
                .onErrorResume(e -> {
                    log.warn("⚠️ Sync org Kernel ignorée pour flotte {} : {}", entity.getId(), e.getMessage());
                    return Mono.just(entity);
                });
    }

    @Override
    public Flux<Fleet> getFleets(UUID requesterId, boolean isAdmin) {
        Flux<FleetEntity> source = isAdmin
                ? repository.findAllByOrganizationAdminId(requesterId)
                : repository.findAllByManagerId(requesterId);
        return source
                .filter(f -> f.getDeletedAt() == null)
                .map(mapper::toDomain);
    }

    @Override
    @Transactional
    public Mono<Fleet> updateFleet(UUID id, Fleet fleet, UUID requesterId, boolean isAdmin) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(FleetException.notFound(id)))
                .flatMap(existing -> accessScope.assertFleetAccess(requesterId, id).thenReturn(existing))
                .flatMap(existing -> (existing.getCompanyId() != null
                        ? assertNameFree(existing.getCompanyId(), fleet.name(), id)
                        : Mono.<Void>empty()).thenReturn(existing))
                .flatMap(existing -> {
                    existing.setName(fleet.name().trim());
                    existing.setPhoneNumber(fleet.phoneNumber());
                    existing.setMonthlyBudget(fleet.monthlyBudget());
                    existing.setNew(false);
                    return repository.save(existing);
                })
                // Une seule source : l'enveloppe récurrente fixe le budget du mois en cours.
                .flatMap(saved -> budgetUseCase.ensureFleetEnvelope(saved.getId()).thenReturn(saved))
                .map(mapper::toDomain);
    }

    @Override
    @Transactional
    public Mono<Void> deleteFleet(UUID id, UUID requesterId, boolean isAdmin) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(FleetException.notFound(id)))
                .filter(f -> f.getDeletedAt() == null)
                .switchIfEmpty(Mono.error(FleetException.notFound(id)))
                .flatMap(fleet -> accessScope.assertFleetAccess(requesterId, id).thenReturn(fleet))
                // Suppression seulement si la flotte est vide (tâche 10.2, doc « uniquement si
                // elle est vide de ressources ») : FleetException.cannotDeleteNotEmpty existait
                // mais n'était plus appelée, laissant véhicules et chauffeurs orphelins.
                .flatMap(fleet -> Mono.zip(vehicleRepo.countByFleetId(id), driverRepo.countByFleetId(id))
                        .flatMap(counts -> counts.getT1() > 0 || counts.getT2() > 0
                                ? Mono.<com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.FleetEntity>error(
                                        FleetException.cannotDeleteNotEmpty())
                                : Mono.just(fleet)))
                .flatMap(fleet -> {
                    fleet.setDeletedAt(java.time.Instant.now());
                    fleet.setNew(false);
                    return repository.save(fleet);
                })
                .then();
    }

    @Override
    public Mono<FleetStatsResponse> getFleetStatistics(UUID fleetId, UUID requesterId, boolean isAdmin) {
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime monthEnd = monthStart.plusMonths(1);
        return getFleetById(fleetId, requesterId, isAdmin)
                .flatMap(fleet -> Mono.zip(
                    driverRepo.countByFleetId(fleetId),
                    tripRepo.getTotalDistanceByFleetId(fleetId).defaultIfEmpty(0.0),
                    vehicleRepo.countByFleetIdAndStatus(fleetId, "AVAILABLE"),
                    vehicleRepo.countByFleetIdAndStatus(fleetId, "ON_TRIP"),
                    vehicleRepo.countByFleetIdAndStatus(fleetId, "MAINTENANCE"),
                    vehicleRepo.countByFleetId(fleetId),
                    expensePersistence.getTotalApprovedByFleetAndMonth(fleetId, monthStart, monthEnd)
                            .defaultIfEmpty(java.math.BigDecimal.ZERO)
                ).map(t -> new FleetStatsResponse(fleetId, t.getT1(), t.getT6(), t.getT2(),
                        Map.of("AVAILABLE", t.getT3(), "ON_TRIP", t.getT4(), "MAINTENANCE", t.getT5()),
                        fleet.monthlyBudget(), t.getT7())));
    }

    // ========================================================================
    // --- 10b. GESTION DU PARC (VEHICULES) ---
    // ========================================================================

    @Override
    public Flux<Vehicle> getFleetVehicles(UUID fleetId, UUID requesterId) {
        return repository.existsByIdAndManagerId(fleetId, requesterId)
                .filter(exists -> exists).switchIfEmpty(Mono.error(FleetException.accessDenied()))
                .thenMany(vehicleRepo.findByFleetId(fleetId))
                .flatMap(v -> vehiclePersistence.getLocalDataById(v.getId()));
    }

    @Override
    @Transactional
    public Mono<Void> assignVehicle(UUID fleetId, UUID vehicleId, UUID requesterId) {
        return repository.existsByIdAndManagerId(fleetId, requesterId)
            .filter(exists -> exists).switchIfEmpty(Mono.error(FleetException.accessDenied()))
            .then(vehiclePersistence.getLocalDataById(vehicleId))
            .switchIfEmpty(Mono.error(FleetException.invalidResourceStatus("Véhicule introuvable")))
            // Le véhicule doit déjà être dans le périmètre (sinon n'importe quel véhicule
            // était « aspiré » par son id) et ne pas changer de flotte avec des trajets ou
            // affectations non terminés rattachés à l'ancienne.
            .flatMap(vehicle -> fleetId.equals(vehicle.fleetId())
                    ? Mono.just(vehicle)
                    : resourceAccess.assertVehicleAccess(requesterId, vehicleId)
                        .then(assertVehicleHasNoOpenCommitments(vehicleId))
                        .thenReturn(vehicle))
            .zipWith(repository.findById(fleetId))
            .flatMap(tuple -> {
                Vehicle vehicle = tuple.getT1();
                // Le gestionnaire du véhicule suit celui de sa nouvelle flotte.
                UUID managerId = tuple.getT2().getManagerId() != null
                        ? tuple.getT2().getManagerId() : vehicle.managerId();
                Vehicle updated = new Vehicle(vehicle.id(), fleetId, managerId, vehicle.currentDriverId(),
                        vehicle.vehicleTypeId(), vehicle.licensePlate(), vehicle.vehicleSerialNumber(),
                        vehicle.brand(), vehicle.model(), vehicle.manufacturingYear(), vehicle.transmissionType(),
                        vehicle.fuelType(), vehicle.tankCapacity(), vehicle.totalSeatNumber(), vehicle.averageFuelConsumption(),
                        vehicle.color(), vehicle.status(), vehicle.photoUrl(), vehicle.serialNumberPhotoUrl(),
                        vehicle.registrationPhotoUrl(), vehicle.illustrationImages(), vehicle.financialParameters(),
                        vehicle.maintenanceParameters(), vehicle.operationalParameters(), vehicle.geofenceRemoteId(),
                        vehicle.kernelResourceId());
                
                return vehiclePersistence.saveLocalData(updated);
            })
            .flatMap(this::syncVehicleWithFleetZones) // Synchronisation Geofence automatique
            .then();
    }

    @Override
    @Transactional
    public Mono<Void> detachVehicle(UUID fleetId, UUID vehicleId, UUID requesterId) {
        return repository.existsByIdAndManagerId(fleetId, requesterId)
                .filter(exists -> exists).switchIfEmpty(Mono.error(FleetException.accessDenied()))
                .then(vehiclePersistence.getLocalDataById(vehicleId))
                .switchIfEmpty(Mono.error(FleetException.invalidResourceStatus("Véhicule introuvable")))
                // Seul un véhicule de CETTE flotte peut en être détaché, et pas avec des
                // trajets ou affectations non terminés rattachés à la flotte.
                .flatMap(vehicle -> fleetId.equals(vehicle.fleetId())
                        ? assertVehicleHasNoOpenCommitments(vehicleId).thenReturn(vehicle)
                        : Mono.error(FleetException.invalidResourceStatus(
                                "Ce véhicule n'appartient pas à cette flotte.")))
                .flatMap(vehicle -> {
                    // On retire le fleetId (devient indépendant)
                    Vehicle updated = new Vehicle(vehicle.id(), null, vehicle.managerId(), vehicle.currentDriverId(),
                            vehicle.vehicleTypeId(), vehicle.licensePlate(), vehicle.vehicleSerialNumber(),
                            vehicle.brand(), vehicle.model(), vehicle.manufacturingYear(), vehicle.transmissionType(),
                            vehicle.fuelType(), vehicle.tankCapacity(), vehicle.totalSeatNumber(), vehicle.averageFuelConsumption(),
                            vehicle.color(), vehicle.status(), vehicle.photoUrl(), vehicle.serialNumberPhotoUrl(),
                            vehicle.registrationPhotoUrl(), vehicle.illustrationImages(), vehicle.financialParameters(),
                            vehicle.maintenanceParameters(), vehicle.operationalParameters(), vehicle.geofenceRemoteId(),
                        vehicle.kernelResourceId());
                    return vehiclePersistence.saveLocalData(updated);
                }).then();
    }

    // ========================================================================
    // --- 10c. GESTION DES CHAUFFEURS ---
    // ========================================================================

    @Override
    public Flux<Driver> getFleetDrivers(UUID fleetId, UUID requesterId) {
        return repository.existsByIdAndManagerId(fleetId, requesterId)
                .filter(exists -> exists).switchIfEmpty(Mono.error(FleetException.accessDenied()))
                .thenMany(driverPersistence.findAllByFleetId(fleetId));
    }

    @Override
    @Transactional
    public Mono<Void> recruitDriver(UUID fleetId, String identifier, UUID managerId) {
        return repository.existsByIdAndManagerId(fleetId, managerId)
                .filter(exists -> exists).switchIfEmpty(Mono.error(FleetException.accessDenied()))
                .then(driverUseCase.searchDriver(identifier))
                .switchIfEmpty(Mono.error(FleetException.recruitmentFailed("Chauffeur introuvable ou non enregistré")))
                // Limite du plan, chauffeur libre ou déjà dans le périmètre (voir DriverService).
                .flatMap(driver -> driverUseCase.attachDriverToFleet(driver.userId(), fleetId, managerId));
    }

    @Override
    @Transactional
    public Mono<Driver> registerDriverInFleet(UUID fleetId, DriverRegistrationRequest request, UUID managerId) {
        // On délègue au driverUseCase qui gère déjà l'Auth et le Profil, mais on force le fleetId
        return driverUseCase.registerDriver(fleetId, request, managerId);
    }

    @Override
    @Transactional
    public Mono<Void> detachDriver(UUID fleetId, UUID driverId, UUID requesterId) {
        // Même règle que DriverService.removeDriverFromFleet (membre de CETTE flotte, sans
        // trajet/affectation non terminé, véhicule désaffecté proprement) : l'ancienne
        // version vidait seulement fleet_id et laissait le véhicule pointer sur le chauffeur.
        return repository.existsByIdAndManagerId(fleetId, requesterId)
                .filter(exists -> exists).switchIfEmpty(Mono.error(FleetException.accessDenied()))
                .then(driverUseCase.removeDriverFromFleet(fleetId, driverId, requesterId));
    }

    private Mono<Void> assertVehicleHasNoOpenCommitments(UUID vehicleId) {
        return availability.vehicleHasOpenCommitments(vehicleId)
                .flatMap(open -> Boolean.TRUE.equals(open)
                        ? Mono.<Void>error(VehicleException.openCommitments())
                        : Mono.<Void>empty());
    }

    // ========================================================================
    // --- HELPERS : SYNCHRONISATION GEOFENCE ---
    // ========================================================================

    private Mono<Vehicle> syncVehicleWithFleetZones(Vehicle vehicle) {
        if (vehicle.fleetId() == null || vehicle.geofenceRemoteId() == null) return Mono.just(vehicle);

        log.info("🛰️ [Geofence Sync] Synchronisation du véhicule {} avec les zones de la flotte {}", 
                vehicle.licensePlate(), vehicle.fleetId());

        return geofencePersistence.findByFleetId(vehicle.fleetId())
                .flatMap(zone -> {
                    log.debug("👉 Ajout véhicule {} -> Zone {}", vehicle.licensePlate(), zone.name());
                    return externalGeofenceApi.addVehicleToZone(vehicle.geofenceRemoteId(), zone.id(), zone.zoneType())
                            .onErrorResume(e -> {
                                log.warn("⚠️ Échec partiel Geofence pour la zone {}: {}", zone.id(), e.getMessage());
                                return Mono.empty();
                            });
                })
                .then(Mono.just(vehicle));
    }
}