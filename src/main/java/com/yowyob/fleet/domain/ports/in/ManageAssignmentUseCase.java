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
    Mono<Assignment> updateStatus(UUID id, Assignment.Status newStatus, BigDecimal actualKm);
    Mono<Assignment> updateResources(UUID id, UUID vehicleId, UUID driverId);
    Mono<Void> delete(UUID id);

    // ── Records Command ───────────────────────────────────────────────────────

    record CreateAssignmentCommand(
            UUID scheduleId,
            UUID fleetId,
            UUID vehicleId,
            UUID driverId,
            UUID missionId,
            LocalDateTime startDatetime,
            LocalDateTime endDatetime,
            String startLocation,
            String endLocation,
            BigDecimal estimatedKm,
            String notes
    ) {}
}
