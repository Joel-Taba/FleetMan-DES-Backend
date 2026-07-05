package com.yowyob.fleet.domain.ports.out;

import com.yowyob.fleet.domain.model.AlertRule;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Port sortant : contrat de persistance des Règles d'alerte.
 */
public interface AlertRulePersistencePort {

    Mono<AlertRule> save(AlertRule rule);

    Mono<AlertRule> findById(UUID id);

    Flux<AlertRule> findAll();

    /** Toutes les règles d'un manager (système + personnalisées). */
    Flux<AlertRule> findByManagerId(UUID managerId);

    /** Règles actives pour un type de déclencheur. */
    Flux<AlertRule> findActiveByManagerAndTrigger(UUID managerId, AlertRule.TriggerType triggerType);

    /** Compte les règles système déjà provisionnées pour un manager. */
    Mono<Long> countSystemTemplatesByManager(UUID managerId);

    Mono<Boolean> existsById(UUID id);

    Mono<Void> deleteById(UUID id);
}
