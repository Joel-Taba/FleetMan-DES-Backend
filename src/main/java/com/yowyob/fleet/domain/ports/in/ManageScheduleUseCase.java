package com.yowyob.fleet.domain.ports.in;

import com.yowyob.fleet.domain.model.Schedule;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Port entrant — Cas d'utilisation pour la gestion des plannings.
 * Domaine pur — aucune dépendance Spring ou infrastructure.
 */
public interface ManageScheduleUseCase {

    Mono<Schedule> createSchedule(CreateScheduleCommand cmd);
    Mono<Schedule> getById(UUID id);
    Flux<Schedule> getAllByManager(UUID managerId);
    Flux<Schedule> getAllByFleet(UUID fleetId);
    Flux<Schedule> getByPeriod(UUID managerId, LocalDate start, LocalDate end);
    Mono<Schedule> update(UpdateScheduleCommand cmd);
    Mono<Schedule> publish(UUID id);
    Mono<Schedule> archive(UUID id);
    Mono<Void> delete(UUID id);

    // ── Records Command ───────────────────────────────────────────────────────

    record CreateScheduleCommand(
            UUID fleetId,
            UUID managerId,
            String title,
            String periodType,   // DAILY, WEEKLY, MONTHLY
            LocalDate startDate,
            LocalDate endDate,
            String notes
    ) {}

    record UpdateScheduleCommand(
            UUID scheduleId,
            String title,
            String notes
    ) {}
}
