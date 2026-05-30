package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.NotificationEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface NotificationR2dbcRepository extends ReactiveCrudRepository<NotificationEntity, UUID> {

    // Récupération paginée par utilisateur
    Flux<NotificationEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    // Compte pour la pagination
    Mono<Long> countByUserId(UUID userId);
}