package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.PlanFeatureEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface PlanFeatureR2dbcRepository extends ReactiveCrudRepository<PlanFeatureEntity, UUID> {

    Flux<PlanFeatureEntity> findByPlanId(UUID planId);

    Mono<Void> deleteByPlanId(UUID planId);
}
