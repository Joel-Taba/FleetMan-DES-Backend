package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.DeliveryProofEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface DeliveryProofR2dbcRepository extends ReactiveCrudRepository<DeliveryProofEntity, UUID> {

    Mono<DeliveryProofEntity> findByMissionId(UUID missionId);

    Mono<Boolean> existsByMissionId(UUID missionId);
}

