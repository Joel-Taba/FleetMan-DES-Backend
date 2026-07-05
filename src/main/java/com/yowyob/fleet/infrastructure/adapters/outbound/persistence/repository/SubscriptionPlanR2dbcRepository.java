package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.SubscriptionPlanEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Repository
public interface SubscriptionPlanR2dbcRepository extends ReactiveCrudRepository<SubscriptionPlanEntity, UUID> {
    Flux<SubscriptionPlanEntity> findAllByIsActiveTrue();
    Mono<SubscriptionPlanEntity> findByName(String name);
}
