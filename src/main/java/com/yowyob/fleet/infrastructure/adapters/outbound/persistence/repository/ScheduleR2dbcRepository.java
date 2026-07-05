package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.ScheduleEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.time.LocalDate;
import java.util.UUID;

public interface ScheduleR2dbcRepository
        extends ReactiveCrudRepository<ScheduleEntity, UUID> {

    @Query("""
            SELECT s.* FROM fleet.schedules s
            JOIN fleet.fleets f ON s.fleet_id = f.id
            WHERE f.manager_id = :managerId
            ORDER BY s.start_date DESC
            """)
    Flux<ScheduleEntity> findAllByManagerId(UUID managerId);

    Flux<ScheduleEntity> findByFleetId(UUID fleetId);

    @Query("""
            SELECT s.* FROM fleet.schedules s
            JOIN fleet.fleets f ON s.fleet_id = f.id
            WHERE f.manager_id = :managerId
              AND s.start_date >= :start
              AND s.end_date   <= :end
            ORDER BY s.start_date DESC
            """)
    Flux<ScheduleEntity> findByPeriodAndManagerId(UUID managerId,
                                                   LocalDate start,
                                                   LocalDate end);
}
