package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.SyncMutationEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface SyncMutationR2dbcRepository extends ReactiveCrudRepository<SyncMutationEntity, UUID> {

    Mono<SyncMutationEntity> findByClientMutationIdAndUserId(UUID clientMutationId, UUID userId);
}
