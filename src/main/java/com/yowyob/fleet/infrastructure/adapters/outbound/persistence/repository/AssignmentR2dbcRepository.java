package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.AssignmentEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public interface AssignmentR2dbcRepository
        extends ReactiveCrudRepository<AssignmentEntity, UUID> {

    @Query("""
            SELECT a.* FROM fleet.assignments a
            JOIN fleet.vehicles v ON a.vehicle_id = v.id
            WHERE v.manager_id = :managerId
            ORDER BY a.start_datetime DESC
            """)
    Flux<AssignmentEntity> findAllByManagerId(UUID managerId);

    Flux<AssignmentEntity> findByScheduleId(UUID scheduleId);

    Flux<AssignmentEntity> findByVehicleId(UUID vehicleId);

    Flux<AssignmentEntity> findByDriverId(UUID driverId);

    @Query("""
            SELECT * FROM fleet.assignments
            WHERE driver_id = :driverId
              AND DATE(start_datetime) = :date
            ORDER BY start_datetime ASC
            """)
    Flux<AssignmentEntity> findByDriverIdAndDate(UUID driverId, LocalDate date);

    @Query("""
            SELECT a.* FROM fleet.assignments a
            JOIN fleet.vehicles v ON a.vehicle_id = v.id
            WHERE v.manager_id = :managerId
              AND DATE(a.start_datetime) >= :start
              AND DATE(a.start_datetime) <= :end
            ORDER BY a.start_datetime ASC
            """)
    Flux<AssignmentEntity> findByDateRangeAndManagerId(UUID managerId,
                                                        LocalDate start,
                                                        LocalDate end);

    /**
     * Détecte les conflits d'affectation pour un véhicule sur une plage horaire.
     * Retourne les affectations actives (PENDING ou IN_PROGRESS) qui se chevauchent.
     * Exclut optionnellement une affectation (pour les mises à jour).
     */
    @Query("""
            SELECT * FROM fleet.assignments
            WHERE vehicle_id = :vehicleId
              AND status IN ('PENDING','IN_PROGRESS')
              AND start_datetime < :endDatetime
              AND end_datetime   > :startDatetime
              AND (:excludeId IS NULL OR id != :excludeId)
            """)
    Flux<AssignmentEntity> findConflictingByVehicle(UUID vehicleId,
                                                     LocalDateTime startDatetime,
                                                     LocalDateTime endDatetime,
                                                     UUID excludeId);

    /**
     * Détecte les conflits d'affectation pour un conducteur sur une plage horaire.
     */
    @Query("""
            SELECT * FROM fleet.assignments
            WHERE driver_id = :driverId
              AND status IN ('PENDING','IN_PROGRESS')
              AND start_datetime < :endDatetime
              AND end_datetime   > :startDatetime
              AND (:excludeId IS NULL OR id != :excludeId)
            """)
    Flux<AssignmentEntity> findConflictingByDriver(UUID driverId,
                                                    LocalDateTime startDatetime,
                                                    LocalDateTime endDatetime,
                                                    UUID excludeId);
}
