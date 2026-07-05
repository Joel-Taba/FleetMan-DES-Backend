package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.AlertRuleEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface AlertRuleR2dbcRepository extends ReactiveCrudRepository<AlertRuleEntity, UUID> {

    @Query("SELECT * FROM fleet.alert_rules WHERE manager_id = :managerId ORDER BY system_template DESC, created_at ASC")
    Flux<AlertRuleEntity> findByManagerId(UUID managerId);

    @Query("SELECT * FROM fleet.alert_rules WHERE manager_id = :managerId AND trigger_type = :triggerType AND active = true ORDER BY system_template DESC")
    Flux<AlertRuleEntity> findActiveByManagerAndTrigger(UUID managerId, String triggerType);

    @Query("SELECT COUNT(*) FROM fleet.alert_rules WHERE manager_id = :managerId AND system_template = true")
    Mono<Long> countSystemTemplatesByManager(UUID managerId);
}
