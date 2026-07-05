package com.yowyob.fleet.domain.ports.in;

import com.yowyob.fleet.domain.model.AlertEvent;
import com.yowyob.fleet.domain.model.AlertRule;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Port d'entrée : gestion des Règles d'alerte et des Événements d'alerte.
 * Invoqué par AlertRuleController et AlertEventController.
 */
public interface ManageAlertRuleUseCase {

    // ── Commandes ─────────────────────────────────────────────────────────────

    record CreateRuleCommand(
            String name,
            String description,
            UUID managerId,
            AlertRule.TriggerType triggerType,
            AlertRule.ActionType actionType,
            AlertRule.TargetRole targetRole,
            String conditionValue
    ) {}

    record UpdateRuleCommand(
            UUID ruleId,
            String name,
            String description,
            AlertRule.ActionType actionType,
            AlertRule.TargetRole targetRole,
            String conditionValue,
            Boolean active
    ) {}

    // ── Règles — CRUD ─────────────────────────────────────────────────────────

    /**
     * Crée une règle d'alerte personnalisée.
     * Lors de la création du premier manager, des règles système (templates)
     * sont provisionnées automatiquement.
     */
    Mono<AlertRule> createRule(CreateRuleCommand command);

    Mono<AlertRule> getRuleById(UUID id);

    /** Toutes les règles d'un manager (système + personnalisées). */
    Flux<AlertRule> getRulesByManager(UUID managerId);

    /** Règles actives d'un manager par type de déclencheur. */
    Flux<AlertRule> getActiveRulesByTrigger(UUID managerId, AlertRule.TriggerType triggerType);

    Mono<AlertRule> updateRule(UpdateRuleCommand command);

    /** Active ou désactive une règle. */
    Mono<AlertRule> toggleRule(UUID ruleId, boolean active);

    /**
     * Supprime une règle personnalisée.
     * Interdit sur les règles système (systemTemplate = true).
     */
    Mono<Void> deleteRule(UUID id);

    /**
     * Provisionne les 8 règles système par défaut pour un nouveau manager.
     * Idempotent : n'ajoute que les règles manquantes.
     */
    Mono<Void> provisionDefaultRules(UUID managerId);

    // ── Événements — Lecture et gestion ──────────────────────────────────────

    Mono<AlertEvent> getEventById(UUID id);

    /** Toutes les notifications in-app non lues d'un manager. */
    Flux<AlertEvent> getUnreadEvents(UUID managerId);

    /** Toutes les notifications d'un manager (historique). */
    Flux<AlertEvent> getAllEvents(UUID managerId);

    /** Compte les notifications non lues (pour le badge dans le header). */
    Mono<Long> countUnread(UUID managerId);

    /** Marque une notification comme lue. */
    Mono<AlertEvent> markAsRead(UUID eventId);

    /** Marque toutes les notifications non lues d'un manager comme lues. */
    Mono<Long> markAllAsRead(UUID managerId);

    /** Ignore/masque une notification. */
    Mono<AlertEvent> dismiss(UUID eventId);

    // ── Moteur — Déclenchement ────────────────────────────────────────────────

    /**
     * Évalue les règles actives d'un manager pour un type de déclencheur donné.
     * Crée les AlertEvent correspondants et déclenche les actions (in-app, email).
     * Appelé par les jobs planifiés des autres modules.
     *
     * @param managerId    Manager concerné
     * @param triggerType  Type d'événement à évaluer
     * @param entityId     ID de l'entité source (document, budget, véhicule...)
     * @param entityType   Type de l'entité source
     * @param contextValue Valeur contextuelle pour évaluer la condition
     *                     (ex: jours restants pour DOC_EXPIRY, % pour BUDGET_THRESHOLD)
     * @param title        Titre de la notification générée
     * @param message      Message détaillé de la notification
     */
    Flux<AlertEvent> triggerRules(UUID managerId,
                                   AlertRule.TriggerType triggerType,
                                   UUID entityId,
                                   String entityType,
                                   String contextValue,
                                   String title,
                                   String message);
}
