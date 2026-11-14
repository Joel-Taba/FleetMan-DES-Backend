package com.yowyob.fleet.domain.ports.out;

import com.yowyob.fleet.domain.model.Mission;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Port sortant : contrat de persistance pour l'agrégat Mission.
 * Implémenté par MissionPersistenceAdapter dans l'infrastructure R2DBC.
 */
public interface MissionPersistencePort {

    Mono<Mission> save(Mission mission);

    Mono<Mission> findById(UUID id);

    Flux<Mission> findAll();

    Flux<Mission> findByManagerId(UUID fleetManagerId);

    Flux<Mission> findByDriverId(UUID driverId);

    Flux<Mission> findByVehicleId(UUID vehicleId);

    Flux<Mission> findByStatus(Mission.Status status, UUID fleetManagerId);

    Flux<Mission> findActiveMissions(UUID fleetManagerId);

    Mono<Boolean> existsOverlappingMissionForVehicle(UUID vehicleId, LocalDateTime start, LocalDateTime end, UUID excludeMissionId);

    Mono<Boolean> existsOverlappingMissionForDriver(UUID driverId, LocalDateTime start, LocalDateTime end, UUID excludeMissionId);

    Mono<Long> countByStatus(Mission.Status status, UUID fleetManagerId);

    Mono<Void> deleteById(UUID id);
}

