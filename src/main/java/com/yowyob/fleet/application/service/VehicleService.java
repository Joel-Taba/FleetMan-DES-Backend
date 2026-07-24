package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.VehicleException;
import com.yowyob.fleet.domain.model.Vehicle;
import com.yowyob.fleet.domain.model.VehicleParameters;
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
import com.yowyob.fleet.domain.ports.out.FleetRepositoryPort;     // AJOUTER


import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.VehicleIllustrationImageEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.VehicleIllustrationImageR2dbcRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
    private final VehicleIllustrationImageR2dbcRepository galleryRepo;
    private final PlanLimitGuard planLimitGuard;

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
                    vehicle.geofenceRemoteId(), vehicle.kernelResourceId()
                );
                return localPersistencePort.saveLocalData(updated);
            })
            
            // 3. SYNCHRONISATION GEOFENCE : On récupère toutes les zones de cette flotte
            .flatMap(savedVehicle -> {
                if (savedVehicle.geofenceRemoteId() == null) {
                    log.warn("⚠️ Le véhicule {} n'a pas d'ID Geofence distant. Synchro Geofence ignorée.", savedVehicle.licensePlate());
                    return Mono.empty();
                }

                log.info("🔄 Synchro : Ajout du véhicule {} aux zones de la flotte {}", savedVehicle.licensePlate(), fleetId);

                return geofencePersistencePort.findByFleetId(fleetId) // Récupère les zones locales liées à la flotte
                    .flatMap(zone -> {
                        log.debug("👉 Ajout à la zone : {}", zone.name());
                        return geofencePort.addVehicleToZone(savedVehicle.geofenceRemoteId(), zone.id(), zone.zoneType())
                                .onErrorResume(e -> {
                                    log.error("❌ Échec ajout véhicule à zone {}: {}", zone.id(), e.getMessage());
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
        return getVehicles(requesterId, isAdmin, token, null);
    }

    public Flux<Vehicle> getVehicles(UUID requesterId, boolean isAdmin, String token, UUID fleetId) {
        Flux<Vehicle> localStream = isAdmin ?
                localPersistencePort.getAllVehicles() :
                localPersistencePort.getVehiclesByManager(requesterId);

        if (fleetId != null) {
            localStream = localStream.filter(v -> fleetId.equals(v.fleetId()));
        }

        return localStream.flatMap(v -> getVehicleDetails(v.id(), token)
                .onErrorResume(e -> Mono.just(v)));
    }

    @Override
    @Transactional
    public Mono<Vehicle> createIndependentVehicle(VehicleRequest request, UUID managerId, String token) {
        UUID fleetId = request.fleetId();
        return createVehicle(fleetId, request, managerId, token);
    }

    @Override
    @Transactional
    public Mono<Vehicle> createVehicle(UUID fleetId, VehicleRequest req, UUID managerId, String token) {
        UUID resolvedFleetId = fleetId != null ? fleetId : req.fleetId();
        boolean lookupMode = req.brandId() != null && req.modelId() != null
                && req.fuelTypeId() != null && req.transmissionTypeId() != null && req.colorId() != null;

        Mono<Void> ownership = resolvedFleetId != null
                ? fleetRepository.existsByIdAndManagerId(resolvedFleetId, managerId)
                        .filter(Boolean::booleanValue)
                        .switchIfEmpty(Mono.error(new RuntimeException("Flotte introuvable ou accès refusé.")))
                        .then()
                : Mono.empty();

        return planLimitGuard.assertCanCreateVehicle(managerId)
                .then(ownership)
                .then(Mono.defer(() -> resolveCreateLabels(req, lookupMode)))
                .flatMap(labels -> resolveVehicleTypeId(req)
                        .flatMap(typeId -> resolveRemoteContext(resolvedFleetId)
                .flatMap(context -> externalVehiclePort.createRemoteVehicle(
                    req, token,
                    labels.brand(), labels.model(), labels.fuel(), labels.trans(), labels.color(),
                    context
                ))
                .flatMap(remote -> {
                    UUID kernelResourceId = remote.kernelResourceId() != null
                            ? remote.kernelResourceId() : remote.id();
                    Vehicle shell = new Vehicle(
                        remote.id(), resolvedFleetId, managerId, null, typeId,
                        req.licensePlate(), remote.vehicleSerialNumber(), labels.brand(), labels.model(),
                        req.manufacturingYear(), labels.trans(), labels.fuel(),
                        req.tankCapacity(), req.totalSeatNumber(), req.averageFuelConsumption(),
                        labels.color(), "AVAILABLE", remote.photoUrl(),
                        null, null, Collections.emptyList(), null, null, null, null,
                        kernelResourceId);
                    return localPersistencePort.saveLocalData(shell);
                })
                .flatMap(savedLocal -> geofencePort.registerRemoteVehicle(savedLocal)
                         .flatMap(geofenceRemoteId -> {
                            log.info("✅ Véhicule synchronisé Geofence. RemoteID: {}", geofenceRemoteId);
                            Vehicle updated = savedLocal.withGeofenceRemoteId(geofenceRemoteId);
                            return localPersistencePort.saveLocalData(updated);
                        })
                        .onErrorResume(e -> {
                            log.warn("⚠️ Échec partiel Synchro Geofence: {}", e.getMessage());
                            return Mono.just(savedLocal);
                        })
                )))
                .flatMap(v -> getVehicleDetails(v.id(), token));
    }

    private record CreateLabels(String brand, String model, String fuel, String trans, String color) {}

    private Mono<CreateLabels> resolveCreateLabels(VehicleRequest req, boolean lookupMode) {
        if (!lookupMode) {
            String brand = requireLabel(req.brand(), "brand");
            String model = requireLabel(req.model(), "model");
            return Mono.just(new CreateLabels(
                    brand,
                    model,
                    req.fuelType() != null ? req.fuelType() : "Diesel",
                    req.transmissionType() != null ? req.transmissionType() : "MANUAL",
                    req.color() != null ? req.color() : "Non spécifié"
            ));
        }
        return Mono.zip(
                brandRepo.findById(req.brandId()).map(b -> b.getLabel()).switchIfEmpty(Mono.just("Unknown")),
                modelRepo.findById(req.modelId()).map(m -> m.getLabel()).switchIfEmpty(Mono.just("Unknown")),
                fuelRepo.findById(req.fuelTypeId()).map(f -> f.getLabel()).switchIfEmpty(Mono.just("Diesel")),
                transRepo.findById(req.transmissionTypeId()).map(t -> t.getLabel()).switchIfEmpty(Mono.just("MANUAL")),
                colorRepo.findById(req.colorId()).map(c -> c.getLabel()).switchIfEmpty(Mono.just("Non spécifié"))
        ).map(t -> new CreateLabels(t.getT1(), t.getT2(), t.getT3(), t.getT4(), t.getT5()));
    }

    private Mono<UUID> resolveVehicleTypeId(VehicleRequest req) {
        if (req.vehicleTypeId() != null) {
            return Mono.just(req.vehicleTypeId());
        }
        return vehicleTypeRepo.findAll().next()
                .map(t -> t.getId())
                .switchIfEmpty(Mono.error(VehicleException.invalidVehicleType()));
    }

    private static String requireLabel(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " est obligatoire");
        }
        return value.trim();
    }

    @Override
    @Transactional
    public Mono<Vehicle> patchVehicleInfo(UUID id, Map<String, Object> updates, String token) {
        return localPersistencePort.getLocalDataById(id)
                .switchIfEmpty(Mono.error(VehicleException.notFound(id)))
                .flatMap(local -> externalVehiclePort.patchRemoteVehicle(id, updates, token)
                        .flatMap(remote -> syncLocalCache(remote, id))
                        .switchIfEmpty(Mono.defer(() -> {
                            Vehicle updated = new Vehicle(
                                    local.id(), local.fleetId(), local.managerId(), local.currentDriverId(), local.vehicleTypeId(),
                                    updates.containsKey("licensePlate") ? String.valueOf(updates.get("licensePlate")) : local.licensePlate(),
                                    updates.containsKey("vehicleSerialNumber") ? String.valueOf(updates.get("vehicleSerialNumber")) : local.vehicleSerialNumber(),
                                    updates.containsKey("brand") ? String.valueOf(updates.get("brand")) : local.brand(),
                                    updates.containsKey("model") ? String.valueOf(updates.get("model")) : local.model(),
                                    updates.containsKey("manufacturingYear") ? toInt(updates.get("manufacturingYear")) : local.manufacturingYear(),
                                    updates.containsKey("transmissionType") ? String.valueOf(updates.get("transmissionType")) : local.transmissionType(),
                                    updates.containsKey("fuelType") ? String.valueOf(updates.get("fuelType")) : local.fuelType(),
                                    local.tankCapacity(), local.totalSeatNumber(), local.averageFuelConsumption(),
                                    updates.containsKey("color") ? String.valueOf(updates.get("color")) : local.color(),
                                    updates.containsKey("status") ? String.valueOf(updates.get("status")) : local.status(),
                                    updates.containsKey("photoUrl") ? (updates.get("photoUrl") == null ? null : String.valueOf(updates.get("photoUrl"))) : local.photoUrl(),
                                    local.serialNumberPhotoUrl(), local.registrationPhotoUrl(),
                                    local.illustrationImages(), local.financialParameters(),
                                    local.maintenanceParameters(), local.operationalParameters(),
                                    local.geofenceRemoteId(), local.kernelResourceId()
                            );
                            UUID fleetIdUpdate = toUuid(updates.get("fleetId"));
                            if (fleetIdUpdate != null) {
                                updated = new Vehicle(
                                        updated.id(), fleetIdUpdate, updated.managerId(), updated.currentDriverId(), updated.vehicleTypeId(),
                                        updated.licensePlate(), updated.vehicleSerialNumber(), updated.brand(), updated.model(),
                                        updated.manufacturingYear(), updated.transmissionType(), updated.fuelType(),
                                        updated.tankCapacity(), updated.totalSeatNumber(), updated.averageFuelConsumption(),
                                        updated.color(), updated.status(), updated.photoUrl(),
                                        updated.serialNumberPhotoUrl(), updated.registrationPhotoUrl(),
                                        updated.illustrationImages(), updated.financialParameters(),
                                        updated.maintenanceParameters(), updated.operationalParameters(),
                                        updated.geofenceRemoteId(), updated.kernelResourceId()
                                );
                            }
                            return localPersistencePort.saveLocalData(updated);
                        })));
    }

    @Override
    @Transactional
    public Mono<Vehicle> updateVehicleGallery(UUID vehicleId, String photoUrl, java.util.List<String> galleryUrls, String token) {
        Mono<Void> photoUpdate = photoUrl != null
                ? localPersistencePort.updateVehiclePhotos(vehicleId, photoUrl, null, null)
                : Mono.empty();
        Mono<Void> galleryUpdate = galleryUrls != null
                ? replaceGallery(vehicleId, galleryUrls)
                : Mono.empty();
        return photoUpdate.then(galleryUpdate).then(getVehicleDetails(vehicleId, token));
    }

    private Mono<Void> replaceGallery(UUID vehicleId, java.util.List<String> galleryUrls) {
        return galleryRepo.findByVehicleId(vehicleId)
                .flatMap(img -> galleryRepo.deleteById(img.getId()))
                .then(Flux.fromIterable(galleryUrls == null ? List.of() : galleryUrls)
                        .filter(url -> url != null && !url.isBlank())
                        .concatMap(url -> galleryRepo.save(new VehicleIllustrationImageEntity(UUID.randomUUID(), vehicleId, url, true)))
                        .then());
    }

    private static Integer toInt(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.intValue();
        try { return Integer.parseInt(value.toString()); } catch (NumberFormatException e) { return null; }
    }

    private static UUID toUuid(Object value) {
        if (value == null) return null;
        if (value instanceof UUID u) return u;
        try { return UUID.fromString(value.toString()); } catch (Exception e) { return null; }
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
                        v.operationalParameters(), v.geofenceRemoteId(), v.kernelResourceId()
                    );
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
                        v.operationalParameters(), v.geofenceRemoteId(), v.kernelResourceId()
                    );
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
                        null
                ));
    }

    @Override
    @Transactional
    public Mono<Void> updateOperationalData(UUID vehicleId, Map<String, Object> updates) {
        return operationalRepo.findByVehicleId(vehicleId)
                .flatMap(e -> {
                    if (updates.containsKey("fuelLevel")) e.setFuelLevel(updates.get("fuelLevel").toString());
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
            case "vehicle-types"  -> vehicleTypeRepo.findAll().map(t -> Map.of("id", t.getId(), "label", t.getLabel(), "code", t.getCode()));
            case "manufacturers"  -> mfrRepo.findAll().map(m -> Map.of("id", m.getId(), "label", m.getLabel(), "code", m.getCode()));
            case "brands"         -> brandRepo.findAll().map(b -> Map.of("id", b.getId(), "label", b.getLabel(), "code", b.getCode()));
            case "models"         -> modelRepo.findAll().map(m -> Map.of("id", m.getId(), "label", m.getLabel(), "code", m.getCode()));
            case "sizes"          -> sizeRepo.findAll().map(s -> Map.of("id", s.getId(), "label", s.getLabel(), "code", s.getCode()));
            case "usages"         -> usageRepo.findAll().map(u -> Map.of("id", u.getId(), "label", u.getLabel(), "code", u.getCode()));
            case "fuel-types"     -> fuelRepo.findAll().map(f -> Map.of("id", f.getId(), "label", f.getLabel(), "code", f.getCode()));
            case "transmissions"  -> transRepo.findAll().map(t -> Map.of("id", t.getId(), "label", t.getLabel(), "code", t.getCode()));
            case "colors"         -> colorRepo.findAll().map(c -> Map.of("id", c.getId(), "label", c.getLabel(), "code", c.getCode()));
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
            colorRepo.findAll().collectList()
        )
        .cast(Object[].class)
        .map(args -> {
            Map<String, Object> catalog = new HashMap<>();
            catalog.put("vehicleTypes",      args[0]);
            catalog.put("manufacturers",     args[1]);
            catalog.put("brands",            args[2]);
            catalog.put("models",            args[3]);
            catalog.put("sizes",             args[4]);
            catalog.put("usages",            args[5]);
            catalog.put("fuelTypes",         args[6]);
            catalog.put("transmissionTypes", args[7]);
            catalog.put("colors",            args[8]);
            return catalog;
        });
    }

    // ========================================================================
    // --- LOGIQUE DE SYNCHRONISATION DU CACHE ---
    // ========================================================================

    private Mono<ExternalVehiclePort.VehicleRemoteContext> resolveRemoteContext(UUID fleetId) {
        if (fleetId == null) {
            return Mono.just(ExternalVehiclePort.VehicleRemoteContext.empty());
        }
        return fleetRepository.findById(fleetId)
                .map(f -> new ExternalVehiclePort.VehicleRemoteContext(f.kernelOrganizationId(), null))
                .defaultIfEmpty(ExternalVehiclePort.VehicleRemoteContext.empty());
    }

    private Mono<Vehicle> syncLocalCache(Vehicle remote, UUID vehicleId) {
        return localPersistencePort.getLocalDataById(vehicleId)
                .flatMap(local -> {
                    Vehicle updated = new Vehicle(
                        local.id(), local.fleetId(), local.managerId(), local.currentDriverId(), local.vehicleTypeId(),
                        remote.licensePlate() != null ? remote.licensePlate() : local.licensePlate(),
                        remote.vehicleSerialNumber() != null ? remote.vehicleSerialNumber() : local.vehicleSerialNumber(),
                        remote.brand() != null ? remote.brand() : local.brand(),
                        remote.model() != null ? remote.model() : local.model(),
                        local.manufacturingYear(),
                        remote.transmissionType() != null ? remote.transmissionType() : local.transmissionType(),
                        remote.fuelType() != null ? remote.fuelType() : local.fuelType(),
                        remote.tankCapacity() != null ? remote.tankCapacity() : local.tankCapacity(),
                        remote.totalSeatNumber() != null ? remote.totalSeatNumber() : local.totalSeatNumber(),
                        remote.averageFuelConsumption() != null ? remote.averageFuelConsumption() : local.averageFuelConsumption(),
                        local.color(), local.status(),
                        remote.photoUrl() != null ? remote.photoUrl() : local.photoUrl(),
                        remote.serialNumberPhotoUrl() != null ? remote.serialNumberPhotoUrl() : local.serialNumberPhotoUrl(),
                        remote.registrationPhotoUrl() != null ? remote.registrationPhotoUrl() : local.registrationPhotoUrl(),
                        local.illustrationImages(), local.financialParameters(),
                        local.maintenanceParameters(), local.operationalParameters(),
                        local.geofenceRemoteId(),
                        local.kernelResourceId()
                    );
                    return localPersistencePort.saveLocalData(updated);
                });
    }
    

    }