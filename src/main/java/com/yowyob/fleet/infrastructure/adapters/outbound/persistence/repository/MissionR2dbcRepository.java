package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.MissionEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface MissionR2dbcRepository extends ReactiveCrudRepository<MissionEntity, UUID> {

    Flux<MissionEntity> findByFleetManagerIdOrderByScheduledStartDesc(UUID fleetManagerId);

    Flux<MissionEntity> findByDriverIdOrderByScheduledStartDesc(UUID driverId);

    Flux<MissionEntity> findByVehicleIdOrderByScheduledStartDesc(UUID vehicleId);

    Flux<MissionEntity> findByStatusAndFleetManagerIdOrderByScheduledStartDesc(String status, UUID fleetManagerId);

    @Query("SELECT * FROM fleet.missions WHERE status = 'ACTIVE' AND fleet_manager_id = :managerId ORDER BY scheduled_start DESC")
    Flux<MissionEntity> findActiveMissions(UUID managerId);

    @Query("SELECT COUNT(*) > 0 FROM fleet.missions WHERE vehicle_id = :vehicleId " +
           "AND status IN ('PENDING', 'ACTIVE') " +
           "AND (:excludeMissionId IS NULL OR id != :excludeMissionId) " +
           "AND scheduled_start < :end AND COALESCE(scheduled_end, scheduled_start + interval '4 hours') > :start")
    Mono<Boolean> existsOverlappingForVehicle(UUID vehicleId, LocalDateTime start, LocalDateTime end, UUID excludeMissionId);

    @Query("SELECT COUNT(*) > 0 FROM fleet.missions WHERE driver_id = :driverId " +
           "AND status IN ('PENDING', 'ACTIVE') " +
           "AND (:excludeMissionId IS NULL OR id != :excludeMissionId) " +
           "AND scheduled_start < :end AND COALESCE(scheduled_end, scheduled_start + interval '4 hours') > :start")
    Mono<Boolean> existsOverlappingForDriver(UUID driverId, LocalDateTime start, LocalDateTime end, UUID excludeMissionId);

    Mono<Long> countByStatusAndFleetManagerId(String status, UUID fleetManagerId);
}

