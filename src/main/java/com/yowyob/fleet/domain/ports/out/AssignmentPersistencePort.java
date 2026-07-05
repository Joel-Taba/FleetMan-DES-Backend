package com.yowyob.fleet.domain.ports.out;

import com.yowyob.fleet.domain.model.Assignment;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Port sortant — Contrat de persistance pour les affectations.
 */
public interface AssignmentPersistencePort {

    Mono<Assignment> save(Assignment assignment);
    Mono<Assignment> findById(UUID id);
    Flux<Assignment> findAllByManagerId(UUID managerId);
    Flux<Assignment> findAllByScheduleId(UUID scheduleId);
    Flux<Assignment> findByVehicleId(UUID vehicleId);
    Flux<Assignment> findByDriverId(UUID driverId);
    Flux<Assignment> findByDriverIdAndDate(UUID driverId, LocalDate date);
    Flux<Assignment> findByDateRange(UUID managerId, LocalDate start, LocalDate end);

    /**
     * Détecte les affectations actives qui chevauchent une plage horaire
     * pour un véhicule donné. Utilisé pour la détection de conflits.
     */
    Flux<Assignment> findConflictingByVehicle(UUID vehicleId,
                                               LocalDateTime start,
                                               LocalDateTime end,
                                               UUID excludeId);

    /**
     * Détecte les affectations actives qui chevauchent une plage horaire
     * pour un conducteur donné. Utilisé pour la détection de conflits.
     */
    Flux<Assignment> findConflictingByDriver(UUID driverId,
                                              LocalDateTime start,
                                              LocalDateTime end,
                                              UUID excludeId);

    Mono<Boolean> existsById(UUID id);
    Mono<Void> deleteById(UUID id);
}
