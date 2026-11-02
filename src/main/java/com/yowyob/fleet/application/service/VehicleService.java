package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.VehicleException;
import com.yowyob.fleet.domain.model.Vehicle;
import com.yowyob.fleet.domain.model.VehicleParameters;
import com.yowyob.fleet.domain.model.VehicleLifecycle;
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
    private final com.yowyob.fleet.domain.ports.out.IncidentPersistencePort incidentPersistence;
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
    private final AccessScopeService accessScope;
    private final ResourceAvailabilityService availability;
    private final com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.TripR2dbcRepository tripRepository;
    private final com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.VehicleLocalR2dbcRepository vehicleRepository;
    private final com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.DriverR2dbcRepository driverRepository;
    private final org.springframework.r2dbc.core.DatabaseClient db;

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
            .switchIfEmpty(accessScope.assertFleetAccess(managerId, fleetId).thenReturn(true))
            
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
        Flux<Vehicle> localStream = isAdmin
                ? localPersistencePort.getVehiclesByOrganizationAdminId(requesterId)
                : localPersistencePort.getVehiclesByManager(requesterId);

        if (fleetId != null) {
            localStream = accessScope.assertFleetAccess(requesterId, fleetId)
                    .thenMany(localStream.filter(v -> fleetId.equals(v.fleetId())));
        }

        return localStream.flatMap(v -> getVehicleDetails(v.id(), token)
                .onErrorResume(e -> Mono.just(v)));
    }

    @Override
    @Transactional
    public Mono<Vehicle> createIndependentVehicle(VehicleRequest request, UUID managerId, String token) {
        UUID fleetId = request.fleetId();
        if (fleetId != null) {
            return fleetRepository.findById(fleetId)
                    .switchIfEmpty(Mono.error(new RuntimeException("Flotte introuvable.")))
                    .flatMap(fleet -> createVehicle(fleetId, request, fleet.managerId(), token));
        }
        UUID owner = request.managerId() != null ? request.managerId() : managerId;
        return createVehicle(null, request, owner, token);
    }

    @Override
    @Transactional
    public Mono<Vehicle> createVehicle(UUID fleetId, VehicleRequest req, UUID managerId, String token) {
        UUID resolvedFleetId = fleetId != null ? fleetId : req.fleetId();
        boolean lookupMode = req.brandId() != null && req.modelId() != null
                && req.fuelTypeId() != null && req.transmissionTypeId() != null && req.colorId() != null;

        Mono<Void> ownership = resolvedFleetId != null
                ? accessScope.assertFleetAccess(managerId, resolvedFleetId)
                : Mono.empty();
        String plate = VehicleLifecycle.normalizePlate(req.licensePlate());

        return planLimitGuard.assertCanCreateVehicle(managerId)
                .then(ownership)
                .then(Mono.defer(() -> requireVehicleType(req.vehicleTypeId())))
                .flatMap(typeCode -> assertPlateFree(plate, null).thenReturn(typeCode))
                .flatMap(typeCode -> resolveCreateLabels(req, lookupMode).map(labels -> {
                    validateSpecs(typeCode, labels.fuel(), req.tankCapacity(), req.totalSeatNumber(),
                            req.averageFuelConsumption(), req.manufacturingYear());
                    if (req.initialOdometer() != null && req.initialOdometer() < 0) {
                        throw VehicleException.invalidSpecs("le relevé kilométrique initial ne peut pas être négatif");
                    }
                    return labels;
                }))
                .flatMap(labels -> resolveRemoteContext(resolvedFleetId)
                .flatMap(context -> externalVehiclePort.createRemoteVehicle(
                    req, token,
                    labels.brand(), labels.model(), labels.fuel(), labels.trans(), labels.color(),
                    context
                ))
                .flatMap(remote -> {
                    UUID kernelResourceId = remote.kernelResourceId() != null
                            ? remote.kernelResourceId() : remote.id();
                    // Un véhicule entre dans le parc disponible, ou directement à l'atelier.
                    String status = VehicleLifecycle.MAINTENANCE.equals(VehicleLifecycle.normalizeStatus(req.status()))
                            ? VehicleLifecycle.MAINTENANCE : VehicleLifecycle.AVAILABLE;
                    Vehicle shell = new Vehicle(
                        remote.id(), resolvedFleetId, managerId, null, req.vehicleTypeId(),
                        plate, blankToNull(req.vehicleSerialNumber()), labels.brand(), labels.model(),
                        req.manufacturingYear(), labels.trans(), labels.fuel(),
                        req.tankCapacity(), req.totalSeatNumber(), req.averageFuelConsumption(),
                        labels.color(), status, remote.photoUrl(),
                        null, null, Collections.emptyList(), null, null, null, null,
                        kernelResourceId);
                    return localPersistencePort.saveLocalData(shell)
                            .flatMap(saved -> initOperational(saved, req.initialOdometer()).thenReturn(saved));
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
                ))
                .flatMap(v -> getVehicleDetails(v.id(), token));
    }

    /** Ligne opérationnelle (compteur, jauge) créée avec le véhicule. */
    private Mono<Void> initOperational(Vehicle vehicle, Double initialOdometer) {
        return operationalRepo.findByVehicleId(vehicle.id())
                .hasElement()
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) return Mono.<Void>empty();
                    // L'entité n'est pas Persistable : un save() avec un id ferait un UPDATE vide.
                    var insert = db.sql("""
                                    INSERT INTO fleet.operational_parameters
                                        (id, vehicle_id, status, odometer_reading, mileage, timestamp, tenant_id)
                                    SELECT :id, v.id, TRUE, :km, :km, NOW(), v.tenant_id
                                    FROM fleet.vehicles v WHERE v.id = :vehicleId
                                    """)
                            .bind("id", UUID.randomUUID())
                            .bind("vehicleId", vehicle.id());
                    insert = initialOdometer != null
                            ? insert.bind("km", BigDecimal.valueOf(initialOdometer))
                            : insert.bindNull("km", BigDecimal.class);
                    return insert.then();
                });
    }

    /** Type obligatoire et existant : il fixe la catégorie de permis exigée. Renvoie son code. */
    private Mono<String> requireVehicleType(UUID vehicleTypeId) {
        if (vehicleTypeId == null) {
            return Mono.error(VehicleException.missingVehicleType());
        }
        return vehicleTypeRepo.findById(vehicleTypeId)
                .map(t -> t.getCode() != null ? t.getCode() : "")
                .switchIfEmpty(Mono.error(VehicleException.invalidVehicleType()));
    }

    /** Plaque unique une fois normalisée, parmi les véhicules non supprimés. */
    private Mono<Void> assertPlateFree(String plate, UUID excludeId) {
        if (plate == null || plate.isBlank()) {
            return Mono.error(VehicleException.invalidSpecs("l'immatriculation est obligatoire"));
        }
        String key = VehicleLifecycle.plateKey(plate);
        return vehicleRepository.findAll()
                .filter(v -> v.getDeletedAt() == null && !v.getId().equals(excludeId))
                .any(v -> key.equals(VehicleLifecycle.plateKey(v.getLicensePlate())))
                .flatMap(taken -> Boolean.TRUE.equals(taken)
                        ? Mono.<Void>error(VehicleException.plateConflict())
                        : Mono.<Void>empty());
    }

    /**
     * Caractéristiques cohérentes : réservoir obligatoire pour un véhicule thermique (le suivi
     * carburant en dépend), valeurs strictement positives, consommation et année plausibles.
     */
    static void validateSpecs(String typeCode, String fuelType, Double tank, Integer seats,
                              Double consumption, Integer year) {
        boolean trailer = "TRAILER".equalsIgnoreCase(typeCode);
        if (!trailer && fuelType == null) {
            throw VehicleException.invalidSpecs("le type de carburant est obligatoire (diesel, essence, hybride, électrique ou GPL)");
        }
        boolean needsTank = !trailer && !"ELECTRIC".equals(fuelType);
        if (needsTank && tank == null) {
            throw VehicleException.invalidSpecs("la capacité du réservoir est obligatoire : elle sert au suivi du carburant");
        }
        if (tank != null && (tank <= 0 || tank > 2000)) {
            throw VehicleException.invalidSpecs("la capacité du réservoir doit être comprise entre 1 et 2 000 L");
        }
        if (seats != null && (seats <= 0 || seats > 120)) {
            throw VehicleException.invalidSpecs("le nombre de places doit être compris entre 1 et 120");
        }
        if (consumption != null && (consumption <= 0 || consumption > 100)) {
            throw VehicleException.invalidSpecs("la consommation de référence doit être comprise entre 0 et 100 L/100 km");
        }
        if (!VehicleLifecycle.isPlausibleYear(year)) {
            throw VehicleException.invalidSpecs("l'année de fabrication doit être comprise entre 1950 et l'année prochaine");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record CreateLabels(String brand, String model, String fuel, String trans, String color) {}

    private Mono<CreateLabels> resolveCreateLabels(VehicleRequest req, boolean lookupMode) {
        if (!lookupMode) {
            String brand = requireLabel(req.brand(), "brand");
            String model = requireLabel(req.model(), "model");
            return Mono.just(new CreateLabels(
                    brand,
                    model,
                    normalizeFuelOrFail(req.fuelType()),
                    normalizeTransmissionOrFail(req.transmissionType()),
                    blankToNull(req.color())
            ));
        }
        return Mono.zip(
                brandRepo.findById(req.brandId()).map(b -> b.getLabel()).switchIfEmpty(Mono.just("Unknown")),
                modelRepo.findById(req.modelId()).map(m -> m.getLabel()).switchIfEmpty(Mono.just("Unknown")),
                fuelRepo.findById(req.fuelTypeId()).map(f -> f.getLabel()).switchIfEmpty(Mono.just("")),
                transRepo.findById(req.transmissionTypeId()).map(t -> t.getLabel()).switchIfEmpty(Mono.just("")),
                colorRepo.findById(req.colorId()).map(c -> c.getLabel()).switchIfEmpty(Mono.just(""))
        ).map(t -> new CreateLabels(t.getT1(), t.getT2(), VehicleLifecycle.normalizeFuelType(t.getT3()),
                VehicleLifecycle.normalizeTransmission(t.getT4()), blankToNull(t.getT5())));
    }

    private static String normalizeFuelOrFail(Object raw) {
        if (raw == null || String.valueOf(raw).isBlank()) return null;
        String fuel = VehicleLifecycle.normalizeFuelType(String.valueOf(raw));
        if (fuel == null) {
            throw VehicleException.invalidSpecs("type de carburant inconnu : " + raw);
        }
        return fuel;
    }

    private static String normalizeTransmissionOrFail(Object raw) {
        if (raw == null || String.valueOf(raw).isBlank()) return null;
        String trans = VehicleLifecycle.normalizeTransmission(String.valueOf(raw));
        if (trans == null) {
            throw VehicleException.invalidSpecs("boîte de vitesses inconnue : " + raw + " (manuelle ou automatique)");
        }
        return trans;
    }

    private static String requireLabel(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " est obligatoire");
        }
        return value.trim();
    }

    /** Statut demandé par un gestionnaire : disponible, maintenance ou immobilisé. */
    private static String normalizeRequestedStatus(Object raw) {
        String value = VehicleLifecycle.normalizeStatus(raw);
        if (value == null || !VehicleLifecycle.MANUAL_STATUSES.contains(value)) {
            if (VehicleLifecycle.ON_TRIP.equals(value)) {
                throw VehicleException.statusManagedByTrips();
            }
            throw VehicleException.invalidStatus(String.valueOf(raw));
        }
        return value;
    }

    /**
     * Règles d'un changement de statut manuel :
     * - ON_TRIP est piloté par les trajets : on ne le quitte pas pendant un trajet en cours ;
     * - un véhicule immobilisé par un incident grave ouvert ne redevient disponible qu'à la
     *   résolution de l'incident ;
     * - une immobilisation exige un motif.
     */
    private Mono<Void> guardStatusChange(Vehicle local, String requested, String reason) {
        if (requested.equals(local.status())) {
            return Mono.empty();
        }
        if (VehicleLifecycle.IMMOBILIZED.equals(requested) && (reason == null || reason.isBlank())) {
            return Mono.error(VehicleException.immobilizationReasonRequired());
        }
        Mono<Void> tripGuard = tripRepository.existsInProgressTripForVehicle(local.id(), null)
                .flatMap(onTrip -> Boolean.TRUE.equals(onTrip)
                        ? Mono.<Void>error(VehicleException.statusManagedByTrips())
                        : Mono.<Void>empty());
        if (VehicleLifecycle.AVAILABLE.equals(requested) && !VehicleLifecycle.AVAILABLE.equals(local.status())) {
            // Vérifié en premier : c'est le motif le plus utile à afficher.
            return incidentPersistence.findByVehicleId(local.id())
                    .any(i -> i.isVehicleImmobilized() && i.immobilizesVehicle())
                    .flatMap(held -> Boolean.TRUE.equals(held)
                            ? Mono.<Void>error(VehicleException.heldByIncident())
                            : Mono.<Void>empty())
                    .then(tripGuard);
        }
        return tripGuard;
    }

    /** Un changement de flotte est refusé tant que des trajets ou affectations sont en cours. */
    private Mono<Void> guardFleetChange(Vehicle local, UUID targetFleet) {
        if (targetFleet == null || targetFleet.equals(local.fleetId())) {
            return Mono.empty();
        }
        return availability.vehicleHasOpenCommitments(local.id())
                .flatMap(open -> Boolean.TRUE.equals(open)
                        ? Mono.<Void>error(VehicleException.openCommitments())
                        : Mono.<Void>empty());
    }

    private static boolean has(Map<String, Object> updates, String key) {
        return updates.containsKey(key) && updates.get(key) != null;
    }

    private static String str(Map<String, Object> updates, String key, String current) {
        if (!updates.containsKey(key)) return current;
        Object v = updates.get(key);
        return v == null || String.valueOf(v).isBlank() ? null : String.valueOf(v).trim();
    }

    private static Double dbl(Map<String, Object> updates, String key, Double current) {
        if (!updates.containsKey(key)) return current;
        Object v = updates.get(key);
        if (v == null || String.valueOf(v).isBlank()) return null;
        try { return v instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(v).replace(',', '.')); }
        catch (NumberFormatException e) { throw VehicleException.invalidSpecs("valeur numérique invalide pour " + key); }
    }

    @Override
    @Transactional
    public Mono<Vehicle> patchVehicleInfo(UUID id, Map<String, Object> updates, String token) {
        return localPersistencePort.getLocalDataById(id)
                .switchIfEmpty(Mono.error(VehicleException.notFound(id)))
                .flatMap(local -> {
                    if (VehicleLifecycle.RETIRED.equals(local.status())) {
                        return Mono.<Vehicle>error(VehicleException.retired());
                    }
                    String requestedStatus = has(updates, "status") ? normalizeRequestedStatus(updates.get("status")) : null;
                    String statusReason = str(updates, "statusReason", null);
                    UUID targetFleet = toUuid(updates.get("fleetId"));
                    UUID typeId = has(updates, "vehicleTypeId") ? toUuid(updates.get("vehicleTypeId")) : local.vehicleTypeId();
                    String plate = has(updates, "licensePlate")
                            ? VehicleLifecycle.normalizePlate(String.valueOf(updates.get("licensePlate"))) : local.licensePlate();
                    String fuel = updates.containsKey("fuelType") ? normalizeFuelOrFail(updates.get("fuelType")) : local.fuelType();
                    String trans = updates.containsKey("transmissionType")
                            ? normalizeTransmissionOrFail(updates.get("transmissionType")) : local.transmissionType();
                    Integer year = updates.containsKey("manufacturingYear") ? toInt(updates.get("manufacturingYear")) : local.manufacturingYear();
                    Double tank = dbl(updates, "tankCapacity", local.tankCapacity());
                    Double consumption = dbl(updates, "averageFuelConsumption", local.averageFuelConsumption());
                    Integer seats = updates.containsKey("totalSeatNumber") ? toInt(updates.get("totalSeatNumber")) : local.totalSeatNumber();

                    // Les caractéristiques ne sont contrôlées que si l'on y touche : une fiche ancienne
                    // incomplète (sans réservoir) reste modifiable pour son statut ou sa couleur.
                    boolean specsTouched = java.util.stream.Stream.of("vehicleTypeId", "fuelType", "tankCapacity",
                                    "totalSeatNumber", "averageFuelConsumption", "manufacturingYear")
                            .anyMatch(updates::containsKey);
                    Mono<Void> checks = Mono.defer(() -> (specsTouched ? requireVehicleType(typeId) : Mono.just(""))
                            .doOnNext(code -> {
                                if (specsTouched) validateSpecs(code, fuel, tank, seats, consumption, year);
                            })
                            .then(plate.equals(local.licensePlate()) ? Mono.<Void>empty() : assertPlateFree(plate, id))
                            .then(requestedStatus != null ? guardStatusChange(local, requestedStatus, statusReason) : Mono.<Void>empty())
                            .then(guardFleetChange(local, targetFleet)));

                    boolean fleetChanged = targetFleet != null && !targetFleet.equals(local.fleetId());
                    Vehicle updated = new Vehicle(
                            local.id(), targetFleet != null ? targetFleet : local.fleetId(), local.managerId(),
                            fleetChanged ? null : local.currentDriverId(), typeId, plate,
                            str(updates, "vehicleSerialNumber", local.vehicleSerialNumber()),
                            has(updates, "brand") ? String.valueOf(updates.get("brand")).trim() : local.brand(),
                            has(updates, "model") ? String.valueOf(updates.get("model")).trim() : local.model(),
                            year, trans, fuel, tank, seats, consumption,
                            str(updates, "color", local.color()),
                            local.status(),
                            updates.containsKey("photoUrl") ? str(updates, "photoUrl", null) : local.photoUrl(),
                            local.serialNumberPhotoUrl(), local.registrationPhotoUrl(),
                            local.illustrationImages(), local.financialParameters(),
                            local.maintenanceParameters(), local.operationalParameters(),
                            local.geofenceRemoteId(), local.kernelResourceId());

                    // Une fiche liée à un autre chauffeur de l'ancienne flotte ne suit pas le véhicule.
                    Mono<Void> fleetSideEffects = fleetChanged && local.currentDriverId() != null
                            ? unlinkRegularDriver(local.id(), local.currentDriverId())
                            : Mono.empty();

                    Mono<Vehicle> statusChange = requestedStatus != null
                            && (!requestedStatus.equals(local.status()) || statusReason != null)
                            ? localPersistencePort.updateLifecycle(id, requestedStatus,
                                    VehicleLifecycle.AVAILABLE.equals(requestedStatus) ? null : statusReason,
                                    null, null, null, null)
                            : Mono.empty();

                    // La base locale fait foi ; la ressource Kernel (nom, série) est mise à jour au mieux.
                    Mono<Void> remote = externalVehiclePort.patchRemoteVehicle(id, updates, token)
                            .then()
                            .onErrorResume(e -> {
                                log.warn("Mise à jour distante du véhicule {} ignorée : {}", id, e.getMessage());
                                return Mono.empty();
                            });

                    return checks
                            .then(fleetSideEffects)
                            .then(localPersistencePort.saveLocalData(updated))
                            .then(statusChange)
                            .then(remote)
                            .then(localPersistencePort.getLocalDataById(id));
                });
    }

    /** Défait le lien véhicule attitré des deux côtés. */
    private Mono<Void> unlinkRegularDriver(UUID vehicleId, UUID driverId) {
        return driverRepository.findById(driverId)
                .filter(d -> vehicleId.equals(d.getAssignedVehicleId()))
                .flatMap(d -> {
                    d.setAssignedVehicleId(null);
                    d.markAsExisting();
                    return driverRepository.save(d);
                })
                .then(vehicleRepository.findById(vehicleId)
                        .flatMap(v -> {
                            v.setCurrentDriverId(null);
                            v.setNew(false);
                            return vehicleRepository.save(v);
                        }))
                .then();
    }

    @Override
    @Transactional
    public Mono<Vehicle> retireVehicle(UUID id, com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.VehicleRetireRequest req) {
        java.time.LocalDate date = req.retiredAt() != null ? req.retiredAt() : java.time.LocalDate.now();
        String type = req.retirementType() == null ? "" : req.retirementType().trim().toUpperCase(java.util.Locale.ROOT);
        if (!VehicleLifecycle.RETIREMENT_TYPES.contains(type)) {
            return Mono.error(VehicleException.invalidRetirement(
                    "type de sortie inconnu (vente, réforme, vol, perte totale, restitution ou autre)"));
        }
        if (date.isAfter(java.time.LocalDate.now())) {
            return Mono.error(VehicleException.invalidRetirement("la date de sortie ne peut pas être dans le futur"));
        }
        String reason = blankToNull(req.reason());
        if ("OTHER".equals(type) && reason == null) {
            return Mono.error(VehicleException.invalidRetirement("précisez le motif de la sortie"));
        }
        if (req.salePrice() != null && (!"SOLD".equals(type) || req.salePrice().signum() < 0)) {
            return Mono.error(VehicleException.invalidRetirement("un prix de cession positif n'est possible que pour une vente"));
        }
        return localPersistencePort.getLocalDataById(id)
                .switchIfEmpty(Mono.error(VehicleException.notFound(id)))
                .flatMap(local -> {
                    if (VehicleLifecycle.RETIRED.equals(local.status())) {
                        return Mono.<Vehicle>error(VehicleException.retired());
                    }
                    Mono<Void> guard = tripRepository.existsInProgressTripForVehicle(id, null)
                            .flatMap(onTrip -> Boolean.TRUE.equals(onTrip)
                                    ? Mono.<Boolean>error(VehicleException.statusManagedByTrips())
                                    : availability.vehicleHasOpenCommitments(id))
                            .flatMap(open -> Boolean.TRUE.equals(open)
                                    ? Mono.<Void>error(VehicleException.openCommitments())
                                    : Mono.<Void>empty());
                    Mono<Void> unlink = local.currentDriverId() != null
                            ? unlinkRegularDriver(id, local.currentDriverId())
                            : driverRepository.findByAssignedVehicleId(id)
                                    .flatMap(d -> unlinkRegularDriver(id, d.getUserId())).then();
                    return guard.then(unlink)
                            .then(localPersistencePort.updateLifecycle(id, VehicleLifecycle.RETIRED, null,
                                    date, type, reason, "SOLD".equals(type) ? req.salePrice() : null));
                })
                .doOnSuccess(v -> log.info("🚗 Véhicule {} retiré du parc ({})", id, type));
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
        // VehicleException.ongoingTripConflict (VHC_005) existait mais n'était plus appelée :
        // un véhicule en trajet, ou promis à des trajets / affectations, était supprimé.
        Mono<Void> guard = tripRepository.existsInProgressTripForVehicle(id, null)
                .flatMap(onTrip -> Boolean.TRUE.equals(onTrip)
                        ? Mono.<Boolean>error(VehicleException.ongoingTripConflict())
                        : availability.vehicleHasOpenCommitments(id))
                .flatMap(open -> Boolean.TRUE.equals(open)
                        ? Mono.<Boolean>error(VehicleException.openCommitments())
                        : vehicleHasHistory(id))
                // Un véhicule qui a roulé sort du parc par un retrait (historique conservé) :
                // la suppression est réservée aux fiches créées par erreur.
                .flatMap(history -> Boolean.TRUE.equals(history)
                        ? Mono.<Void>error(VehicleException.hasHistory())
                        : Mono.<Void>empty());
        Mono<Void> remote = externalVehiclePort.deleteRemoteVehicle(id, t)
                .onErrorResume(e -> {
                    log.warn("Suppression distante véhicule {} ignorée : {}", id, e.getMessage());
                    return Mono.empty();
                });
        return guard.then(remote).then(localPersistencePort.deleteLocalData(id));
    }

    private Mono<Boolean> vehicleHasHistory(UUID id) {
        return db.sql("""
                        SELECT EXISTS (SELECT 1 FROM fleet.trips WHERE vehicle_id = :id)
                            OR EXISTS (SELECT 1 FROM fleet.assignments WHERE vehicle_id = :id)
                            OR EXISTS (SELECT 1 FROM fleet.fuel_recharges WHERE vehicle_id = :id)
                            OR EXISTS (SELECT 1 FROM fleet.expenses WHERE vehicle_id = :id)
                            OR EXISTS (SELECT 1 FROM fleet.maintenances WHERE vehicle_id = :id)
                            OR EXISTS (SELECT 1 FROM fleet.incidents WHERE vehicle_id = :id) AS used
                        """)
                .bind("id", id)
                .map((row, meta) -> Boolean.TRUE.equals(row.get("used", Boolean.class)))
                .one()
                .defaultIfEmpty(false);
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
                .switchIfEmpty(Mono.error(VehicleException.notFound(vehicleId)))
                .flatMap(e -> {
                    if (updates.get("fuelLevel") != null) {
                        // Jauge en pourcentage du réservoir (« 62 » ou « 62% »).
                        BigDecimal pct;
                        try {
                            pct = new BigDecimal(updates.get("fuelLevel").toString().replace("%", "").replace(',', '.').trim());
                        } catch (NumberFormatException ex) {
                            return Mono.error(VehicleException.invalidSpecs("niveau de carburant invalide"));
                        }
                        if (pct.signum() < 0 || pct.compareTo(BigDecimal.valueOf(100)) > 0) {
                            return Mono.error(VehicleException.invalidSpecs("le niveau de carburant est un pourcentage entre 0 et 100"));
                        }
                        e.setFuelLevel(pct.setScale(0, java.math.RoundingMode.HALF_UP) + "%");
                    }
                    if (updates.get("odometerReading") != null) {
                        BigDecimal km;
                        try {
                            km = new BigDecimal(updates.get("odometerReading").toString().replace(',', '.').trim());
                        } catch (NumberFormatException ex) {
                            return Mono.error(VehicleException.invalidSpecs("relevé kilométrique invalide"));
                        }
                        // Un compteur ne recule pas : un relevé plus bas est une erreur de saisie.
                        if (e.getOdometerReading() != null && km.compareTo(e.getOdometerReading()) < 0) {
                            return Mono.error(VehicleException.odometerDecrease(e.getOdometerReading()));
                        }
                        e.setOdometerReading(km);
                        e.setMileage(km);
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