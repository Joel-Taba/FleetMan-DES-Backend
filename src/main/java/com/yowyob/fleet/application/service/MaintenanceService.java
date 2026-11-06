package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.OperationException;
import com.yowyob.fleet.domain.model.AlertEvent;
import com.yowyob.fleet.domain.model.AlertRule;
import com.yowyob.fleet.domain.model.Coordinates;
import com.yowyob.fleet.domain.model.Maintenance;
import com.yowyob.fleet.domain.ports.in.ManageMaintenanceUseCase;
import com.yowyob.fleet.domain.ports.out.DriverPersistencePort;
import com.yowyob.fleet.domain.ports.out.MaintenancePersistencePort;
import com.yowyob.fleet.domain.ports.out.OperationEventPort;
import com.yowyob.fleet.domain.ports.out.SendAlertPort;
import com.yowyob.fleet.domain.ports.out.VehiclePersistencePort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.MaintenanceParameterR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.UserLocalR2dbcRepository;
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
    private final SendAlertPort sendAlertPort;
    private final MaintenanceParameterR2dbcRepository maintenanceParamRepo;
    private final UserLocalR2dbcRepository userRepo;
    private final com.yowyob.fleet.domain.ports.in.ManageExpenseUseCase expenseUseCase;
    private final com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.OperationalParameterR2dbcRepository operationalRepo;
    private final OperationLookup lookup;
    private final OperationNotifier notifications;

    // ── 1. CRÉATION ──────────────────────────────────────────────────────────

    /**
     * Demande d'un chauffeur (REQUESTED, notifiée au gestionnaire), ordre planifié / en cours, ou
     * intervention terminée saisie après coup par le gestionnaire (notifiée aux chauffeurs du véhicule).
     */
    @Override
    @Transactional
    public Mono<Maintenance> createMaintenance(CreateMaintenanceCommand cmd) {

        // 1. Vérification existence du véhicule
        Mono<String> vehicleRegistrationMono = vehiclePersistence.getLocalDataById(cmd.vehicleId())
                .switchIfEmpty(Mono.error(OperationException.vehicleNotFound(cmd.vehicleId())))
                .map(v -> v.licensePlate());

        // 2. Chauffeur cité : nom lisible (l'identifiant s'affichait à la place du nom)
        Mono<String> driverNameMono = cmd.driverId() != null
                ? driverPersistence.findById(cmd.driverId())
                        .switchIfEmpty(Mono.error(OperationException.driverNotFound(cmd.driverId())))
                        .flatMap(d -> lookup.fullName(d.userId()))
                : Mono.just("");

        Mono<java.util.Optional<UUID>> requesterMono = lookup.localUser(cmd.actorId())
                .filterWhen(lookup::isDriver)
                .map(java.util.Optional::of)
                .defaultIfEmpty(java.util.Optional.empty());

        return Mono.zip(vehicleRegistrationMono, driverNameMono, requesterMono)
                .flatMap(tuple -> {
                    String vehicleReg  = tuple.getT1();
                    String driverName  = tuple.getT2().isBlank() ? null : tuple.getT2();
                    UUID requester     = tuple.getT3().orElse(null);

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
                    // Un chauffeur ne fait qu'une demande : c'est le gestionnaire qui planifie.
                    Maintenance.Status initial = requester != null ? Maintenance.Status.REQUESTED : cmd.status();
                    String type = cmd.maintenanceType() != null ? cmd.maintenanceType()
                            : Maintenance.TYPES.contains(cmd.subject()) ? cmd.subject() : null;
                    maintenance.initWorkflow(initial, type, cmd.urgent(), cmd.plannedAt(), requester, LocalDateTime.now());

                    Mono<Void> workshop = maintenance.getStatus() == Maintenance.Status.IN_PROGRESS
                            ? enterWorkshop(maintenance)
                            : Mono.empty();
                    return workshop.then(maintenancePersistence.save(maintenance));
                })
                .flatMap(saved -> {
                    // 3. Paramètres de maintenance du véhicule : seulement quand l'intervention a eu lieu
                    Mono<Void> syncParams = (saved.getStatus() == Maintenance.Status.COMPLETED
                            ? syncMaintenanceParameters(saved.getVehicleId())
                            : Mono.<Void>empty())
                            .then(syncExpense(saved));

                    // 4. Événement Kafka (push, best-effort) ; alerte + email au gestionnaire pour une demande
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
                                Mono<Void> kafkaPublish = eventPort.publishMaintenanceCreated(event)
                                        .doOnError(e -> log.warn("⚠️ Échec publication événement maintenance {}: {}",
                                                saved.getId(), e.getMessage()))
                                        .onErrorResume(e -> Mono.empty());

                                Mono<?> alert = saved.getStatus() == Maintenance.Status.REQUESTED && saved.getRequestedBy() != null
                                        ? notifyManagerOfMaintenance(saved, v.managerId())
                                        : Mono.empty();

                                return Mono.when(kafkaPublish, alert);
                            })
                            .doOnError(e -> log.warn("⚠️ Échec alerte maintenance {}: {}",
                                    saved.getId(), e.getMessage()))
                            .onErrorResume(e -> Mono.empty())
                            .subscribe();

                    // 5. Notification in-app (centre de notifications), dans la chaîne : jamais bloquante
                    return syncParams
                            .then(notifications.maintenanceCreated(saved, cmd.actorId()))
                            .thenReturn(saved);
                });
    }

    // ── 1 bis. CYCLE DE VIE ─────────────────────────────────────────────────

    @Override
    @Transactional
    public Mono<Maintenance> transition(TransitionCommand cmd) {
        return maintenancePersistence.findById(cmd.maintenanceId())
                .switchIfEmpty(Mono.error(OperationException.maintenanceNotFound(cmd.maintenanceId())))
                .flatMap(m -> {
                    Maintenance.Status before = m.getStatus();
                    LocalDateTime now = LocalDateTime.now();
                    String action = cmd.action() == null ? "" : cmd.action().trim().toUpperCase();
                    Mono<Void> vehicleEffect;
                    switch (action) {
                        case "PLAN" -> {
                            m.plan(cmd.plannedAt(), cmd.garage());
                            vehicleEffect = Mono.empty();
                        }
                        case "START" -> {
                            m.start(now);
                            vehicleEffect = enterWorkshop(m);
                        }
                        case "COMPLETE" -> {
                            m.complete(now, cmd.cost(), cmd.report());
                            vehicleEffect = before == Maintenance.Status.IN_PROGRESS
                                    ? lookup.releaseVehicleFromWorkshop(m.getVehicleId(), m.getId()).then()
                                    : Mono.empty();
                        }
                        case "CANCEL" -> {
                            m.cancel(now, cmd.reason());
                            vehicleEffect = before == Maintenance.Status.IN_PROGRESS
                                    ? lookup.releaseVehicleFromWorkshop(m.getVehicleId(), m.getId()).then()
                                    : Mono.empty();
                        }
                        default -> {
                            return Mono.error(com.yowyob.fleet.domain.exception.MaintenanceWorkflowException
                                    .invalidTransition(before.name(), action));
                        }
                    }
                    Mono<Void> afterSave = switch (m.getStatus()) {
                        case COMPLETED -> syncMaintenanceParameters(m.getVehicleId()).then(syncExpense(m));
                        case CANCELLED -> expenseUseCase.deleteAutoExpenses(m.getId());
                        default -> Mono.empty();
                    };
                    return vehicleEffect
                            .then(maintenancePersistence.save(m))
                            .flatMap(saved -> afterSave
                                    .then(notifications.maintenanceTransition(saved, cmd.actorId()))
                                    .thenReturn(saved));
                });
    }

    /** Le véhicule disponible passe en MAINTENANCE ; un véhicule en trajet doit d'abord rentrer. */
    private Mono<Void> enterWorkshop(Maintenance m) {
        return lookup.vehicle(m.getVehicleId())
                .flatMap(v -> com.yowyob.fleet.domain.model.VehicleLifecycle.ON_TRIP.equals(v.status())
                        ? Mono.<Void>error(com.yowyob.fleet.domain.exception.MaintenanceWorkflowException.vehicleOnTrip())
                        : lookup.vehicleToWorkshop(m.getVehicleId(), "Maintenance : " + m.getSubject()));
    }

    @Override
    public Flux<Maintenance> getForDriver(UUID driverId) {
        return lookup.localUser(driverId).flatMapMany(localId -> lookup.assignedVehicle(localId)
                .map(java.util.Optional::of)
                .defaultIfEmpty(java.util.Optional.empty())
                .flatMapMany(vehicle -> maintenancePersistence.findForDriver(localId, vehicle.orElse(null))));
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
        // Limité aux véhicules du gestionnaire : l'ancien filter(m -> true) renvoyait les
        // maintenances de TOUTES les entreprises.
        return maintenancePersistence.findAllByManagerId(managerId)
                .filter(m -> m.getDateTime() != null
                        && (start == null || !m.getDateTime().isBefore(start))
                        && (end == null || !m.getDateTime().isAfter(end)));
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

                    return maintenancePersistence.save(maintenance)
                            .flatMap(saved -> (saved.getStatus() == Maintenance.Status.CANCELLED
                                    ? Mono.<Void>empty()
                                    : syncExpense(saved)).thenReturn(saved));
                });
    }

    // ── 4. SUPPRESSION ───────────────────────────────────────────────────────

    @Override
    public Mono<Void> delete(UUID id) {
        return maintenancePersistence.existsById(id)
                .flatMap(exists -> {
                    if (!exists) return Mono.error(OperationException.maintenanceNotFound(id));
                    return expenseUseCase.deleteAutoExpenses(id)
                            .then(maintenancePersistence.deleteById(id));
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
        // Le kilométrage relevé à la maintenance sert de base aux seuils kilométriques
        // de la maintenance préventive.
        Mono<java.math.BigDecimal> odometer = operationalRepo.findByVehicleId(vehicleId)
                .mapNotNull(op -> op.getOdometerReading());
        return maintenanceParamRepo.findByVehicleId(vehicleId)
                .zipWith(odometer.map(java.util.Optional::of).defaultIfEmpty(java.util.Optional.empty()))
                .flatMap(tuple -> {
                    var params = tuple.getT1();
                    params.setLastMaintenanceAt(LocalDate.now());
                    tuple.getT2().ifPresent(params::setLastMaintenanceKm);
                    params.setMaintenanceStatus("UP_TO_DATE");
                    return maintenanceParamRepo.save(params);
                })
                .doOnSuccess(p -> log.info("✅ maintenance_parameters mis à jour pour le véhicule {}", vehicleId))
                .doOnError(e -> log.warn("⚠️ Impossible de mettre à jour maintenance_parameters pour {}: {}",
                        vehicleId, e.getMessage()))
                .then();
    }

    /** Dépense MAINTENANCE auto-générée, alignée sur le coût de l'intervention. */
    private Mono<Void> syncExpense(Maintenance maintenance) {
        return expenseUseCase.syncAutoExpense(
                com.yowyob.fleet.domain.model.Expense.ExpenseType.MAINTENANCE,
                maintenance.getId(),
                maintenance.getCost(),
                "Maintenance : " + maintenance.getSubject(),
                maintenance.getVehicleId(),
                maintenance.getDriverId());
    }

    private Mono<AlertEvent> notifyManagerOfMaintenance(Maintenance maintenance, UUID managerId) {
        String title = "Nouvelle maintenance déclarée";
        String message = String.format("Une maintenance a été enregistrée pour le véhicule %s : \"%s\"",
                maintenance.getVehicleRegistrationNumber(), maintenance.getSubject());

        AlertEvent event = new AlertEvent(
                null, null, null,
                managerId, AlertRule.TriggerType.MAINTENANCE_DECLARED, AlertRule.ActionType.IN_APP_NOTIFICATION,
                title, message,
                maintenance.getId(), "MAINTENANCE",
                AlertEvent.ReadStatus.UNREAD,
                LocalDateTime.now(), null
        );
        return sendAlertPort.sendInApp(event)
                .doOnSuccess(saved -> notifyManagerByEmail(saved, managerId));
    }

    /**
     * Email au gestionnaire, en plus du in-app — toujours actif comme le in-app
     * (pas conditionné par une AlertRule configurable). Fire & forget : un échec
     * d'envoi email ne doit jamais faire échouer la création de la maintenance.
     */
    private void notifyManagerByEmail(AlertEvent event, UUID managerId) {
        userRepo.findById(managerId)
                .mapNotNull(u -> u.getEmail())
                .flatMap(email -> sendAlertPort.sendEmail(event, email))
                .doOnError(e -> log.warn("⚠️ Échec envoi email maintenance {} : {}", event.getId(), e.getMessage()))
                .onErrorResume(e -> Mono.empty())
                .subscribe();
    }
}
