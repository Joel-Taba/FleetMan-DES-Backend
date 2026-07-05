package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.VehicleDocumentEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.time.LocalDate;
import java.util.UUID;

public interface VehicleDocumentR2dbcRepository
        extends ReactiveCrudRepository<VehicleDocumentEntity, UUID> {

    Flux<VehicleDocumentEntity> findByVehicleId(UUID vehicleId);

    @Query("""
            SELECT vd.* FROM fleet.vehicle_documents vd
            JOIN fleet.vehicles v ON vd.vehicle_id = v.id
            WHERE v.manager_id = :managerId
            ORDER BY vd.expiry_date ASC
            """)
    Flux<VehicleDocumentEntity> findAllByManagerId(UUID managerId);

    @Query("""
            SELECT * FROM fleet.vehicle_documents
            WHERE expiry_date <= :threshold
              AND status NOT IN ('PENDING_RENEWAL')
            ORDER BY expiry_date ASC
            """)
    Flux<VehicleDocumentEntity> findExpiringBefore(LocalDate threshold);
}
