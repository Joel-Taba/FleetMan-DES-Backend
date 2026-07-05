package com.yowyob.fleet.infrastructure.adapters.outbound.persistence;

import com.yowyob.fleet.domain.model.AlertEvent;
import com.yowyob.fleet.domain.model.AlertRule;
import com.yowyob.fleet.domain.ports.out.AlertEventPersistencePort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.AlertEventEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.AlertEventR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AlertEventPersistenceAdapter implements AlertEventPersistencePort {

    private final AlertEventR2dbcRepository repository;

    @Override
    public Mono<AlertEvent> save(AlertEvent event) {
        AlertEventEntity entity = toEntity(event);
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID());
            entity.setNew(true);
        }
        return repository.save(entity).map(this::toDomain);
    }

    @Override
    public Mono<AlertEvent> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Flux<AlertEvent> findUnreadByManagerId(UUID managerId) {
        return repository.findUnreadByManagerId(managerId).map(this::toDomain);
    }

    @Override
    public Flux<AlertEvent> findAllByManagerId(UUID managerId) {
        return repository.findAllByManagerId(managerId).map(this::toDomain);
    }

    @Override
    public Mono<Long> countUnreadByManagerId(UUID managerId) {
        return repository.countUnreadByManagerId(managerId);
    }

    @Override
    public Mono<Long> markAllAsReadByManagerId(UUID managerId) {
        return repository.markAllAsReadByManagerId(managerId);
    }

    @Override
    public Mono<Boolean> existsById(UUID id) {
        return repository.existsById(id);
    }

    @Override
    public Mono<Void> deleteById(UUID id) {
        return repository.deleteById(id);
    }

    private AlertEvent toDomain(AlertEventEntity e) {
        AlertRule.TriggerType trigger = null;
        try { trigger = AlertRule.TriggerType.valueOf(e.getTriggerType()); }
        catch (Exception ex) { /* null = inconnu */ }

        AlertRule.ActionType action = null;
        try { action = AlertRule.ActionType.valueOf(e.getActionType()); }
        catch (Exception ex) { action = AlertRule.ActionType.IN_APP_NOTIFICATION; }

        AlertEvent.ReadStatus status = AlertEvent.ReadStatus.UNREAD;
        try { status = AlertEvent.ReadStatus.valueOf(e.getReadStatus()); }
        catch (Exception ex) { /* défaut UNREAD */ }

        return new AlertEvent(
                e.getId(), e.getRuleId(), e.getRuleName(),
                e.getManagerId(), trigger, action,
                e.getTitle(), e.getMessage(),
                e.getSourceEntityId(), e.getSourceEntityType(),
                status, e.getSentAt(), e.getReadAt()
        );
    }

    private AlertEventEntity toEntity(AlertEvent a) {
        AlertEventEntity e = new AlertEventEntity();
        e.setId(a.getId());
        e.setRuleId(a.getRuleId());
        e.setRuleName(a.getRuleName());
        e.setManagerId(a.getManagerId());
        e.setTriggerType(a.getTriggerType() != null ? a.getTriggerType().name() : null);
        e.setActionType(a.getActionType() != null ? a.getActionType().name() : null);
        e.setTitle(a.getTitle());
        e.setMessage(a.getMessage());
        e.setSourceEntityId(a.getSourceEntityId());
        e.setSourceEntityType(a.getSourceEntityType());
        e.setReadStatus(a.getReadStatus() != null
                ? a.getReadStatus().name()
                : AlertEvent.ReadStatus.UNREAD.name());
        e.setSentAt(a.getSentAt() != null ? a.getSentAt() : LocalDateTime.now());
        e.setReadAt(a.getReadAt());
        return e;
    }
}
