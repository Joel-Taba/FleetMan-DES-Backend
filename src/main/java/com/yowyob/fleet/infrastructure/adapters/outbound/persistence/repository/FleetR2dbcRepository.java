package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.FleetEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.data.r2dbc.repository.Query;

import java.util.UUID;

public interface FleetR2dbcRepository extends ReactiveCrudRepository<FleetEntity, UUID> {

    Flux<FleetEntity> findAllByManagerId(UUID managerId);

    Mono<Boolean> existsByIdAndManagerId(UUID id, UUID managerId);

    Mono<Long> countByManagerId(UUID managerId);

    @Query("SELECT f.* FROM fleet.fleets f " +
            "JOIN fleet.fleet_managers fm ON f.manager_id = fm.user_id " +
            "WHERE fm.company_name = (SELECT company_name FROM fleet.fleet_managers WHERE user_id = :userId)")
    Flux<FleetEntity> findAllBySameCompanyAsUser(UUID userId);

    @Query("SELECT EXISTS (" +
            "  SELECT 1 FROM fleet.fleet_managers fm1 " +
            "  JOIN fleet.fleet_managers fm2 ON fm1.company_name = fm2.company_name " +
            "  WHERE fm1.user_id = :managerId AND fm2.user_id = :requesterId" +
            ")")
    Mono<Boolean> shareSameCompany(UUID managerId, UUID requesterId);
}