package com.yowyob.fleet.domain.ports.out;

import com.yowyob.fleet.domain.model.Schedule;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Port sortant — Contrat de persistance pour les plannings.
 */
public interface SchedulePersistencePort {

    Mono<Schedule> save(Schedule schedule);
    Mono<Schedule> findById(UUID id);
    Flux<Schedule> findAllByManagerId(UUID managerId);
    Flux<Schedule> findAllByFleetId(UUID fleetId);
    Flux<Schedule> findByPeriod(UUID managerId, LocalDate start, LocalDate end);
    Mono<Boolean> existsById(UUID id);
    Mono<Void> deleteById(UUID id);
}
