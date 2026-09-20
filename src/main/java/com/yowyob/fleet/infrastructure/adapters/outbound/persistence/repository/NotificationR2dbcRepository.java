package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.NotificationEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface NotificationR2dbcRepository extends ReactiveCrudRepository<NotificationEntity, UUID> {

    Flux<NotificationEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Mono<Long> countByUserId(UUID userId);

    Mono<NotificationEntity> findByIdAndUserId(UUID id, UUID userId);

    @Modifying
    @Query("UPDATE fleet.notifications SET is_read = true WHERE user_id = :userId AND is_read = false")
    Mono<Long> markAllAsReadByUserId(UUID userId);
}