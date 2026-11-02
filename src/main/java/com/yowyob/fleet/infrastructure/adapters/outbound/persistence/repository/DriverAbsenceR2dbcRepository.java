package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.DriverAbsenceEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.Collection;
import java.util.UUID;

public interface DriverAbsenceR2dbcRepository extends ReactiveCrudRepository<DriverAbsenceEntity, UUID> {

    @Query("SELECT * FROM fleet.driver_absences WHERE driver_id = :driverId ORDER BY start_date DESC")
    Flux<DriverAbsenceEntity> findByDriverId(UUID driverId);

    /** Absences non annulées qui ne sont pas terminées avant {@code from} (en cours ou à venir). */
    @Query("""
            SELECT * FROM fleet.driver_absences
            WHERE driver_id IN (:driverIds) AND cancelled_at IS NULL
              AND (end_date IS NULL OR end_date >= :from)
            """)
    Flux<DriverAbsenceEntity> findOpenForDrivers(Collection<UUID> driverIds, java.time.LocalDate from);

    @Query("""
            SELECT * FROM fleet.driver_absences
            WHERE driver_id = :driverId AND cancelled_at IS NULL
              AND start_date <= :to AND (end_date IS NULL OR end_date >= :from)
            """)
    Flux<DriverAbsenceEntity> findOverlapping(UUID driverId, java.time.LocalDate from, java.time.LocalDate to);
}
