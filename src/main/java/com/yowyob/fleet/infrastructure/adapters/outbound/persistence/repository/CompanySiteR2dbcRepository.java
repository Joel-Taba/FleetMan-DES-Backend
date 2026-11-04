package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.CompanySiteEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface CompanySiteR2dbcRepository extends ReactiveCrudRepository<CompanySiteEntity, UUID> {

    @Query("""
            SELECT * FROM fleet.company_sites WHERE company_id = :companyId
            ORDER BY CASE site_type WHEN 'HEAD_OFFICE' THEN 0 ELSE 1 END, LOWER(name)
            """)
    Flux<CompanySiteEntity> findByCompany(UUID companyId);

    Mono<CompanySiteEntity> findByIdAndCompanyId(UUID id, UUID companyId);

    @Query("SELECT EXISTS (SELECT 1 FROM fleet.company_sites WHERE company_id = :companyId AND LOWER(name) = LOWER(:name) AND id <> :excludeId)")
    Mono<Boolean> nameTaken(UUID companyId, String name, UUID excludeId);

    @Query("SELECT EXISTS (SELECT 1 FROM fleet.company_sites WHERE company_id = :companyId AND site_type = 'HEAD_OFFICE' AND id <> :excludeId)")
    Mono<Boolean> headOfficeTaken(UUID companyId, UUID excludeId);

    @Query("SELECT COUNT(*) FROM fleet.fleets WHERE site_id = :siteId AND deleted_at IS NULL")
    Mono<Long> countFleets(UUID siteId);
}
