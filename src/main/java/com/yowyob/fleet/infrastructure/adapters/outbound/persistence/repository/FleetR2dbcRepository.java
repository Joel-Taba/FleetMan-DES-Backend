package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.FleetEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

public interface FleetR2dbcRepository extends ReactiveCrudRepository<FleetEntity, UUID> {
    
    Flux<FleetEntity> findAllByManagerId(UUID managerId);
    Mono<Boolean> existsByIdAndManagerId(UUID id, UUID managerId);
    @Query("SELECT COUNT(*) FROM fleet.fleets WHERE manager_id = :managerId AND deleted_at IS NULL")
    Mono<Long> countByManagerId(UUID managerId);

    @Query("SELECT COUNT(*) FROM fleet.fleets WHERE manager_id = :managerId AND deleted_at IS NULL")
    Mono<Long> countActiveByManagerId(UUID managerId);
    Mono<FleetEntity> findByKernelOrganizationId(UUID kernelOrganizationId);

    @Query("SELECT * FROM fleet.fleets WHERE manager_id = :managerId AND deleted_at IS NULL AND updated_at > :since")
    Flux<FleetEntity> findActiveByManagerIdUpdatedAfter(UUID managerId, Instant since);

    @Query("SELECT * FROM fleet.fleets WHERE manager_id = :managerId AND deleted_at IS NOT NULL AND deleted_at > :since")
    Flux<FleetEntity> findDeletedByManagerIdSince(UUID managerId, Instant since);

    @Query("""
            SELECT f.* FROM fleet.fleets f
            WHERE f.deleted_at IS NULL
              AND (
                f.created_by = :adminId
                OR f.manager_id IN (
                    SELECT fm.user_id FROM fleet.fleet_managers fm WHERE fm.created_by_admin_id = :adminId
                )
              )
            ORDER BY f.created_at DESC NULLS LAST
            """)
    Flux<FleetEntity> findAllByOrganizationAdminId(UUID adminId);

    @Query("""
            SELECT COUNT(*) FROM fleet.fleets f
            WHERE f.deleted_at IS NULL
              AND (
                f.created_by = :adminId
                OR f.manager_id IN (
                    SELECT fm.user_id FROM fleet.fleet_managers fm WHERE fm.created_by_admin_id = :adminId
                )
              )
            """)
    Mono<Long> countByOrganizationAdminId(UUID adminId);
}