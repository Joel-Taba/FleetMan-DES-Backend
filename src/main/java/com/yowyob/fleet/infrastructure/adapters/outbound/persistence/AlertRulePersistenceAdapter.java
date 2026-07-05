package com.yowyob.fleet.infrastructure.adapters.outbound.persistence;

import com.yowyob.fleet.domain.model.AlertRule;
import com.yowyob.fleet.domain.ports.out.AlertRulePersistencePort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.AlertRuleEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.AlertRuleR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AlertRulePersistenceAdapter implements AlertRulePersistencePort {

    private final AlertRuleR2dbcRepository repository;

    @Override
    public Mono<AlertRule> save(AlertRule rule) {
        AlertRuleEntity entity = toEntity(rule);
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID());
            entity.setNew(true);
        }
        return repository.save(entity).map(this::toDomain);
    }

    @Override
    public Mono<AlertRule> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Flux<AlertRule> findAll() {
        return repository.findAll().map(this::toDomain);
    }

    @Override
    public Flux<AlertRule> findByManagerId(UUID managerId) {
        return repository.findByManagerId(managerId).map(this::toDomain);
    }

    @Override
    public Flux<AlertRule> findActiveByManagerAndTrigger(UUID managerId,
                                                          AlertRule.TriggerType triggerType) {
        return repository.findActiveByManagerAndTrigger(managerId, triggerType.name())
                .map(this::toDomain);
    }

    @Override
    public Mono<Long> countSystemTemplatesByManager(UUID managerId) {
        return repository.countSystemTemplatesByManager(managerId);
    }

    @Override
    public Mono<Boolean> existsById(UUID id) {
        return repository.existsById(id);
    }

    @Override
    public Mono<Void> deleteById(UUID id) {
        return repository.deleteById(id);
    }

    private AlertRule toDomain(AlertRuleEntity e) {
        AlertRule.TriggerType trigger;
        try { trigger = AlertRule.TriggerType.valueOf(e.getTriggerType()); }
        catch (IllegalArgumentException ex) { trigger = AlertRule.TriggerType.INCIDENT_REPORTED; }

        AlertRule.ActionType action;
        try { action = AlertRule.ActionType.valueOf(e.getActionType()); }
        catch (IllegalArgumentException ex) { action = AlertRule.ActionType.IN_APP_NOTIFICATION; }

        AlertRule.TargetRole target;
        try { target = AlertRule.TargetRole.valueOf(e.getTargetRole()); }
        catch (IllegalArgumentException ex) { target = AlertRule.TargetRole.MANAGER; }

        return new AlertRule(
                e.getId(), e.getName(), e.getDescription(),
                e.getManagerId(), trigger, action, target,
                e.isActive(), e.isSystemTemplate(),
                e.getConditionValue(), e.getCreatedAt(), e.getUpdatedAt()
        );
    }

    private AlertRuleEntity toEntity(AlertRule r) {
        AlertRuleEntity e = new AlertRuleEntity();
        e.setId(r.getId());
        e.setName(r.getName());
        e.setDescription(r.getDescription());
        e.setManagerId(r.getManagerId());
        e.setTriggerType(r.getTriggerType().name());
        e.setActionType(r.getActionType().name());
        e.setTargetRole(r.getTargetRole().name());
        e.setActive(r.isActive());
        e.setSystemTemplate(r.isSystemTemplate());
        e.setConditionValue(r.getConditionValue());
        e.setCreatedAt(r.getCreatedAt() != null ? r.getCreatedAt() : LocalDateTime.now());
        e.setUpdatedAt(r.getUpdatedAt() != null ? r.getUpdatedAt() : LocalDateTime.now());
        return e;
    }
}
