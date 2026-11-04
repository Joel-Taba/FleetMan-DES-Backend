package com.yowyob.fleet.domain.ports.in;

import com.yowyob.fleet.domain.model.Assignment;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Port entrant — Cas d'utilisation pour la gestion des affectations.
 * Domaine pur — aucune dépendance Spring ou infrastructure.
 */
public interface ManageAssignmentUseCase {

    Mono<Assignment> createAssignment(CreateAssignmentCommand cmd);
    Mono<Assignment> getById(UUID id);
    Flux<Assignment> getAllByManager(UUID managerId);
    Flux<Assignment> getAllBySchedule(UUID scheduleId);
    Flux<Assignment> getByVehicle(UUID vehicleId);
    Flux<Assignment> getByDriver(UUID driverId);
    Flux<Assignment> getByDriverToday(UUID driverId);
    Flux<Assignment> getByDateRange(UUID managerId, LocalDate start, LocalDate end);
    Flux<Assignment> getConflicts(UUID managerId);
    Mono<Boolean> checkVehicleAvailability(UUID vehicleId,
                                            LocalDateTime start,
                                            LocalDateTime end,
                                            UUID excludeAssignmentId);
    Mono<Boolean> checkDriverAvailability(UUID driverId,
                                           LocalDateTime start,
                                           LocalDateTime end,
                                           UUID excludeAssignmentId);
    /**
     * Statuts manuels uniquement : NO_SHOW (chauffeur absent au poste) et CANCELLED. Le démarrage
     * (premier départ) et la fin (heure de fin, km des trajets) sont automatiques.
     */
    Mono<Assignment> updateStatus(UUID id, Assignment.Status newStatus, BigDecimal actualKm, String reason);

    /** Nombre de trajets rattachés au poste. */
    Mono<Long> countLinkedTrips(UUID assignmentId);

    /** Un chauffeur ne voit un poste que si son programme est publié (ou s'il n'a pas de programme). */
    Mono<Boolean> isVisibleToDriver(Assignment assignment);
    Mono<Assignment> updateShift(UUID id, ShiftChange change);
    Mono<Void> delete(UUID id, String reason);

    // ── Records Command ───────────────────────────────────────────────────────

    record CreateAssignmentCommand(
            UUID scheduleId,
            UUID fleetId,
            UUID vehicleId,
            UUID driverId,
            LocalDateTime startDatetime,
            LocalDateTime endDatetime,
            String notes,
            /** Motif d'un dépassement des règles de temps de travail (sinon refusé). */
            String workRuleOverride,
            /** Motif communiqué au chauffeur si le planning est déjà publié (facultatif à la création). */
            String changeReason
    ) {}

    /** Réaffectation et/ou nouveaux horaires d'un poste en attente. */
    record ShiftChange(UUID vehicleId, UUID driverId, LocalDateTime startDatetime, LocalDateTime endDatetime,
                       String reason, String workRuleOverride) {}
}
