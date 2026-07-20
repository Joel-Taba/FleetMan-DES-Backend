package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.FleetException;
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
        FleetEntity entity = mapper.toEntity(fleet);
        entity.setId(UUID.randomUUID());
        entity.setManagerId(managerId);
        entity.setKernelOrganizationId(kernelOrganizationId);
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

    @Override
    @Transactional
    public Mono<Fleet> createFleetAsAdmin(Fleet fleet, UUID managerId) {
        // Création administrative : ni limite de plan (le manager n'a pas encore
        // forcément la flotte à sa charge), ni provisioning d'organisation Kernel
        // (réservé au flux d'auto-inscription du gestionnaire) — juste l'entité
        // locale, assignée ou non à ce stade.
        return saveFleetLocally(fleet, managerId, null);
    }

    @Override
    @Transactional
    public Mono<Void> assignFleetsToManager(java.util.List<UUID> fleetIds, UUID managerId) {
        return repository.findAllById(fleetIds)
                .flatMap(entity -> {
                    entity.setManagerId(managerId);
                    entity.setNew(false);
                    return repository.save(entity);
                })
                .then();
    }

    @Override
    public Mono<Fleet> getFleetById(UUID id, UUID requesterId, boolean isAdmin) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(FleetException.notFound(id)))
                .filter(f -> isAdmin || f.getManagerId().equals(requesterId))
                .switchIfEmpty(Mono.error(FleetException.accessDenied()))
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
        return isAdmin ? repository.findAll().map(mapper::toDomain) 
                       : repository.findAllByManagerId(requesterId).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public Mono<Fleet> updateFleet(UUID id, Fleet fleet, UUID requesterId, boolean isAdmin) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(FleetException.notFound(id)))
                .filter(f -> isAdmin || f.getManagerId().equals(requesterId))
                .flatMap(existing -> {
                    existing.setName(fleet.name());
                    existing.setPhoneNumber(fleet.phoneNumber());
                    existing.setNew(false);
                    return repository.save(existing);
                }).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public Mono<Void> deleteFleet(UUID id, UUID requesterId, boolean isAdmin) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(FleetException.notFound(id)))
                .filter(f -> isAdmin || f.getManagerId().equals(requesterId))
                .flatMap(fleet -> 
                    // Sécurité : Vérifier si la flotte est vide
                    Mono.zip(vehicleRepo.countByFleetId(id), driverRepo.countByFleetId(id))
                        .flatMap(tuple -> {
                            if (tuple.getT1() > 0 || tuple.getT2() > 0) {
                                return Mono.error(FleetException.cannotDeleteNotEmpty());
                            }
                            return repository.delete(fleet);
                        })
                );
    }

    @Override
    public Mono<FleetStatsResponse> getFleetStatistics(UUID fleetId, UUID requesterId, boolean isAdmin) {
        return getFleetById(fleetId, requesterId, isAdmin)
                .then(Mono.zip(
                    driverRepo.countByFleetId(fleetId),
                    tripRepo.getTotalDistanceByFleetId(fleetId).defaultIfEmpty(0.0),
                    vehicleRepo.countByFleetIdAndStatus(fleetId, "AVAILABLE"),
                    vehicleRepo.countByFleetIdAndStatus(fleetId, "ON_TRIP"),
                    vehicleRepo.countByFleetIdAndStatus(fleetId, "MAINTENANCE")
                ).map(t -> new FleetStatsResponse(fleetId, t.getT1(), t.getT2(), 
                        Map.of("AVAILABLE", t.getT3(), "ON_TRIP", t.getT4(), "MAINTENANCE", t.getT5()))));
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
            .flatMap(vehicle -> {
                // Mise à jour de l'assignation
                Vehicle updated = new Vehicle(vehicle.id(), fleetId, vehicle.managerId(), vehicle.currentDriverId(),
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
                .flatMap(driver -> driverPersistence.updateFleetAssignment(driver.userId(), fleetId));
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
        return repository.existsByIdAndManagerId(fleetId, requesterId)
                .filter(exists -> exists).switchIfEmpty(Mono.error(FleetException.accessDenied()))
                .then(driverPersistence.updateFleetAssignment(driverId, null));
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