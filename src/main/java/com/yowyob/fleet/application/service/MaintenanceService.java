package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.OperationException;
import com.yowyob.fleet.domain.model.Coordinates;
import com.yowyob.fleet.domain.model.Maintenance;
import com.yowyob.fleet.domain.ports.in.ManageMaintenanceUseCase;
import com.yowyob.fleet.domain.ports.out.DriverPersistencePort;
import com.yowyob.fleet.domain.ports.out.MaintenancePersistencePort;
import com.yowyob.fleet.domain.ports.out.OperationEventPort;
import com.yowyob.fleet.domain.ports.out.VehiclePersistencePort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.MaintenanceParameterR2dbcRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MaintenanceService implements ManageMaintenanceUseCase {

    private final MaintenancePersistencePort maintenancePersistence;
    private final VehiclePersistencePort vehiclePersistence;
    private final DriverPersistencePort driverPersistence;
    private final OperationEventPort eventPort;
    private final MaintenanceParameterR2dbcRepository maintenanceParamRepo;

    // ── 1. CRÉATION ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    public Mono<Maintenance> createMaintenance(CreateMaintenanceCommand cmd) {

        // 1. Vérification existence du véhicule
        Mono<String> vehicleRegistrationMono = vehiclePersistence.getLocalDataById(cmd.vehicleId())
                .switchIfEmpty(Mono.error(OperationException.vehicleNotFound(cmd.vehicleId())))
                .map(v -> v.licensePlate());

        // 2. Résolution optionnelle du nom du chauffeur
        Mono<String> driverNameMono = cmd.driverId() != null
                ? driverPersistence.findById(cmd.driverId())
                        .switchIfEmpty(Mono.error(OperationException.driverNotFound(cmd.driverId())))
                        .map(d -> d.userId().toString()) // sera enrichi si le modèle Driver expose le nom
                : Mono.just("");

        return Mono.zip(vehicleRegistrationMono, driverNameMono)
                .flatMap(tuple -> {
                    String vehicleReg  = tuple.getT1();
                    String driverName  = tuple.getT2().isBlank() ? null : tuple.getT2();

                    Coordinates coords = buildCoordinates(cmd.longitude(), cmd.latitude());

                    Maintenance maintenance = new Maintenance(
                            null,
                            cmd.subject(),
                            cmd.cost(),
                            LocalDateTime.now(),
                            cmd.report(),
                            coords,
                            cmd.locationName(),
                            cmd.vehicleId(),
                            vehicleReg,
                            cmd.driverId(),
                            driverName
                    );

                    return maintenancePersistence.save(maintenance);
                })
                .flatMap(saved -> {
                    // 3. Synchronisation maintenance_parameters du véhicule
                    Mono<Void> syncParams = syncMaintenanceParameters(saved.getVehicleId());

                    // 4. Publication événement → notification Fleet Manager (fire & forget)
                    vehiclePersistence.getLocalDataById(saved.getVehicleId())
                            .flatMap(v -> {
                                OperationEventPort.MaintenanceCreatedEvent event =
                                        new OperationEventPort.MaintenanceCreatedEvent(
                                                saved.getId(),
                                                saved.getSubject(),
                                                saved.getVehicleId(),
                                                saved.getVehicleRegistrationNumber(),
                                                saved.getDriverId(),
                                                v.managerId()
                                        );
                                return eventPort.publishMaintenanceCreated(event);
                            })
                            .doOnError(e -> log.warn("⚠️ Échec publication événement maintenance {}: {}",
                                    saved.getId(), e.getMessage()))
                            .subscribe();

                    return syncParams.thenReturn(saved);
                });
    }

    // ── 2. LECTURE ───────────────────────────────────────────────────────────

    @Override
    public Mono<Maintenance> getById(UUID id) {
        return maintenancePersistence.findById(id)
                .switchIfEmpty(Mono.error(OperationException.maintenanceNotFound(id)));
    }

    @Override
    public Flux<Maintenance> getAllByManager(UUID managerId) {
        return maintenancePersistence.findAllByManagerId(managerId);
    }

    @Override
    public Flux<Maintenance> getByVehicleId(UUID vehicleId) {
        return vehiclePersistence.getLocalDataById(vehicleId)
                .switchIfEmpty(Mono.error(OperationException.vehicleNotFound(vehicleId)))
                .thenMany(maintenancePersistence.findByVehicleId(vehicleId));
    }

    @Override
    public Flux<Maintenance> getByDriverId(UUID driverId) {
        return maintenancePersistence.findByDriverId(driverId);
    }

    @Override
    public Flux<Maintenance> getByDateRange(LocalDateTime start, LocalDateTime end, UUID managerId) {
        return maintenancePersistence.findByDateRange(start, end)
                .filter(m -> true); // le filtrage par manager est délégué à la couche persistance si nécessaire
    }

    @Override
    public Mono<Long> countByDriverId(UUID driverId) {
        return maintenancePersistence.countByDriverId(driverId);
    }

    // ── 3. MISE À JOUR ───────────────────────────────────────────────────────

    @Override
    @Transactional
    public Mono<Maintenance> update(UpdateMaintenanceCommand cmd) {
        return maintenancePersistence.findById(cmd.maintenanceId())
                .switchIfEmpty(Mono.error(OperationException.maintenanceNotFound(cmd.maintenanceId())))
                .flatMap(maintenance -> {
                    if (cmd.report() != null)    maintenance.addReport(cmd.report());
                    if (cmd.cost() != null)      maintenance.updateCost(cmd.cost());

                    Coordinates coords = buildCoordinates(cmd.longitude(), cmd.latitude());
                    if (coords != null || cmd.locationName() != null) {
                        maintenance.updateLocation(coords, cmd.locationName());
                    }

                    return maintenancePersistence.save(maintenance);
                });
    }

    // ── 4. SUPPRESSION ───────────────────────────────────────────────────────

    @Override
    public Mono<Void> delete(UUID id) {
        return maintenancePersistence.existsById(id)
                .flatMap(exists -> {
                    if (!exists) return Mono.error(OperationException.maintenanceNotFound(id));
                    return maintenancePersistence.deleteById(id);
                });
    }

    // ── HELPERS PRIVÉS ───────────────────────────────────────────────────────

    /**
     * Construit un objet Coordinates uniquement si les deux coordonnées sont fournies.
     */
    private Coordinates buildCoordinates(Double longitude, Double latitude) {
        if (longitude != null && latitude != null) {
            return new Coordinates(longitude, latitude);
        }
        return null;
    }

    /**
     * Met à jour last_maintenance_at dans maintenance_parameters du véhicule.
     * Appelé après chaque création de maintenance.
     */
    private Mono<Void> syncMaintenanceParameters(UUID vehicleId) {
        return maintenanceParamRepo.findByVehicleId(vehicleId)
                .flatMap(params -> {
                    params.setLastMaintenanceAt(LocalDate.now());
                    params.setMaintenanceStatus("UP_TO_DATE");
                    return maintenanceParamRepo.save(params);
                })
                .doOnSuccess(p -> log.info("✅ maintenance_parameters mis à jour pour le véhicule {}", vehicleId))
                .doOnError(e -> log.warn("⚠️ Impossible de mettre à jour maintenance_parameters pour {}: {}",
                        vehicleId, e.getMessage()))
                .then();
    }
}
