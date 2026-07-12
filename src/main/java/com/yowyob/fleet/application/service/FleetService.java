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
import com.yowyob.fleet.infrastructure.mappers.FleetMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    // ========================================================================
    // --- 10a. ADMINISTRATION CRUD ---
    // ========================================================================

    @Override
    @Transactional
    public Mono<Fleet> createFleet(Fleet fleet, UUID managerId) {
        FleetEntity entity = mapper.toEntity(fleet);
        entity.setId(UUID.randomUUID());
        // Si le request contient un managerId spécifique, l'utiliser, sinon
        // l'utilisateur connecté
        entity.setManagerId(fleet.managerId() != null ? fleet.managerId() : managerId);
        entity.setCreatedAt(Instant.now());
        entity.setNew(true);

        return repository.save(entity).map(mapper::toDomain);
    }

    @Override
    public Mono<Fleet> getFleetById(UUID id, UUID requesterId, boolean isAdmin) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(FleetException.notFound(id)))
                .flatMap(f -> {
                    if (f.getManagerId().equals(requesterId)) {
                        return Mono.just(f);
                    }
                    return repository.shareSameCompany(f.getManagerId(), requesterId)
                            .flatMap(share -> {
                                if (share) {
                                    return Mono.just(f);
                                }
                                String reqStr = requesterId.toString();
                                boolean systemAllowed = reqStr.equals("311c6d0d-77ca-4b08-8e65-8bdf8dcb60a2")
                                        || reqStr.equals("a0000002-0000-4000-8000-000000000002")
                                        || reqStr.equals("a0000001-0000-4000-8000-000000000001")
                                        || reqStr.equals("a0000000-0000-4000-8000-000000000101"); // Nehemie Admin Seed
                                                                                                  // ID
                                if (systemAllowed) {
                                    return Mono.just(f);
                                }
                                return Mono.error(FleetException.accessDenied());
                            });
                })
                .map(mapper::toDomain);
    }

    @Override
    public Flux<Fleet> getFleets(UUID requesterId, boolean isAdmin) {
        if (isAdmin) {
            return repository.findAllBySameCompanyAsUser(requesterId)
                    .switchIfEmpty(Flux.defer(() -> repository.findAll()))
                    .map(mapper::toDomain);
        }
        return repository.findAllByManagerId(requesterId).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public Mono<Fleet> updateFleet(UUID id, Fleet fleet, UUID requesterId, boolean isAdmin) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(FleetException.notFound(id)))
                .flatMap(existing -> {
                    Mono<Boolean> hasAccess = Mono.just(existing.getManagerId().equals(requesterId));
                    if (!existing.getManagerId().equals(requesterId)) {
                        hasAccess = repository.shareSameCompany(existing.getManagerId(), requesterId)
                                .map(share -> share
                                        || requesterId.toString().equals("a0000001-0000-4000-8000-000000000001"));
                    }
                    return hasAccess.flatMap(allowed -> {
                        if (!allowed) {
                            return Mono.error(FleetException.accessDenied());
                        }
                        existing.setName(fleet.name());
                        existing.setPhoneNumber(fleet.phoneNumber());
                        if (fleet.managerId() != null) {
                            existing.setManagerId(fleet.managerId());
                        }
                        existing.setNew(false);
                        return repository.save(existing);
                    });
                }).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public Mono<Void> deleteFleet(UUID id, UUID requesterId, boolean isAdmin) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(FleetException.notFound(id)))
                .flatMap(existing -> {
                    Mono<Boolean> hasAccess = Mono.just(existing.getManagerId().equals(requesterId));
                    if (!existing.getManagerId().equals(requesterId)) {
                        hasAccess = repository.shareSameCompany(existing.getManagerId(), requesterId)
                                .map(share -> share
                                        || requesterId.toString().equals("a0000001-0000-4000-8000-000000000001"));
                    }
                    return hasAccess.flatMap(allowed -> {
                        if (!allowed) {
                            return Mono.error(FleetException.accessDenied());
                        }
                        return Mono.zip(vehicleRepo.countByFleetId(id), driverRepo.countByFleetId(id))
                                .flatMap(tuple -> {
                                    if (tuple.getT1() > 0 || tuple.getT2() > 0) {
                                        return Mono.error(FleetException.cannotDeleteNotEmpty());
                                    }
                                    return repository.delete(existing);
                                });
                    });
                });
    }

    @Override
    public Mono<FleetStatsResponse> getFleetStatistics(UUID fleetId, UUID requesterId, boolean isAdmin) {
        return getFleetById(fleetId, requesterId, isAdmin)
                .then(Mono.zip(
                        driverRepo.countByFleetId(fleetId),
                        tripRepo.getTotalDistanceByFleetId(fleetId).defaultIfEmpty(0.0),
                        vehicleRepo.countByFleetIdAndStatus(fleetId, "AVAILABLE"),
                        vehicleRepo.countByFleetIdAndStatus(fleetId, "ON_TRIP"),
                        vehicleRepo.countByFleetIdAndStatus(fleetId, "MAINTENANCE"))
                        .map(t -> new FleetStatsResponse(fleetId, t.getT1(), t.getT2(),
                                Map.of("AVAILABLE", t.getT3(), "ON_TRIP", t.getT4(), "MAINTENANCE", t.getT5()))));
    }

    // ========================================================================
    // --- 10b. GESTION DU PARC (VEHICULES) ---
    // ========================================================================

    private Mono<Void> assertFleetAccess(UUID fleetId, UUID requesterId) {
        return repository.findById(fleetId)
                .switchIfEmpty(Mono.error(FleetException.notFound(fleetId)))
                .flatMap(fleet -> {
                    String reqStr = requesterId.toString();
                    boolean allowed = fleet.getManagerId().equals(requesterId)
                            || reqStr.equals("311c6d0d-77ca-4b08-8e65-8bdf8dcb60a2") // Nehemie
                            || reqStr.equals("a0000002-0000-4000-8000-000000000002") // Marie Admin
                            || reqStr.equals("a0000001-0000-4000-8000-000000000001") // Jean SuperAdmin
                            || reqStr.equals("a0000000-0000-4000-8000-000000000101"); // Nehemie Admin Seed ID
                    if (allowed) {
                        return Mono.empty();
                    }
                    return repository.shareSameCompany(fleet.getManagerId(), requesterId)
                            .flatMap(share -> {
                                if (share) {
                                    return Mono.empty();
                                }
                                return Mono.error(FleetException.accessDenied());
                            });
                });
    }

    @Override
    public Flux<Vehicle> getFleetVehicles(UUID fleetId, UUID requesterId) {
        return assertFleetAccess(fleetId, requesterId)
                .thenMany(vehicleRepo.findByFleetId(fleetId))
                .flatMap(v -> vehiclePersistence.getLocalDataById(v.getId()));
    }

    @Override
    @Transactional
    public Mono<Void> assignVehicle(UUID fleetId, UUID vehicleId, UUID requesterId) {
        return assertFleetAccess(fleetId, requesterId)
                .then(vehiclePersistence.getLocalDataById(vehicleId))
                .switchIfEmpty(Mono.error(FleetException.invalidResourceStatus("Véhicule introuvable")))
                .flatMap(vehicle -> {
                    // Mise à jour de l'assignation
                    Vehicle updated = new Vehicle(vehicle.id(), fleetId, vehicle.managerId(), vehicle.currentDriverId(),
                            vehicle.vehicleTypeId(), vehicle.licensePlate(), vehicle.vehicleSerialNumber(),
                            vehicle.brand(), vehicle.model(), vehicle.manufacturingYear(), vehicle.transmissionType(),
                            vehicle.fuelType(), vehicle.tankCapacity(), vehicle.totalSeatNumber(),
                            vehicle.averageFuelConsumption(),
                            vehicle.color(), vehicle.status(), vehicle.photoUrl(), vehicle.serialNumberPhotoUrl(),
                            vehicle.registrationPhotoUrl(), vehicle.illustrationImages(), vehicle.financialParameters(),
                            vehicle.maintenanceParameters(), vehicle.operationalParameters(),
                            vehicle.geofenceRemoteId());

                    return vehiclePersistence.saveLocalData(updated);
                })
                .flatMap(this::syncVehicleWithFleetZones) // Synchronisation Geofence automatique
                .then();
    }

    @Override
    @Transactional
    public Mono<Void> detachVehicle(UUID fleetId, UUID vehicleId, UUID requesterId) {
        return assertFleetAccess(fleetId, requesterId)
                .then(vehiclePersistence.getLocalDataById(vehicleId))
                .flatMap(vehicle -> {
                    // On retire le fleetId (devient indépendant)
                    Vehicle updated = new Vehicle(vehicle.id(), null, vehicle.managerId(), vehicle.currentDriverId(),
                            vehicle.vehicleTypeId(), vehicle.licensePlate(), vehicle.vehicleSerialNumber(),
                            vehicle.brand(), vehicle.model(), vehicle.manufacturingYear(), vehicle.transmissionType(),
                            vehicle.fuelType(), vehicle.tankCapacity(), vehicle.totalSeatNumber(),
                            vehicle.averageFuelConsumption(),
                            vehicle.color(), vehicle.status(), vehicle.photoUrl(), vehicle.serialNumberPhotoUrl(),
                            vehicle.registrationPhotoUrl(), vehicle.illustrationImages(), vehicle.financialParameters(),
                            vehicle.maintenanceParameters(), vehicle.operationalParameters(),
                            vehicle.geofenceRemoteId());
                    return vehiclePersistence.saveLocalData(updated);
                }).then();
    }

    // ========================================================================
    // --- 10c. GESTION DES CHAUFFEURS ---
    // ========================================================================

    @Override
    public Flux<Driver> getFleetDrivers(UUID fleetId, UUID requesterId) {
        return assertFleetAccess(fleetId, requesterId)
                .thenMany(driverPersistence.findAllByFleetId(fleetId));
    }

    @Override
    @Transactional
    public Mono<Void> recruitDriver(UUID fleetId, String identifier, UUID managerId) {
        return assertFleetAccess(fleetId, managerId)
                .then(driverUseCase.searchDriver(identifier))
                .switchIfEmpty(Mono.error(FleetException.recruitmentFailed("Chauffeur introuvable ou non enregistré")))
                .flatMap(driver -> driverPersistence.updateFleetAssignment(driver.userId(), fleetId));
    }

    @Override
    @Transactional
    public Mono<Driver> registerDriverInFleet(UUID fleetId, DriverRegistrationRequest request, UUID managerId) {
        // On délègue au driverUseCase qui gère déjà l'Auth et le Profil, mais on force
        // le fleetId
        return driverUseCase.registerDriver(fleetId, request, managerId);
    }

    @Override
    @Transactional
    public Mono<Void> detachDriver(UUID fleetId, UUID driverId, UUID requesterId) {
        return assertFleetAccess(fleetId, requesterId)
                .then(driverPersistence.updateFleetAssignment(driverId, null));
    }

    // ========================================================================
    // --- HELPERS : SYNCHRONISATION GEOFENCE ---
    // ========================================================================

    private Mono<Vehicle> syncVehicleWithFleetZones(Vehicle vehicle) {
        if (vehicle.fleetId() == null || vehicle.geofenceRemoteId() == null)
            return Mono.just(vehicle);

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