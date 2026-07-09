package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.SubscriptionDocumentEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface SubscriptionDocumentR2dbcRepository
        extends ReactiveCrudRepository<SubscriptionDocumentEntity, UUID> {
    Flux<SubscriptionDocumentEntity> findByUserId(UUID userId);
}
