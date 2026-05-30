package com.yowyob.fleet.infrastructure.adapters.outbound.persistence;

import com.yowyob.fleet.domain.model.Notification;
import com.yowyob.fleet.domain.ports.out.NotificationHistoryRepositoryPort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.NotificationEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.NotificationR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class NotificationHistoryR2dbcAdapter implements NotificationHistoryRepositoryPort {

    private final NotificationR2dbcRepository repository;

    @Override
    public Mono<Void> save(Notification domain) {
        NotificationEntity entity = new NotificationEntity(
            domain.id(),
            domain.userId(),
            domain.title(),
            domain.message(),
            domain.type(),
            domain.isRead(),
            LocalDateTime.now(),
            domain.dataJson(),
            true // New
        );
        return repository.save(entity).then();
    }

    @Override
    public Mono<PagedResult<Notification>> getUserNotifications(UUID userId, int page, int size) {
        return repository.countByUserId(userId)
            .flatMap(total -> {
                if (total == 0) {
                    return Mono.just(new PagedResult<Notification>(java.util.Collections.emptyList(), 0, 0, page));
                }
                
                int totalPages = (int) Math.ceil((double) total / size);
                
                return repository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size))
                    .map(this::toDomain)
                    .collectList()
                    .map(content -> new PagedResult<>(content, total, totalPages, page));
            });
    }

    private Notification toDomain(NotificationEntity entity) {
        return new Notification(
            entity.getId(),
            entity.getUserId(),
            entity.getTitle(),
            entity.getMessage(),
            entity.getType(),
            entity.isRead(),
            entity.getCreatedAt(),
            entity.getData()
        );
    }
}