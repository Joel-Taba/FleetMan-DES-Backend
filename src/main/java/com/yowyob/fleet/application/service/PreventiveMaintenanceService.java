package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.PreventiveMaintenanceException;
import com.yowyob.fleet.domain.model.MaintenanceAlert;
import com.yowyob.fleet.domain.model.MaintenancePlan;
import com.yowyob.fleet.domain.ports.in.ManageMaintenancePlanUseCase;
import com.yowyob.fleet.domain.ports.out.*;
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
public class PreventiveMaintenanceService implements ManageMaintenancePlanUseCase {

    private final MaintenancePlanPersistencePort planPort;
    private final MaintenanceAlertPersistencePort alertPort;
    private final VehiclePersistencePort vehiclePort;
    private final FleetRepositoryPort fleetPort;

    // ── Plans — CRUD ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    public Mono<MaintenancePlan> createPlan(CreatePlanCommand cmd) {

        Mono<Void> validation = validateFleetAndVehicle(cmd.fleetId(), cmd.vehicleId(), cmd.scope());

        return validation.then(Mono.defer(() -> {
            MaintenancePlan plan = new MaintenancePlan(
                    null,
                    cmd.maintenanceType(),
                    cmd.scope(),
                    cmd.fleetId(),
                    cmd.vehicleId(),
                    cmd.managerId(),
                    cmd.label(),
                    cmd.description(),
                    cmd.intervalKm(),
                    cmd.intervalDays(),
                    cmd.preAlertKm(),
                    cmd.preAlertDays(),
                    true,
                    null, null
            );
            return planPort.save(plan);
        })).flatMap(saved -> {
            // Génère immédiatement les alertes pour les véhicules concernés (fire & forget)
            if (saved.getScope() == MaintenancePlan.PlanScope.VEHICLE && saved.getVehicleId() != null) {
                evaluateSingleVehiclePlan(saved.getVehicleId(), saved)
                        .doOnError(e -> log.warn("Erreur génération alertes plan {}: {}", saved.getId(), e.getMessage()))
                        .subscribe();
            } else {
                vehiclePort.getVehiclesByManager(saved.getManagerId())
                        .filter(v -> saved.getFleetId().equals(v.fleetId()))
                        .flatMap(v -> evaluateSingleVehiclePlan(v.id(), saved))
                        .doOnError(e -> log.warn("Erreur génération alertes plan flotte {}: {}", saved.getId(), e.getMessage()))
                        .subscribe();
            }
            return Mono.just(saved);
        });
    }

    @Override
    public Mono<MaintenancePlan> getPlanById(UUID id) {
        return planPort.findById(id)
                .switchIfEmpty(Mono.error(PreventiveMaintenanceException.planNotFound(id)));
    }

    @Override
    public Flux<MaintenancePlan> getPlansByFleet(UUID fleetId) {
        return planPort.findByFleetId(fleetId);
    }

    @Override
    public Flux<MaintenancePlan> getPlansByVehicle(UUID vehicleId) {
        return planPort.findByVehicleId(vehicleId);
    }

    @Override
    public Flux<MaintenancePlan> getPlansByManager(UUID managerId) {
        return planPort.findByManagerId(managerId);
    }

    @Override
    @Transactional
    public Mono<MaintenancePlan> updatePlan(UpdatePlanCommand cmd) {
        return planPort.findById(cmd.planId())
                .switchIfEmpty(Mono.error(PreventiveMaintenanceException.planNotFound(cmd.planId())))
                .flatMap(plan -> {
                    if (cmd.label() != null)       plan.setLabel(cmd.label());
                    if (cmd.description() != null) plan.setDescription(cmd.description());
                    if (cmd.intervalKm() != null)  plan.setIntervalKm(cmd.intervalKm());
                    if (cmd.intervalDays() != null) plan.setIntervalDays(cmd.intervalDays());
                    if (cmd.preAlertKm() != null)  plan.setPreAlertKm(cmd.preAlertKm());
                    if (cmd.preAlertDays() != null) plan.setPreAlertDays(cmd.preAlertDays());
                    if (cmd.active() != null)      plan.setActive(cmd.active());
                    plan.setUpdatedAt(LocalDateTime.now());
                    return planPort.save(plan);
                });
    }

    @Override
    @Transactional
    public Mono<MaintenancePlan> togglePlan(UUID planId, boolean active) {
        return planPort.findById(planId)
                .switchIfEmpty(Mono.error(PreventiveMaintenanceException.planNotFound(planId)))
                .flatMap(plan -> {
                    plan.setActive(active);
                    plan.setUpdatedAt(LocalDateTime.now());
                    return planPort.save(plan);
                });
    }

    @Override
    public Mono<Void> deletePlan(UUID id) {
        return planPort.existsById(id)
                .flatMap(exists -> {
                    if (!exists) return Mono.error(PreventiveMaintenanceException.planNotFound(id));
                    return planPort.deleteById(id);
                });
    }

    // ── Alertes — Lecture ─────────────────────────────────────────────────────

    @Override
    public Mono<MaintenanceAlert> getAlertById(UUID id) {
        return alertPort.findById(id)
                .switchIfEmpty(Mono.error(PreventiveMaintenanceException.alertNotFound(id)));
    }

    @Override
    public Flux<MaintenanceAlert> getActiveAlerts(UUID managerId) {
        return alertPort.findActiveByManagerId(managerId);
    }

    @Override
    public Flux<MaintenanceAlert> getUrgentAlerts(UUID managerId) {
        return alertPort.findUrgentByManagerId(managerId);
    }

    @Override
    public Flux<MaintenanceAlert> getAlertsByVehicle(UUID vehicleId) {
        return alertPort.findByVehicleId(vehicleId);
    }

    @Override
    public Flux<MaintenanceAlert> getAlertsByFleet(UUID fleetId) {
        return alertPort.findActiveByFleetId(fleetId);
    }

    // ── Alertes — Actions ─────────────────────────────────────────────────────

    @Override
    @Transactional
    public Mono<MaintenanceAlert> resolveAlert(UUID alertId, UUID maintenanceId) {
        return alertPort.findById(alertId)
                .switchIfEmpty(Mono.error(PreventiveMaintenanceException.alertNotFound(alertId)))
                .flatMap(alert -> {
                    if (alert.getStatus() == MaintenanceAlert.AlertStatus.RESOLVED) {
                        return Mono.error(PreventiveMaintenanceException.alertAlreadyResolved(alertId));
                    }
                    alert.resolve(maintenanceId);
                    return alertPort.save(alert)
                            .doOnSuccess(a -> log.info("✅ Alerte {} résolue par maintenance {}",
                                    alertId, maintenanceId));
                });
    }

    @Override
    public Flux<MaintenanceAlert> evaluatePlansForVehicle(UUID vehicleId) {
        return vehiclePort.getLocalDataById(vehicleId)
                .switchIfEmpty(Mono.error(PreventiveMaintenanceException.vehicleNotFound(vehicleId)))
                .flatMapMany(vehicle ->
                        planPort.findActiveByFleetId(vehicle.fleetId())
                                .filter(p -> p.getScope() == MaintenancePlan.PlanScope.FLEET
                                        || vehicleId.equals(p.getVehicleId()))
                                .flatMap(plan -> evaluateSingleVehiclePlan(vehicleId, plan))
                );
    }

    @Override
    public Flux<MaintenanceAlert> evaluateAllPlans(UUID managerId) {
        return vehiclePort.getVehiclesByManager(managerId)
                .flatMap(vehicle ->
                        planPort.findActiveByFleetId(vehicle.fleetId())
                                .filter(p -> p.getScope() == MaintenancePlan.PlanScope.FLEET
                                        || vehicle.id().equals(p.getVehicleId()))
                                .flatMap(plan -> evaluateSingleVehiclePlan(vehicle.id(), plan)
                                        .onErrorResume(e -> {
                                            log.warn("Erreur évaluation plan {} véhicule {}: {}",
                                                    plan.getId(), vehicle.id(), e.getMessage());
                                            return Mono.empty();
                                        })
                                )
                );
    }

    // ── Logique d'évaluation interne ──────────────────────────────────────────

    /**
     * Évalue un plan pour un véhicule spécifique et crée/met à jour l'alerte.
     * Logique :
     * 1. Récupère le kilométrage actuel et la date de dernière maintenance
     * 2. Calcule le km cible et la date cible
     * 3. Détermine si on est dans la zone de préalerte, DUE ou OVERDUE
     * 4. Crée ou met à jour l'alerte (évite les doublons)
     */
    private Mono<MaintenanceAlert> evaluateSingleVehiclePlan(UUID vehicleId, MaintenancePlan plan) {
        return vehiclePort.getLocalDataById(vehicleId)
                .flatMap(vehicle -> {
                    float currentKm = 0f;
                    LocalDate lastMaintenanceDate = null;
                    float lastMaintenanceKm = 0f;

                    if (vehicle.operationalParameters() != null) {
                        Float mileage = vehicle.operationalParameters().mileage();
                        currentKm = mileage != null ? mileage : 0f;
                    }
                    if (vehicle.maintenanceParameters() != null) {
                        lastMaintenanceDate = vehicle.maintenanceParameters().lastMaintenanceDate();
                    }

                    // Calcul des seuils cibles
                    Float targetKm = plan.computeNextMaintenanceKm(lastMaintenanceKm > 0 ? lastMaintenanceKm : currentKm);
                    LocalDate targetDate = plan.computeNextMaintenanceDate(
                            lastMaintenanceDate != null ? lastMaintenanceDate : LocalDate.now().minusDays(1));

                    // Détermine si l'alerte est pertinente (dans la zone de préalerte ou dépassée)
                    boolean kmAlert = targetKm != null
                            && (currentKm >= targetKm - (plan.getPreAlertKm() != null ? plan.getPreAlertKm() : 500));
                    boolean dateAlert = targetDate != null
                            && !LocalDate.now().isBefore(targetDate.minusDays(
                                    plan.getPreAlertDays() != null ? plan.getPreAlertDays() : 30));

                    if (!kmAlert && !dateAlert) {
                        return Mono.empty(); // Pas encore dans la zone d'alerte
                    }

                    MaintenanceAlert.TriggerType trigger =
                            kmAlert && dateAlert ? MaintenanceAlert.TriggerType.BOTH
                            : kmAlert ? MaintenanceAlert.TriggerType.MILEAGE
                            : MaintenanceAlert.TriggerType.DATE;

                    final float finalCurrentKm = currentKm;
                    final LocalDate finalLastDate = lastMaintenanceDate;
                    final Float finalTargetKm = targetKm;
                    final LocalDate finalTargetDate = targetDate;

                    // Cherche une alerte active existante pour ce véhicule/type (évite les doublons)
                    return alertPort.findActiveByVehicleAndType(vehicleId, plan.getMaintenanceType().name())
                            .flatMap(existing -> {
                                // Mise à jour de l'alerte existante
                                existing.refreshStatus(finalCurrentKm, LocalDate.now());
                                return alertPort.save(existing);
                            })
                            .switchIfEmpty(Mono.defer(() -> {
                                // Création d'une nouvelle alerte
                                Float kmRemaining = finalTargetKm != null
                                        ? finalTargetKm - finalCurrentKm : null;
                                int daysRemaining = finalTargetDate != null
                                        ? (int) LocalDate.now().until(finalTargetDate,
                                                java.time.temporal.ChronoUnit.DAYS)
                                        : 0;

                                MaintenanceAlert.AlertStatus initialStatus =
                                        (finalCurrentKm >= (finalTargetKm != null ? finalTargetKm : Float.MAX_VALUE)
                                        || !LocalDate.now().isBefore(finalTargetDate != null
                                                ? finalTargetDate : LocalDate.MAX))
                                        ? MaintenanceAlert.AlertStatus.DUE
                                        : MaintenanceAlert.AlertStatus.UPCOMING;

                                MaintenanceAlert alert = new MaintenanceAlert(
                                        null, plan.getId(), plan.getMaintenanceType(),
                                        vehicleId, vehicle.licensePlate(),
                                        vehicle.fleetId(), vehicle.managerId(),
                                        initialStatus, trigger,
                                        null, finalTargetKm, finalCurrentKm, kmRemaining,
                                        finalLastDate, finalTargetDate, daysRemaining,
                                        null, null, null, null
                                );
                                return alertPort.save(alert)
                                        .doOnSuccess(a -> log.info(
                                                "🔧 Nouvelle alerte {} [{}] pour véhicule {}",
                                                plan.getMaintenanceType(), a.getStatus(), vehicleId));
                            }));
                });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Mono<Void> validateFleetAndVehicle(UUID fleetId, UUID vehicleId,
                                                MaintenancePlan.PlanScope scope) {
        Mono<Void> fleetCheck = fleetPort.findById(fleetId)
                .switchIfEmpty(Mono.error(PreventiveMaintenanceException.fleetNotFound(fleetId)))
                .then();

        if (scope == MaintenancePlan.PlanScope.VEHICLE && vehicleId != null) {
            return fleetCheck.then(
                    vehiclePort.getLocalDataById(vehicleId)
                            .switchIfEmpty(Mono.error(
                                    PreventiveMaintenanceException.vehicleNotFound(vehicleId)))
                            .then()
            );
        }
        return fleetCheck;
    }
}
