package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.AlertException;
import com.yowyob.fleet.domain.model.AlertEvent;
import com.yowyob.fleet.domain.model.AlertRule;
import com.yowyob.fleet.domain.ports.in.ManageAlertRuleUseCase;
import com.yowyob.fleet.domain.ports.out.AlertEventPersistencePort;
import com.yowyob.fleet.domain.ports.out.AlertRulePersistencePort;
import com.yowyob.fleet.domain.ports.out.SendAlertPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertRuleEngineService implements ManageAlertRuleUseCase {

    private final AlertRulePersistencePort rulePort;
    private final AlertEventPersistencePort eventPort;
    private final SendAlertPort sendAlertPort;

    // ── Règles — CRUD ─────────────────────────────────────────────────────────

    @Override
    public Mono<AlertRule> createRule(CreateRuleCommand cmd) {
        AlertRule rule = new AlertRule(
                null, cmd.name(), cmd.description(),
                cmd.managerId(), cmd.triggerType(),
                cmd.actionType(), cmd.targetRole(),
                true, false,
                cmd.conditionValue(), null, null
        );
        return rulePort.save(rule);
    }

    @Override
    public Mono<AlertRule> getRuleById(UUID id) {
        return rulePort.findById(id)
                .switchIfEmpty(Mono.error(AlertException.ruleNotFound(id)));
    }

    @Override
    public Flux<AlertRule> getRulesByManager(UUID managerId) {
        return rulePort.findByManagerId(managerId);
    }

    @Override
    public Flux<AlertRule> getActiveRulesByTrigger(UUID managerId,
                                                     AlertRule.TriggerType triggerType) {
        return rulePort.findActiveByManagerAndTrigger(managerId, triggerType);
    }

    @Override
    public Mono<AlertRule> updateRule(UpdateRuleCommand cmd) {
        return rulePort.findById(cmd.ruleId())
                .switchIfEmpty(Mono.error(AlertException.ruleNotFound(cmd.ruleId())))
                .flatMap(rule -> {
                    if (cmd.name() != null)           rule.setName(cmd.name());
                    if (cmd.description() != null)    rule.setDescription(cmd.description());
                    if (cmd.conditionValue() != null) rule.setConditionValue(cmd.conditionValue());
                    if (cmd.active() != null)         rule.toggle(cmd.active());
                    rule.setUpdatedAt(LocalDateTime.now());
                    return rulePort.save(rule);
                });
    }

    @Override
    public Mono<AlertRule> toggleRule(UUID ruleId, boolean active) {
        return rulePort.findById(ruleId)
                .switchIfEmpty(Mono.error(AlertException.ruleNotFound(ruleId)))
                .flatMap(rule -> {
                    rule.toggle(active);
                    return rulePort.save(rule);
                });
    }

    @Override
    public Mono<Void> deleteRule(UUID id) {
        return rulePort.findById(id)
                .switchIfEmpty(Mono.error(AlertException.ruleNotFound(id)))
                .flatMap(rule -> {
                    if (rule.isSystemTemplate()) {
                        return Mono.error(AlertException.systemRuleNotDeletable(id));
                    }
                    return rulePort.deleteById(id);
                });
    }

    @Override
    public Mono<Void> provisionDefaultRules(UUID managerId) {
        return rulePort.countSystemTemplatesByManager(managerId)
                .flatMap(count -> {
                    if (count > 0) return Mono.empty(); // Déjà provisionnées

                    List<AlertRule> defaults = buildDefaultRules(managerId);
                    return Flux.fromIterable(defaults)
                            .flatMap(rulePort::save)
                            .then();
                });
    }

    // ── Événements — Lecture ─────────────────────────────────────────────────

    @Override
    public Mono<AlertEvent> getEventById(UUID id) {
        return eventPort.findById(id)
                .switchIfEmpty(Mono.error(AlertException.eventNotFound(id)));
    }

    @Override
    public Flux<AlertEvent> getUnreadEvents(UUID managerId) {
        return eventPort.findUnreadByManagerId(managerId);
    }

    @Override
    public Flux<AlertEvent> getAllEvents(UUID managerId) {
        return eventPort.findAllByManagerId(managerId);
    }

    @Override
    public Mono<Long> countUnread(UUID managerId) {
        return eventPort.countUnreadByManagerId(managerId);
    }

    @Override
    public Mono<AlertEvent> markAsRead(UUID eventId) {
        return eventPort.findById(eventId)
                .switchIfEmpty(Mono.error(AlertException.eventNotFound(eventId)))
                .flatMap(event -> {
                    event.markAsRead();
                    return eventPort.save(event);
                });
    }

    @Override
    public Mono<Long> markAllAsRead(UUID managerId) {
        return eventPort.markAllAsReadByManagerId(managerId);
    }

    @Override
    public Mono<AlertEvent> dismiss(UUID eventId) {
        return eventPort.findById(eventId)
                .switchIfEmpty(Mono.error(AlertException.eventNotFound(eventId)))
                .flatMap(event -> {
                    event.dismiss();
                    return eventPort.save(event);
                });
    }

    // ── Moteur — Déclenchement ────────────────────────────────────────────────

    @Override
    public Flux<AlertEvent> triggerRules(UUID managerId,
                                          AlertRule.TriggerType triggerType,
                                          UUID entityId,
                                          String entityType,
                                          String contextValue,
                                          String title,
                                          String message) {

        return rulePort.findActiveByManagerAndTrigger(managerId, triggerType)
                .filter(rule -> evaluateCondition(rule, contextValue))
                .flatMap(rule -> {
                    AlertEvent event = new AlertEvent(
                            null, rule.getId(), rule.getName(),
                            managerId, triggerType, rule.getActionType(),
                            title, message,
                            entityId, entityType,
                            AlertEvent.ReadStatus.UNREAD,
                            LocalDateTime.now(), null
                    );

                    // Persiste toujours en in-app
                    Mono<AlertEvent> persistMono = sendAlertPort.sendInApp(event);

                    // Envoie email en fire & forget si actionType = EMAIL
                    if (rule.getActionType() == AlertRule.ActionType.EMAIL) {
                        persistMono = persistMono
                                .doOnSuccess(saved ->
                                        sendAlertPort.sendEmail(saved, null)
                                                .doOnError(e -> log.warn("Erreur envoi email règle {}: {}",
                                                        rule.getId(), e.getMessage()))
                                                .subscribe()
                                );
                    }

                    return persistMono
                            .doOnSuccess(e -> log.debug(
                                    "✅ Alerte déclenchée [{}] → manager {} | {}",
                                    triggerType, managerId, title));
                });
    }

    // ── Logique d'évaluation des conditions ───────────────────────────────────

    /**
     * Évalue si la condition de la règle est satisfaite par la valeur contextuelle.
     *
     * Pour les conditions numériques : contextValue ≤ conditionThreshold
     * Exemples :
     *  - DOCUMENT_EXPIRY,  conditionValue=30, contextValue=7  → 7 ≤ 30 → true (alerte)
     *  - BUDGET_THRESHOLD, conditionValue=80, contextValue=85 → 85 ≥ 80 → true
     *  - DRIVER_SCORE_DROP,conditionValue=50, contextValue=40 → 40 ≤ 50 → true
     */
    private boolean evaluateCondition(AlertRule rule, String contextValue) {
        if (contextValue == null || contextValue.isBlank()) return true; // Pas de filtre

        int threshold = rule.getConditionThreshold();
        if (threshold < 0) return true; // Pas de seuil numérique configuré

        try {
            int value = Integer.parseInt(contextValue);
            return switch (rule.getTriggerType()) {
                case DOCUMENT_EXPIRY    -> value <= threshold;      // Alerte si peu de jours restants
                case BUDGET_THRESHOLD   -> value >= threshold;      // Alerte si % consommé ≥ seuil
                case DRIVER_SCORE_DROP  -> value <= threshold;      // Alerte si score ≤ seuil
                case FUEL_ANOMALY       -> value >= threshold;      // Alerte si dépassement ≥ seuil %
                case MAINTENANCE_ALERT_DUE -> true;                 // Pas de seuil numérique
                case INCIDENT_REPORTED  -> true;                    // Filtré par sévérité en amont
                case TRIP_OVERDUE       -> value >= threshold;      // Alerte si retard ≥ seuil minutes
                case MAINTENANCE_DECLARED -> true;                  // Toujours notifié, pas de seuil (jamais
                                                                     // utilisé via AlertRule en pratique)
            };
        } catch (NumberFormatException e) {
            // Condition string (ex: sévérité incident) — on passe
            return true;
        }
    }

    // ── Templates de règles par défaut ────────────────────────────────────────

    /**
     * Construit les 8 règles système provisionnées pour chaque nouveau manager.
     */
    private List<AlertRule> buildDefaultRules(UUID managerId) {
        return List.of(
                template(managerId, "Document expirant (30j)",
                        "Alerte quand un document légal expire dans 30 jours ou moins.",
                        AlertRule.TriggerType.DOCUMENT_EXPIRY,
                        AlertRule.ActionType.IN_APP_NOTIFICATION,
                        AlertRule.TargetRole.MANAGER, "30"),

                template(managerId, "Document expirant (7j)",
                        "Alerte critique quand un document légal expire dans 7 jours ou moins.",
                        AlertRule.TriggerType.DOCUMENT_EXPIRY,
                        AlertRule.ActionType.EMAIL,
                        AlertRule.TargetRole.MANAGER, "7"),

                template(managerId, "Budget à 80%",
                        "Alerte quand le budget mensuel d'une flotte ou d'un véhicule est consommé à 80%.",
                        AlertRule.TriggerType.BUDGET_THRESHOLD,
                        AlertRule.ActionType.IN_APP_NOTIFICATION,
                        AlertRule.TargetRole.MANAGER, "80"),

                template(managerId, "Budget dépassé (100%)",
                        "Alerte critique quand le budget est entièrement consommé.",
                        AlertRule.TriggerType.BUDGET_THRESHOLD,
                        AlertRule.ActionType.EMAIL,
                        AlertRule.TargetRole.MANAGER, "100"),

                template(managerId, "Maintenance en retard",
                        "Alerte quand une maintenance préventive est en statut DUE ou OVERDUE.",
                        AlertRule.TriggerType.MAINTENANCE_ALERT_DUE,
                        AlertRule.ActionType.IN_APP_NOTIFICATION,
                        AlertRule.TargetRole.MANAGER, null),

                template(managerId, "Score conducteur faible",
                        "Alerte quand le score mensuel d'un conducteur tombe en dessous de 50/100.",
                        AlertRule.TriggerType.DRIVER_SCORE_DROP,
                        AlertRule.ActionType.IN_APP_NOTIFICATION,
                        AlertRule.TargetRole.MANAGER, "50"),

                template(managerId, "Incident déclaré",
                        "Notification immédiate lors de la déclaration de tout incident terrain.",
                        AlertRule.TriggerType.INCIDENT_REPORTED,
                        AlertRule.ActionType.IN_APP_NOTIFICATION,
                        AlertRule.TargetRole.MANAGER, null),

                template(managerId, "Anomalie consommation carburant",
                        "Alerte quand la consommation d'un véhicule dépasse de 30% la moyenne de la flotte.",
                        AlertRule.TriggerType.FUEL_ANOMALY,
                        AlertRule.ActionType.IN_APP_NOTIFICATION,
                        AlertRule.TargetRole.MANAGER, "30")
        );
    }

    private AlertRule template(UUID managerId, String name, String description,
                                AlertRule.TriggerType trigger, AlertRule.ActionType action,
                                AlertRule.TargetRole target, String conditionValue) {
        return new AlertRule(null, name, description, managerId,
                trigger, action, target, true, true, conditionValue, null, null);
    }
}
