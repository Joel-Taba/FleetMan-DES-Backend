package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.DriverDocumentEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.time.LocalDate;
import java.util.UUID;

public interface DriverDocumentR2dbcRepository
        extends ReactiveCrudRepository<DriverDocumentEntity, UUID> {

    Flux<DriverDocumentEntity> findByDriverId(UUID driverId);

    @Query("""
            SELECT dd.* FROM fleet.driver_documents dd
            JOIN fleet.drivers d ON dd.driver_id = d.user_id
            JOIN fleet.fleets f  ON d.fleet_id   = f.id
            WHERE f.manager_id = :managerId
            ORDER BY dd.expiry_date ASC NULLS LAST
            """)
    Flux<DriverDocumentEntity> findAllByManagerId(UUID managerId);

    @Query("""
            SELECT * FROM fleet.driver_documents
            WHERE expiry_date IS NOT NULL
              AND expiry_date <= :threshold
              AND status NOT IN ('PENDING_RENEWAL')
            ORDER BY expiry_date ASC
            """)
    Flux<DriverDocumentEntity> findExpiringBefore(LocalDate threshold);
}
