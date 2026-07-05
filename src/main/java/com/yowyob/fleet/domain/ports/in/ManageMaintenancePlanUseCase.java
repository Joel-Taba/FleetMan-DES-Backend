package com.yowyob.fleet.domain.ports.in;

import com.yowyob.fleet.domain.model.MaintenanceAlert;
import com.yowyob.fleet.domain.model.MaintenancePlan;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Port d'entrée : cas d'utilisation pour la gestion des Plans et Alertes de Maintenance Préventive.
 * Invoqué par MaintenancePlanController et MaintenanceAlertController.
 */
public interface ManageMaintenancePlanUseCase {

    // ── Commandes ─────────────────────────────────────────────────────────────

    record CreatePlanCommand(
            MaintenancePlan.MaintenanceType maintenanceType,
            MaintenancePlan.PlanScope scope,
            UUID fleetId,
            UUID vehicleId,         // Obligatoire si scope = VEHICLE
            UUID managerId,
            String label,
            String description,
            Integer intervalKm,     // Au moins l'un des deux doit être fourni
            Integer intervalDays,
            Integer preAlertKm,
            Integer preAlertDays
    ) {}

    record UpdatePlanCommand(
            UUID planId,
            String label,
            String description,
            Integer intervalKm,
            Integer intervalDays,
            Integer preAlertKm,
            Integer preAlertDays,
            Boolean active
    ) {}

    // ── Plans — CRUD ──────────────────────────────────────────────────────────

    /**
     * Crée un plan de maintenance préventive (flotte ou véhicule).
     * Génère immédiatement les alertes pour les véhicules concernés.
     */
    Mono<MaintenancePlan> createPlan(CreatePlanCommand command);

    Mono<MaintenancePlan> getPlanById(UUID id);

    /** Plans actifs d'une flotte. */
    Flux<MaintenancePlan> getPlansByFleet(UUID fleetId);

    /** Plans spécifiques à un véhicule (scope = VEHICLE). */
    Flux<MaintenancePlan> getPlansByVehicle(UUID vehicleId);

    /** Tous les plans d'un manager (flotte + véhicules). */
    Flux<MaintenancePlan> getPlansByManager(UUID managerId);

    Mono<MaintenancePlan> updatePlan(UpdatePlanCommand command);

    /** Active/désactive un plan sans le supprimer. */
    Mono<MaintenancePlan> togglePlan(UUID planId, boolean active);

    Mono<Void> deletePlan(UUID id);

    // ── Alertes — Lecture ─────────────────────────────────────────────────────

    Mono<MaintenanceAlert> getAlertById(UUID id);

    /** Toutes les alertes actives (non résolues) d'un manager. */
    Flux<MaintenanceAlert> getActiveAlerts(UUID managerId);

    /** Alertes urgentes (DUE + OVERDUE) d'un manager — pour le dashboard. */
    Flux<MaintenanceAlert> getUrgentAlerts(UUID managerId);

    /** Toutes les alertes d'un véhicule (historique complet). */
    Flux<MaintenanceAlert> getAlertsByVehicle(UUID vehicleId);

    /** Alertes actives d'une flotte spécifique. */
    Flux<MaintenanceAlert> getAlertsByFleet(UUID fleetId);

    // ── Alertes — Actions ─────────────────────────────────────────────────────

    /**
     * Résout manuellement une alerte en précisant la maintenance effectuée.
     * Lie l'alerte à une Maintenance existante via son ID.
     */
    Mono<MaintenanceAlert> resolveAlert(UUID alertId, UUID maintenanceId);

    /**
     * Déclenche l'évaluation de tous les plans actifs pour un véhicule donné.
     * Crée ou met à jour les alertes correspondantes.
     * Utilisé après une mise à jour du kilométrage.
     */
    Flux<MaintenanceAlert> evaluatePlansForVehicle(UUID vehicleId);

    /**
     * Évalue tous les plans de tous les véhicules actifs d'une flotte.
     * Utilisé par le job planifié quotidien.
     */
    Flux<MaintenanceAlert> evaluateAllPlans(UUID managerId);
}
