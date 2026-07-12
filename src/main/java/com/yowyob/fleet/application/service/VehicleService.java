package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.VehicleException;
import com.yowyob.fleet.domain.model.Vehicle;
import com.yowyob.fleet.domain.model.VehicleParameters;
import com.yowyob.fleet.domain.model.Driver;
import com.yowyob.fleet.domain.ports.in.ManageVehicleUseCase;
import com.yowyob.fleet.domain.ports.out.ExternalGeofencePort;
import com.yowyob.fleet.domain.ports.out.ExternalVehiclePort;
import com.yowyob.fleet.domain.ports.out.VehiclePersistencePort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.VehicleRequest;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.OperationalParameterR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.VehicleTypeR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.resources.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.yowyob.fleet.domain.ports.out.GeofencePersistencePort; // AJOUTER
import com.yowyob.fleet.domain.ports.out.FleetRepositoryPort; // AJOUTER
import com.yowyob.fleet.domain.ports.out.DriverPersistencePort;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VehicleService implements ManageVehicleUseCase {

    private final VehiclePersistencePort localPersistencePort;
    private final ExternalVehiclePort externalVehiclePort;
    private final ExternalGeofencePort geofencePort;
    // Ajout des ports nécessaires pour la validation et la récupération des zones
    private final GeofencePersistencePort geofencePersistencePort;
    private final FleetRepositoryPort fleetRepository;
    private final DriverPersistencePort driverPersistencePort;

    // Repositories des ressources (Souveraineté)
    private final VehicleTypeR2dbcRepository vehicleTypeRepo;
    private final ManufacturerR2dbcRepository mfrRepo;
    private final BrandR2dbcRepository brandRepo;
    private final VehicleModelR2dbcRepository modelRepo;
    private final VehicleSizeR2dbcRepository sizeRepo;
    private final UsageTypeR2dbcRepository usageRepo;
    private final FuelTypeR2dbcRepository fuelRepo;
    private final TransmissionTypeR2dbcRepository transRepo;
    private final VehicleColorR2dbcRepository colorRepo;

    private final OperationalParameterR2dbcRepository operationalRepo;

    // ========================================================================
    // --- 09a. GESTION DU PARC (FLEET MANAGER) ---
    // ========================================================================
    // --- NOUVELLE MÉTHODE : SENS A (Véhicule -> Flotte -> Zones) ---
    @Override
    @Transactional
    public Mono<Void> assignVehicleToFleet(UUID fleetId, UUID vehicleId, UUID managerId) {
        // 1. Vérification : La flotte existe et appartient au manager
        return fleetRepository.existsByIdAndManagerId(fleetId, managerId)
                .filter(exists -> exists)
                .switchIfEmpty(Mono.error(new RuntimeException("Flotte introuvable ou accès refusé.")))

                // 2. Récupération et mise à jour du véhicule local
                .then(localPersistencePort.getLocalDataById(vehicleId))
                .switchIfEmpty(Mono.error(VehicleException.notFound(vehicleId)))
                .flatMap(vehicle -> {
                    // On met à jour le fleetId dans l'objet local
                    Vehicle updated = new Vehicle(
                            vehicle.id(), fleetId, // Affectation FleetID
                            vehicle.managerId(), vehicle.currentDriverId(), vehicle.vehicleTypeId(),
                            vehicle.licensePlate(), vehicle.vehicleSerialNumber(), vehicle.brand(), vehicle.model(),
                            vehicle.manufacturingYear(), vehicle.transmissionType(), vehicle.fuelType(),
                            vehicle.tankCapacity(), vehicle.totalSeatNumber(), vehicle.averageFuelConsumption(),
                            vehicle.color(), vehicle.status(), vehicle.photoUrl(),
                            vehicle.serialNumberPhotoUrl(), vehicle.registrationPhotoUrl(),
                            vehicle.illustrationImages(), vehicle.financialParameters(),
                            vehicle.maintenanceParameters(), vehicle.operationalParameters(),
                            vehicle.geofenceRemoteId());
                    return localPersistencePort.saveLocalData(updated);
                })

                // 3. SYNCHRONISATION GEOFENCE : On récupère toutes les zones de cette flotte
                .flatMap(savedVehicle -> {
                    if (savedVehicle.geofenceRemoteId() == null) {
                        log.warn("⚠️ Le véhicule {} n'a pas d'ID Geofence distant. Synchro Geofence ignorée.",
                                savedVehicle.licensePlate());
                        return Mono.empty();
                    }

                    log.info("🔄 Synchro : Ajout du véhicule {} aux zones de la flotte {}", savedVehicle.licensePlate(),
                            fleetId);

                    return geofencePersistencePort.findByFleetId(fleetId) // Récupère les zones locales liées à la
                                                                          // flotte
                            .flatMap(zone -> {
                                log.debug("👉 Ajout à la zone : {}", zone.name());
                                return geofencePort
                                        .addVehicleToZone(savedVehicle.geofenceRemoteId(), zone.id(), zone.zoneType())
                                        .onErrorResume(e -> {
                                            log.error("❌ Échec ajout véhicule à zone {}: {}", zone.id(),
                                                    e.getMessage());
                                            return Mono.empty(); // On continue pour les autres zones
                                        });
                            })
                            .then();
                });
    }

    @Override
    public Mono<Vehicle> getVehicleDetails(UUID vehicleId, String token) {
        return externalVehiclePort.getExternalVehicleInfo(vehicleId, token)
                .flatMap(remote -> syncLocalCache(remote, vehicleId))
                .switchIfEmpty(localPersistencePort.getLocalDataById(vehicleId))
                .switchIfEmpty(Mono.error(VehicleException.notFound(vehicleId)));
    }

    @Override
    public Flux<Vehicle> getVehicles(UUID requesterId, boolean isAdmin, String token) {
        Flux<Vehicle> localStream;
        if (isAdmin) {
            localStream = localPersistencePort.getVehiclesByCompanyOfUser(requesterId)
                    .switchIfEmpty(Flux.defer(() -> localPersistencePort.getAllVehicles()));
        } else {
            localStream = driverPersistencePort.findById(requesterId)
                    .flatMapMany((Driver driver) -> {
                        Flux<Vehicle> vehicles = localPersistencePort.getAllVehicles()
                                .filter(v -> requesterId.equals(v.currentDriverId()));
                        if (driver.assignedVehicleId() != null) {
                            Mono<Vehicle> primaryVehicle = localPersistencePort
                                    .getLocalDataById(driver.assignedVehicleId());
                            vehicles = Flux.concat(primaryVehicle, vehicles).distinct(Vehicle::id);
                        }
                        return vehicles;
                    })
                    .switchIfEmpty(localPersistencePort.getVehiclesByManager(requesterId));
        }

        return localStream.flatMap(v -> getVehicleDetails(v.id(), token)
                .onErrorResume(e -> Mono.just(v)));
    }

    @Override
    @Transactional
    public Mono<Vehicle> createIndependentVehicle(VehicleRequest request, UUID managerId, String token) {
        return createVehicle(null, request, managerId, token);
    }

    @Override
    @Transactional
    public Mono<Vehicle> createVehicle(UUID fleetId, VehicleRequest req, UUID managerId, String token) {
        return Mono.zip(
                args -> args, // Combinateur : on retourne le tableau brut
                vehicleTypeRepo.findById(req.vehicleTypeId())
                        .switchIfEmpty(Mono.error(VehicleException.invalidVehicleType())),
                mfrRepo.findById(req.manufacturerId()),
                brandRepo.findById(req.brandId()),
                modelRepo.findById(req.modelId()),
                sizeRepo.findById(req.sizeId()),
                usageRepo.findById(req.usageTypeId()),
                fuelRepo.findById(req.fuelTypeId()),
                transRepo.findById(req.transmissionTypeId()),
                colorRepo.findById(req.colorId()))
                .cast(Object[].class)
                .flatMap(args -> {
                    // On récupère les entités par leur index (0 à 8)
                    var typeEntity = (com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.VehicleTypeEntity) args[0]; // Attention
                                                                                                                                       // au
                                                                                                                                       // type
                                                                                                                                       // si
                                                                                                                                       // different
                    var brandLabel = ((com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.resources.BrandEntity) args[2])
                            .getLabel();
                    var modelLabel = ((com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.resources.VehicleModelEntity) args[3])
                            .getLabel();
                    var fuelLabel = ((com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.resources.FuelTypeEntity) args[6])
                            .getLabel();
                    var transLabel = ((com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.resources.TransmissionTypeEntity) args[7])
                            .getLabel();
                    var colorLabel = ((com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.resources.VehicleColorEntity) args[8])
                            .getLabel();

                    return externalVehiclePort.createRemoteVehicle(
                            req,
                            token,
                            brandLabel,
                            modelLabel,
                            fuelLabel,
                            transLabel,
                            colorLabel)
                            .flatMap(remote -> {
                                Vehicle shell = new Vehicle(
                                        remote.id(), fleetId, managerId, null, req.vehicleTypeId(),
                                        req.licensePlate(), remote.vehicleSerialNumber(), brandLabel, modelLabel,
                                        req.manufacturingYear(), transLabel, fuelLabel,
                                        req.tankCapacity(), req.totalSeatNumber(), req.averageFuelConsumption(),
                                        colorLabel, "AVAILABLE", remote.photoUrl(),
                                        null, null, Collections.emptyList(), null, null, null, null);

                                return localPersistencePort.saveLocalData(shell);
                            })
                            .flatMap(savedLocal -> geofencePort.registerRemoteVehicle(savedLocal)
                                    .flatMap(geofenceRemoteId -> {
                                        log.info("✅ Véhicule synchronisé Geofence. RemoteID: {}", geofenceRemoteId);
                                        // Mise à jour de l'ID distant localement
                                        Vehicle updated = savedLocal.withGeofenceRemoteId(geofenceRemoteId);
                                        return localPersistencePort.saveLocalData(updated);
                                    })
                                    .onErrorResume(e -> {
                                        log.warn(
                                                "⚠️ Échec partiel Synchro Geofence (Le véhicule est créé mais pas lié au moteur geo): {}",
                                                e.getMessage());
                                        return Mono.just(savedLocal);
                                    }));
                }).flatMap(v -> getVehicleDetails(v.id(), token));
    }

    @Override
    @Transactional
    public Mono<Vehicle> patchVehicleInfo(UUID id, Map<String, Object> updates, String token) {
        return externalVehiclePort.patchRemoteVehicle(id, updates, token)
                .flatMap(remote -> syncLocalCache(remote, id))
                .onErrorResume(e -> {
                    log.warn("⚠️ Remote vehicle patch failed or offline: {}. Performing local database patch.",
                            e.getMessage());
                    return Mono.empty();
                })
                .switchIfEmpty(
                        localPersistencePort.getLocalDataById(id)
                                .flatMap(v -> {
                                    Vehicle updated = new Vehicle(
                                            v.id(), v.fleetId(), v.managerId(), v.currentDriverId(), v.vehicleTypeId(),
                                            updates.containsKey("licensePlate") ? (String) updates.get("licensePlate")
                                                    : v.licensePlate(),
                                            updates.containsKey("vehicleSerialNumber")
                                                    ? (String) updates.get("vehicleSerialNumber")
                                                    : v.vehicleSerialNumber(),
                                            updates.containsKey("brand") ? (String) updates.get("brand") : v.brand(),
                                            updates.containsKey("model") ? (String) updates.get("model") : v.model(),
                                            v.manufacturingYear(),
                                            v.transmissionType(),
                                            v.fuelType(),
                                            v.tankCapacity(),
                                            v.totalSeatNumber(),
                                            v.averageFuelConsumption(),
                                            updates.containsKey("color") ? (String) updates.get("color") : v.color(),
                                            updates.containsKey("status") ? (String) updates.get("status") : v.status(),
                                            v.photoUrl(),
                                            v.serialNumberPhotoUrl(),
                                            v.registrationPhotoUrl(),
                                            v.illustrationImages(),
                                            v.financialParameters(),
                                            v.maintenanceParameters(),
                                            v.operationalParameters(),
                                            v.geofenceRemoteId());
                                    return localPersistencePort.saveLocalData(updated);
                                }));
    }

    @Override
    @Transactional
    public Mono<Vehicle> updateFinancialParameters(UUID id, VehicleParameters.Financial p, String t) {
        return localPersistencePort.getLocalDataById(id)
                .flatMap(v -> {
                    Vehicle updated = new Vehicle(
                            v.id(), v.fleetId(), v.managerId(), v.currentDriverId(), v.vehicleTypeId(),
                            v.licensePlate(), v.vehicleSerialNumber(), v.brand(), v.model(),
                            v.manufacturingYear(), v.transmissionType(), v.fuelType(),
                            v.tankCapacity(), v.totalSeatNumber(), v.averageFuelConsumption(),
                            v.color(), v.status(), v.photoUrl(), v.serialNumberPhotoUrl(),
                            v.registrationPhotoUrl(), v.illustrationImages(), p, v.maintenanceParameters(),
                            v.operationalParameters(), v.geofenceRemoteId());
                    return localPersistencePort.saveLocalData(updated);
                }).then(getVehicleDetails(id, t));
    }

    @Override
    @Transactional
    public Mono<Vehicle> updateMaintenanceParameters(UUID id, VehicleParameters.Maintenance p, String t) {
        return localPersistencePort.getLocalDataById(id)
                .flatMap(v -> {
                    Vehicle updated = new Vehicle(
                            v.id(), v.fleetId(), v.managerId(), v.currentDriverId(), v.vehicleTypeId(),
                            v.licensePlate(), v.vehicleSerialNumber(), v.brand(), v.model(),
                            v.manufacturingYear(), v.transmissionType(), v.fuelType(),
                            v.tankCapacity(), v.totalSeatNumber(), v.averageFuelConsumption(),
                            v.color(), v.status(), v.photoUrl(), v.serialNumberPhotoUrl(),
                            v.registrationPhotoUrl(), v.illustrationImages(), v.financialParameters(), p,
                            v.operationalParameters(), v.geofenceRemoteId());
                    return localPersistencePort.saveLocalData(updated);
                }).then(getVehicleDetails(id, t));
    }

    @Override
    public Mono<Void> removeVehicle(UUID id, String t) {
        return externalVehiclePort.deleteRemoteVehicle(id, t)
                .then(localPersistencePort.deleteLocalData(id));
    }

    // ========================================================================
    // --- 09c. OPÉRATIONNEL (DRIVER) ---
    // ========================================================================

    @Override
    public Mono<VehicleParameters.Operational> getOperationalData(UUID vehicleId) {
        return operationalRepo.findByVehicleId(vehicleId)
                .map(e -> new VehicleParameters.Operational(
                        e.getStatut(),
                        e.getCurrentSpeed() != null ? e.getCurrentSpeed().floatValue() : 0.0f,
                        e.getFuelLevel(),
                        e.getMileage() != null ? e.getMileage().floatValue() : 0.0f,
                        e.getOdometerReading() != null ? e.getOdometerReading().floatValue() : 0.0f,
                        e.getBearing() != null ? e.getBearing().floatValue() : 0.0f,
                        e.getTimestamp(),
                        null));
    }

    @Override
    @Transactional
    public Mono<Void> updateOperationalData(UUID vehicleId, Map<String, Object> updates) {
        return operationalRepo.findByVehicleId(vehicleId)
                .flatMap(e -> {
                    if (updates.containsKey("fuelLevel"))
                        e.setFuelLevel(updates.get("fuelLevel").toString());
                    if (updates.containsKey("odometerReading")) {
                        e.setOdometerReading(new BigDecimal(updates.get("odometerReading").toString()));
                    }
                    e.setTimestamp(Instant.now());
                    return operationalRepo.save(e);
                }).then();
    }

    // ========================================================================
    // --- 09d. RÉFÉRENTIELS (LOOKUP MANAGER) ---
    // ========================================================================

    @Override
    public Flux<Map<String, Object>> getLocalLookupData(String resource) {
        return switch (resource.toLowerCase()) {
            case "vehicle-types" ->
                vehicleTypeRepo.findAll().map(t -> Map.of("id", t.getId(), "label", t.getLabel(), "code", t.getCode()));
            case "manufacturers" ->
                mfrRepo.findAll().map(m -> Map.of("id", m.getId(), "label", m.getLabel(), "code", m.getCode()));
            case "brands" ->
                brandRepo.findAll().map(b -> Map.of("id", b.getId(), "label", b.getLabel(), "code", b.getCode()));
            case "models" ->
                modelRepo.findAll().map(m -> Map.of("id", m.getId(), "label", m.getLabel(), "code", m.getCode()));
            case "sizes" ->
                sizeRepo.findAll().map(s -> Map.of("id", s.getId(), "label", s.getLabel(), "code", s.getCode()));
            case "usages" ->
                usageRepo.findAll().map(u -> Map.of("id", u.getId(), "label", u.getLabel(), "code", u.getCode()));
            case "fuel-types" ->
                fuelRepo.findAll().map(f -> Map.of("id", f.getId(), "label", f.getLabel(), "code", f.getCode()));
            case "transmissions" ->
                transRepo.findAll().map(t -> Map.of("id", t.getId(), "label", t.getLabel(), "code", t.getCode()));
            case "colors" ->
                colorRepo.findAll().map(c -> Map.of("id", c.getId(), "label", c.getLabel(), "code", c.getCode()));
            default -> Flux.error(VehicleException.invalidResource());
        };
    }

    @Override
    public Mono<Map<String, Object>> getAllResourcesCatalog() {
        return Mono.zip(
                args -> args, // Combinateur
                vehicleTypeRepo.findAll().collectList(),
                mfrRepo.findAll().collectList(),
                brandRepo.findAll().collectList(),
                modelRepo.findAll().collectList(),
                sizeRepo.findAll().collectList(),
                usageRepo.findAll().collectList(),
                fuelRepo.findAll().collectList(),
                transRepo.findAll().collectList(),
                colorRepo.findAll().collectList())
                .cast(Object[].class)
                .map(args -> {
                    Map<String, Object> catalog = new HashMap<>();
                    catalog.put("vehicleTypes", args[0]);
                    catalog.put("manufacturers", args[1]);
                    catalog.put("brands", args[2]);
                    catalog.put("models", args[3]);
                    catalog.put("sizes", args[4]);
                    catalog.put("usages", args[5]);
                    catalog.put("fuelTypes", args[6]);
                    catalog.put("transmissionTypes", args[7]);
                    catalog.put("colors", args[8]);
                    return catalog;
                });
    }

    // ========================================================================
    // --- LOGIQUE DE SYNCHRONISATION DU CACHE ---
    // ========================================================================

    private Mono<Vehicle> syncLocalCache(Vehicle remote, UUID vehicleId) {
        return localPersistencePort.getLocalDataById(vehicleId)
                .flatMap(local -> {
                    Vehicle updated = new Vehicle(
                            local.id(), local.fleetId(), local.managerId(), local.currentDriverId(),
                            local.vehicleTypeId(),
                            remote.licensePlate() != null ? remote.licensePlate() : local.licensePlate(),
                            remote.vehicleSerialNumber() != null ? remote.vehicleSerialNumber()
                                    : local.vehicleSerialNumber(),
                            remote.brand() != null ? remote.brand() : local.brand(),
                            remote.model() != null ? remote.model() : local.model(),
                            local.manufacturingYear(),
                            remote.transmissionType() != null ? remote.transmissionType() : local.transmissionType(),
                            remote.fuelType() != null ? remote.fuelType() : local.fuelType(),
                            remote.tankCapacity() != null ? remote.tankCapacity() : local.tankCapacity(),
                            remote.totalSeatNumber() != null ? remote.totalSeatNumber() : local.totalSeatNumber(),
                            remote.averageFuelConsumption() != null ? remote.averageFuelConsumption()
                                    : local.averageFuelConsumption(),
                            remote.color() != null ? remote.color() : local.color(),
                            remote.status() != null ? remote.status() : local.status(),
                            remote.photoUrl() != null ? remote.photoUrl() : local.photoUrl(),
                            remote.serialNumberPhotoUrl() != null ? remote.serialNumberPhotoUrl()
                                    : local.serialNumberPhotoUrl(),
                            remote.registrationPhotoUrl() != null ? remote.registrationPhotoUrl()
                                    : local.registrationPhotoUrl(),
                            local.illustrationImages(), local.financialParameters(),
                            local.maintenanceParameters(), local.operationalParameters(),
                            local.geofenceRemoteId());
                    return localPersistencePort.saveLocalData(updated);
                });
    }

}