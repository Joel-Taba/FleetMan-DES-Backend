package com.yowyob.fleet.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entité du domaine : Règle d'alerte métier.
 * Domaine pur — aucune dépendance Spring, JPA ou Jackson.
 *
 * Une règle définit : "SI [condition] ALORS [action]"
 * Exemple : "SI document expire dans 30j ALORS envoyer notification in-app au Manager"
 *
 * Les règles sont évaluées par le moteur AlertRuleEngineService sur réception
 * d'un événement métier (Spring ApplicationEvent).
 */
public class AlertRule {

    // ── Enums du domaine ─────────────────────────────────────────────────────

    /** Type de déclencheur */
    public enum TriggerType {
        DOCUMENT_EXPIRY,        // Document légal expire dans X jours
        BUDGET_THRESHOLD,       // Budget consommé à X%
        MAINTENANCE_ALERT_DUE,  // Alerte maintenance DUE ou OVERDUE
        DRIVER_SCORE_DROP,      // Score conducteur < seuil
        FUEL_ANOMALY,           // Consommation carburant anormale
        INCIDENT_REPORTED,      // Incident déclaré (selon sévérité)
        TRIP_OVERDUE,            // Trajet non terminé après délai
        MAINTENANCE_DECLARED     // Nouvelle maintenance déclarée (distinct de MAINTENANCE_ALERT_DUE,
                                  // qui concerne un rappel préventif). Toujours notifié directement,
                                  // jamais utilisé dans une AlertRule configurable (pas de contrainte
                                  // CHECK correspondante sur alert_rules.trigger_type).
    }

    /** Action déclenchée */
    public enum ActionType {
        IN_APP_NOTIFICATION,    // Notification in-app (toujours activée)
        EMAIL                   // Email au destinataire
    }

    /** Destinataire de l'action */
    public enum TargetRole {
        MANAGER,
        ADMIN,
        DRIVER
    }

    // ── Champs ───────────────────────────────────────────────────────────────

    private UUID id;

    /** Nom lisible de la règle */
    private String name;

    /** Description optionnelle */
    private String description;

    /** Manager propriétaire */
    private final UUID managerId;

    /** Type de déclencheur */
    private final TriggerType triggerType;

    /** Type d'action déclenchée */
    private final ActionType actionType;

    /** Destinataire */
    private final TargetRole targetRole;

    /** Règle active ou non */
    private boolean active;

    /** Règle fournie par défaut (template système, non supprimable) */
    private final boolean systemTemplate;

    /**
     * Paramètres de la condition sous forme de valeur seuil encodée en String.
     * Exemples :
     *  - DOCUMENT_EXPIRY → "30"  (jours avant expiration)
     *  - BUDGET_THRESHOLD → "80"  (pourcentage de consommation)
     *  - DRIVER_SCORE_DROP → "50" (score minimal)
     *  - INCIDENT_REPORTED → "HIGH" (sévérité minimale)
     *  - FUEL_ANOMALY → "30"     (% de dépassement vs moyenne flotte)
     */
    private String conditionValue;

    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ─────────────────────────────────────────────────────────────────────────

    public AlertRule(UUID id,
                     String name,
                     String description,
                     UUID managerId,
                     TriggerType triggerType,
                     ActionType actionType,
                     TargetRole targetRole,
                     boolean active,
                     boolean systemTemplate,
                     String conditionValue,
                     LocalDateTime createdAt,
                     LocalDateTime updatedAt) {

        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Le nom de la règle est obligatoire.");
        if (managerId == null)
            throw new IllegalArgumentException("Le manager est obligatoire.");
        if (triggerType == null)
            throw new IllegalArgumentException("Le type de déclencheur est obligatoire.");
        if (actionType == null)
            throw new IllegalArgumentException("Le type d'action est obligatoire.");
        if (targetRole == null)
            throw new IllegalArgumentException("Le destinataire est obligatoire.");

        this.id = id;
        this.name = name;
        this.description = description;
        this.managerId = managerId;
        this.triggerType = triggerType;
        this.actionType = actionType;
        this.targetRole = targetRole;
        this.active = active;
        this.systemTemplate = systemTemplate;
        this.conditionValue = conditionValue;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        this.updatedAt = updatedAt != null ? updatedAt : LocalDateTime.now();
    }

    // ── Méthodes métier ───────────────────────────────────────────────────────

    /** Active ou désactive la règle. Interdit sur les templates système. */
    public void toggle(boolean active) {
        this.active = active;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Retourne le seuil numérique de la condition.
     * Retourne -1 si non applicable (ex: pas de seuil numérique).
     */
    public int getConditionThreshold() {
        try {
            return conditionValue != null ? Integer.parseInt(conditionValue) : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public UUID getId()                    { return id; }
    public String getName()               { return name; }
    public String getDescription()        { return description; }
    public UUID getManagerId()            { return managerId; }
    public TriggerType getTriggerType()   { return triggerType; }
    public ActionType getActionType()     { return actionType; }
    public TargetRole getTargetRole()     { return targetRole; }
    public boolean isActive()             { return active; }
    public boolean isSystemTemplate()     { return systemTemplate; }
    public String getConditionValue()     { return conditionValue; }
    public LocalDateTime getCreatedAt()   { return createdAt; }
    public LocalDateTime getUpdatedAt()   { return updatedAt; }

    public void setId(UUID id)                         { this.id = id; }
    public void setName(String name)                   { this.name = name; }
    public void setDescription(String desc)            { this.description = desc; }
    public void setConditionValue(String val)          { this.conditionValue = val; }
    public void setUpdatedAt(LocalDateTime updatedAt)  { this.updatedAt = updatedAt; }
}
