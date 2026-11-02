package com.yowyob.fleet.application.service;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.AssignmentR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.BudgetR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.DriverR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.ExpenseR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.FuelRechargeR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.IncidentR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.MaintenanceAlertR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.MaintenancePlanR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.MaintenanceR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.ScheduleR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.TripR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.VehicleLocalR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.UUID;

/**
 * Cloisonnement par ressource (« un gestionnaire ne voit que SA flotte », analyse des
 * besoins §4.3) pour les modules qui n'en avaient pas : affectations, plannings,
 * opérations, dépenses/budgets, documents, maintenance préventive.
 *
 * Toutes les règles se ramènent à {@link AccessScopeService#assertFleetAccess} via la
 * flotte du véhicule ou du chauffeur concerné. Une ressource introuvable passe le
 * contrôle : le service métier renvoie ensuite son propre 404.
 */
@Service
@RequiredArgsConstructor
public class ResourceAccessService {

    private final AccessScopeService accessScope;
    private final VehicleLocalR2dbcRepository vehicleRepo;
    private final DriverR2dbcRepository driverRepo;
    private final TripR2dbcRepository tripRepo;
    private final AssignmentR2dbcRepository assignmentRepo;
    private final ScheduleR2dbcRepository scheduleRepo;
    private final MaintenanceR2dbcRepository maintenanceRepo;
    private final IncidentR2dbcRepository incidentRepo;
    private final FuelRechargeR2dbcRepository fuelRepo;
    private final ExpenseR2dbcRepository expenseRepo;
    private final BudgetR2dbcRepository budgetRepo;
    private final MaintenancePlanR2dbcRepository planRepo;
    private final MaintenanceAlertR2dbcRepository alertRepo;

    // ── Ressources de base ───────────────────────────────────────────────────

    public Mono<Void> assertFleetAccess(UUID actorId, UUID fleetId) {
        return accessScope.assertFleetAccess(actorId, fleetId);
    }

    /**
     * Véhicule rattaché à une flotte du périmètre. Un véhicule sans flotte reste
     * accessible au gestionnaire qui l'a créé (création « indépendante »), et non plus
     * seulement aux administrateurs.
     */
    public Mono<Void> assertVehicleAccess(UUID actorId, UUID vehicleId) {
        if (vehicleId == null) return Mono.empty();
        return vehicleRepo.findById(vehicleId)
                .flatMap(v -> {
                    if (v.getFleetId() != null) {
                        return accessScope.assertVehicleFleetAccess(actorId, v.getFleetId());
                    }
                    return accessScope.resolveLocalUserId(actorId)
                            .flatMap(localId -> localId.equals(v.getManagerId())
                                    ? Mono.<Void>empty()
                                    : accessScope.assertVehicleFleetAccess(actorId, null));
                });
    }

    /** Chauffeur lui-même, ou chauffeur rattaché à une flotte du périmètre. */
    public Mono<Void> assertDriverAccess(UUID actorId, UUID driverId) {
        if (driverId == null) return Mono.empty();
        return accessScope.resolveLocalUserId(actorId).flatMap(localId -> {
            if (localId.equals(driverId)) return Mono.<Void>empty();
            return driverRepo.findById(driverId)
                    .flatMap(d -> d.getFleetId() != null
                            ? accessScope.assertFleetAccess(actorId, d.getFleetId())
                            : Mono.<Void>error(new AccessDeniedException("Chauffeur hors périmètre.")));
        });
    }

    /**
     * Un chauffeur ne déclare (carburant, incident, maintenance) que pour le véhicule
     * qui lui est affecté ou celui de son trajet en cours.
     */
    public Mono<Void> assertDriverOperatesVehicle(UUID driverActorId, UUID vehicleId) {
        return accessScope.resolveLocalUserId(driverActorId).flatMap(localId ->
                driverRepo.findById(localId)
                        .filter(d -> Objects.equals(d.getAssignedVehicleId(), vehicleId))
                        .map(d -> true)
                        .switchIfEmpty(tripRepo.findInProgressTripForDriver(localId)
                                .map(t -> Objects.equals(t.getVehicleId(), vehicleId)))
                        .defaultIfEmpty(false)
                        .flatMap(ok -> Boolean.TRUE.equals(ok)
                                ? Mono.<Void>empty()
                                : Mono.<Void>error(new AccessDeniedException(
                                        "Ce véhicule ne vous est pas affecté et n'est pas celui de votre trajet en cours."))));
    }

    // ── Planification ────────────────────────────────────────────────────────

    /** Le chauffeur de l'affectation, ou un gestionnaire de sa flotte. */
    public Mono<Void> assertAssignmentAccess(UUID actorId, UUID assignmentId) {
        return assignmentRepo.findById(assignmentId)
                .flatMap(a -> accessScope.resolveLocalUserId(actorId)
                        .flatMap(localId -> localId.equals(a.getDriverId())
                                ? Mono.<Void>empty()
                                : accessScope.assertFleetAccess(actorId, a.getFleetId())));
    }

    /** Gestion d'une affectation : réservée à la flotte (le chauffeur ne réaffecte ni ne supprime). */
    public Mono<Void> assertAssignmentManagement(UUID actorId, UUID assignmentId) {
        return assignmentRepo.findById(assignmentId)
                .flatMap(a -> accessScope.assertFleetAccess(actorId, a.getFleetId()));
    }

    public Mono<Void> assertScheduleAccess(UUID actorId, UUID scheduleId) {
        return scheduleRepo.findById(scheduleId)
                .flatMap(s -> accessScope.assertFleetAccess(actorId, s.getFleetId()));
    }

    // ── Opérations ───────────────────────────────────────────────────────────

    public Mono<Void> assertMaintenanceAccess(UUID actorId, UUID maintenanceId) {
        return maintenanceRepo.findById(maintenanceId)
                .flatMap(m -> driverSelfOrVehicle(actorId, m.getDriverId(), m.getVehicleId()));
    }

    public Mono<Void> assertIncidentAccess(UUID actorId, UUID incidentId) {
        return incidentRepo.findById(incidentId)
                .flatMap(i -> driverSelfOrVehicle(actorId, i.getDriverId(), i.getVehicleId()));
    }

    public Mono<Void> assertFuelRechargeAccess(UUID actorId, UUID rechargeId) {
        return fuelRepo.findById(rechargeId)
                .flatMap(f -> driverSelfOrVehicle(actorId, f.getDriverId(), f.getVehicleId()));
    }

    // ── Dépenses & budgets ───────────────────────────────────────────────────

    /** Le chauffeur concerné ou l'auteur de la dépense, ou un gestionnaire / l'admin de sa flotte. */
    public Mono<Void> assertExpenseAccess(UUID actorId, UUID expenseId) {
        return expenseRepo.findById(expenseId)
                .flatMap(e -> accessScope.resolveLocalUserId(actorId).flatMap(localId ->
                        localId.equals(e.getDriverId()) || localId.equals(e.getCreatedBy())
                                ? Mono.<Void>empty()
                                : e.getVehicleId() != null
                                        ? assertVehicleAccess(actorId, e.getVehicleId())
                                        : accessScope.assertFleetAccess(actorId, e.getFleetId())));
    }

    public Mono<Void> assertBudgetAccess(UUID actorId, UUID budgetId) {
        return budgetRepo.findById(budgetId)
                .flatMap(b -> assertBudgetTarget(actorId, b.getScope(), b.getEntityId()));
    }

    /** Cible d'un budget : flotte (scope FLEET) ou véhicule (scope VEHICLE) du périmètre. */
    public Mono<Void> assertBudgetTarget(UUID actorId, String scope, UUID entityId) {
        return "VEHICLE".equals(scope)
                ? assertVehicleAccess(actorId, entityId)
                : accessScope.assertFleetAccess(actorId, entityId);
    }

    // ── Maintenance préventive ───────────────────────────────────────────────

    public Mono<Void> assertMaintenancePlanAccess(UUID actorId, UUID planId) {
        return planRepo.findById(planId)
                .flatMap(p -> p.getVehicleId() != null
                        ? assertVehicleAccess(actorId, p.getVehicleId())
                        : accessScope.assertFleetAccess(actorId, p.getFleetId()));
    }

    public Mono<Void> assertMaintenanceAlertAccess(UUID actorId, UUID alertId) {
        return alertRepo.findById(alertId)
                .flatMap(a -> assertVehicleAccess(actorId, a.getVehicleId()));
    }

    /** Ressource personnelle du gestionnaire (règle d'alerte, notification). */
    public Mono<Void> assertOwner(UUID actorId, UUID ownerId) {
        return accessScope.resolveLocalUserId(actorId)
                .flatMap(localId -> localId.equals(ownerId)
                        ? Mono.<Void>empty()
                        : Mono.<Void>error(new AccessDeniedException("Ressource hors périmètre.")));
    }

    private Mono<Void> driverSelfOrVehicle(UUID actorId, UUID driverId, UUID vehicleId) {
        return accessScope.resolveLocalUserId(actorId)
                .flatMap(localId -> localId.equals(driverId)
                        ? Mono.<Void>empty()
                        : assertVehicleAccess(actorId, vehicleId));
    }
}
