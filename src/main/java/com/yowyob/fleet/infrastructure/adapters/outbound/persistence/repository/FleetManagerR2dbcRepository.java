package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.FleetManagerEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface FleetManagerR2dbcRepository extends ReactiveCrudRepository<FleetManagerEntity, UUID> {

    Flux<FleetManagerEntity> findByCreatedByAdminId(UUID createdByAdminId);

    Mono<Boolean> existsByUserIdAndCreatedByAdminId(UUID userId, UUID createdByAdminId);

    @Query("SELECT COUNT(*) FROM fleet.fleet_managers fm JOIN fleet.users u ON u.id = fm.user_id WHERE fm.created_by_admin_id = :adminId AND u.deleted_at IS NULL")
    Mono<Long> countByCreatedByAdminId(UUID adminId);
}