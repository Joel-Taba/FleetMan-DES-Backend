package com.yowyob.fleet.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entité du domaine : Événement d'alerte déclenché.
 * Domaine pur — aucune dépendance Spring, JPA ou Jackson.
 *
 * Un AlertEvent est créé à chaque fois qu'une règle est évaluée positivement.
 * Il constitue la piste d'audit de toutes les alertes envoyées.
 */
public class AlertEvent {

    // ── Enums du domaine ─────────────────────────────────────────────────────

    /** Statut de lecture de la notification in-app */
    public enum ReadStatus {
        UNREAD,
        READ,
        DISMISSED
    }

    // ── Champs ───────────────────────────────────────────────────────────────

    private UUID id;

    /** Règle qui a déclenché cet événement */
    private final UUID ruleId;

    /** Nom de la règle (dénormalisé pour l'affichage sans jointure) */
    private final String ruleName;

    /** Manager destinataire */
    private final UUID managerId;

    /** Type de déclencheur */
    private final AlertRule.TriggerType triggerType;

    /** Type d'action exécutée */
    private final AlertRule.ActionType actionType;

    /**
     * Titre court de la notification (ex: "Document assurance expirant")
     */
    private final String title;

    /**
     * Message détaillé de la notification
     * (ex: "L'assurance du véhicule CE-001-AA expire dans 7 jours.")
     */
    private final String message;

    /** ID de l'entité source qui a déclenché la règle (véhicule, document, budget...) */
    private final UUID sourceEntityId;

    /** Type de l'entité source (VEHICLE, DOCUMENT, BUDGET, DRIVER, INCIDENT...) */
    private final String sourceEntityType;

    /** Statut de lecture pour les notifications in-app */
    private ReadStatus readStatus;

    /** Date d'envoi */
    private final LocalDateTime sentAt;

    /** Date de lecture (null si non lu) */
    private LocalDateTime readAt;

    // ─────────────────────────────────────────────────────────────────────────

    public AlertEvent(UUID id,
                      UUID ruleId,
                      String ruleName,
                      UUID managerId,
                      AlertRule.TriggerType triggerType,
                      AlertRule.ActionType actionType,
                      String title,
                      String message,
                      UUID sourceEntityId,
                      String sourceEntityType,
                      ReadStatus readStatus,
                      LocalDateTime sentAt,
                      LocalDateTime readAt) {

        if (managerId == null)
            throw new IllegalArgumentException("Le manager destinataire est obligatoire.");
        if (title == null || title.isBlank())
            throw new IllegalArgumentException("Le titre de l'alerte est obligatoire.");

        this.id = id;
        this.ruleId = ruleId;
        this.ruleName = ruleName;
        this.managerId = managerId;
        this.triggerType = triggerType;
        this.actionType = actionType;
        this.title = title;
        this.message = message;
        this.sourceEntityId = sourceEntityId;
        this.sourceEntityType = sourceEntityType;
        this.readStatus = readStatus != null ? readStatus : ReadStatus.UNREAD;
        this.sentAt = sentAt != null ? sentAt : LocalDateTime.now();
        this.readAt = readAt;
    }

    // ── Méthodes métier ───────────────────────────────────────────────────────

    /** Marque la notification comme lue. */
    public void markAsRead() {
        if (this.readStatus == ReadStatus.UNREAD) {
            this.readStatus = ReadStatus.READ;
            this.readAt = LocalDateTime.now();
        }
    }

    /** Ignore/masque la notification. */
    public void dismiss() {
        this.readStatus = ReadStatus.DISMISSED;
        if (this.readAt == null) this.readAt = LocalDateTime.now();
    }

    public boolean isUnread() { return this.readStatus == ReadStatus.UNREAD; }

    // ── Getters ───────────────────────────────────────────────────────────────

    public UUID getId()                            { return id; }
    public UUID getRuleId()                        { return ruleId; }
    public String getRuleName()                    { return ruleName; }
    public UUID getManagerId()                     { return managerId; }
    public AlertRule.TriggerType getTriggerType()  { return triggerType; }
    public AlertRule.ActionType getActionType()    { return actionType; }
    public String getTitle()                       { return title; }
    public String getMessage()                     { return message; }
    public UUID getSourceEntityId()                { return sourceEntityId; }
    public String getSourceEntityType()            { return sourceEntityType; }
    public ReadStatus getReadStatus()              { return readStatus; }
    public LocalDateTime getSentAt()               { return sentAt; }
    public LocalDateTime getReadAt()               { return readAt; }

    public void setId(UUID id)                     { this.id = id; }
    public void setReadStatus(ReadStatus status)   { this.readStatus = status; }
    public void setReadAt(LocalDateTime at)        { this.readAt = at; }
}
