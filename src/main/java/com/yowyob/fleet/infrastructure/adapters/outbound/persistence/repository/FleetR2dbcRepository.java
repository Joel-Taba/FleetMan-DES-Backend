package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.FleetEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface FleetR2dbcRepository extends ReactiveCrudRepository<FleetEntity, UUID> {
    
    Flux<FleetEntity> findAllByManagerId(UUID managerId);
    Mono<Boolean> existsByIdAndManagerId(UUID id, UUID managerId);
    Mono<Long> countByManagerId(UUID managerId);
    Mono<FleetEntity> findByKernelOrganizationId(UUID kernelOrganizationId);
}