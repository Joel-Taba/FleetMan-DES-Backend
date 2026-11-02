package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.CompanyEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface CompanyR2dbcRepository extends ReactiveCrudRepository<CompanyEntity, UUID> {

    /** Entreprise d'un administrateur (lui-même) ou d'un gestionnaire (celle de l'admin qui l'a créé). */
    @Query("""
            SELECT c.* FROM fleet.companies c
            WHERE c.id = :userId
               OR c.id = (SELECT fm.created_by_admin_id FROM fleet.fleet_managers fm WHERE fm.user_id = :userId)
            ORDER BY (c.id = :userId) DESC
            LIMIT 1
            """)
    Mono<CompanyEntity> findForUser(UUID userId);
}
