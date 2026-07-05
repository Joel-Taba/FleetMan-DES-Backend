package com.yowyob.fleet.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entité du domaine : Budget mensuel.
 * Domaine pur — aucune dépendance Spring, JPA ou Jackson.
 *
 * Un budget définit un plafond de dépenses mensuel pour une flotte ou un véhicule.
 * Permet de suivre la consommation budgétaire en temps réel et déclencher
 * des alertes automatiques à 80% et 100%.
 */
public class Budget {

    // ── Enums du domaine ─────────────────────────────────────────────────────

    /** Portée du budget */
    public enum BudgetScope {
        FLEET,    // Budget pour une flotte entière
        VEHICLE   // Budget pour un véhicule spécifique
    }

    /** Statut d'alerte budgétaire */
    public enum AlertLevel {
        NORMAL,     // < 80% consommé
        WARNING,    // ≥ 80% consommé
        EXCEEDED    // ≥ 100% consommé
    }

    // ── Champs ───────────────────────────────────────────────────────────────

    private UUID id;

    /** Portée du budget (flotte ou véhicule) */
    private final BudgetScope scope;

    /** ID de l'entité concernée (fleetId ou vehicleId selon scope) */
    private final UUID entityId;

    /** Manager propriétaire */
    private final UUID managerId;

    /** Mois du budget (format YYYY-MM-01) */
    private final LocalDate budgetMonth;

    /** Montant plafond en FCFA (obligatoire, > 0) */
    private BigDecimal amount;

    /** Montant déjà consommé (recalculé dynamiquement) */
    private BigDecimal consumed;

    /** Niveau d'alerte courant */
    private AlertLevel alertLevel;

    /** Indique si l'alerte 80% a déjà été envoyée */
    private boolean alert80Sent;

    /** Indique si l'alerte 100% a déjà été envoyée */
    private boolean alert100Sent;

    /** Notes / contexte du budget */
    private String notes;

    /** Date de création */
    private final LocalDateTime createdAt;

    /** Dernière mise à jour */
    private LocalDateTime updatedAt;

    // ─────────────────────────────────────────────────────────────────────────

    public Budget(UUID id,
                  BudgetScope scope,
                  UUID entityId,
                  UUID managerId,
                  LocalDate budgetMonth,
                  BigDecimal amount,
                  BigDecimal consumed,
                  AlertLevel alertLevel,
                  boolean alert80Sent,
                  boolean alert100Sent,
                  String notes,
                  LocalDateTime createdAt,
                  LocalDateTime updatedAt) {

        if (scope == null) {
            throw new IllegalArgumentException("La portée du budget est obligatoire.");
        }
        if (entityId == null) {
            throw new IllegalArgumentException("L'entité cible (flotte ou véhicule) est obligatoire.");
        }
        if (managerId == null) {
            throw new IllegalArgumentException("Le manager est obligatoire pour un budget.");
        }
        if (budgetMonth == null) {
            throw new IllegalArgumentException("Le mois du budget est obligatoire.");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Le montant du budget doit être strictement positif.");
        }

        this.id = id;
        this.scope = scope;
        this.entityId = entityId;
        this.managerId = managerId;
        this.budgetMonth = budgetMonth.withDayOfMonth(1); // Normalise au 1er du mois
        this.amount = amount;
        this.consumed = consumed != null ? consumed : BigDecimal.ZERO;
        this.alertLevel = alertLevel != null ? alertLevel : AlertLevel.NORMAL;
        this.alert80Sent = alert80Sent;
        this.alert100Sent = alert100Sent;
        this.notes = notes;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        this.updatedAt = updatedAt != null ? updatedAt : LocalDateTime.now();
    }

    // ── Méthodes métier ───────────────────────────────────────────────────────

    /**
     * Met à jour le montant consommé et recalcule le niveau d'alerte.
     * Retourne le nouveau niveau d'alerte pour déclencher les notifications.
     */
    public AlertLevel updateConsumed(BigDecimal newConsumed) {
        this.consumed = newConsumed != null ? newConsumed : BigDecimal.ZERO;
        this.updatedAt = LocalDateTime.now();
        this.alertLevel = computeAlertLevel();
        return this.alertLevel;
    }

    /**
     * Met à jour le montant plafond du budget.
     */
    public void updateAmount(BigDecimal newAmount) {
        if (newAmount == null || newAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Le montant du budget doit être strictement positif.");
        }
        this.amount = newAmount;
        this.updatedAt = LocalDateTime.now();
        this.alertLevel = computeAlertLevel();
    }

    /**
     * Calcule le taux de consommation en pourcentage.
     * Retourne 0 si le budget est nul.
     */
    public BigDecimal consumptionRate() {
        if (amount.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return consumed
                .multiply(BigDecimal.valueOf(100))
                .divide(amount, 2, RoundingMode.HALF_UP);
    }

    /**
     * Calcule le montant restant.
     */
    public BigDecimal remaining() {
        BigDecimal remaining = amount.subtract(consumed);
        return remaining.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : remaining;
    }

    /**
     * Indique si le budget est dépassé.
     */
    public boolean isExceeded() {
        return consumed.compareTo(amount) >= 0;
    }

    /**
     * Calcule le niveau d'alerte en fonction du taux de consommation.
     */
    private AlertLevel computeAlertLevel() {
        BigDecimal rate = consumptionRate();
        if (rate.compareTo(BigDecimal.valueOf(100)) >= 0) return AlertLevel.EXCEEDED;
        if (rate.compareTo(BigDecimal.valueOf(80)) >= 0)  return AlertLevel.WARNING;
        return AlertLevel.NORMAL;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public UUID getId()                        { return id; }
    public BudgetScope getScope()              { return scope; }
    public UUID getEntityId()                  { return entityId; }
    public UUID getManagerId()                 { return managerId; }
    public LocalDate getBudgetMonth()          { return budgetMonth; }
    public BigDecimal getAmount()              { return amount; }
    public BigDecimal getConsumed()            { return consumed; }
    public AlertLevel getAlertLevel()          { return alertLevel; }
    public boolean isAlert80Sent()             { return alert80Sent; }
    public boolean isAlert100Sent()            { return alert100Sent; }
    public String getNotes()                   { return notes; }
    public LocalDateTime getCreatedAt()        { return createdAt; }
    public LocalDateTime getUpdatedAt()        { return updatedAt; }

    // ── Setters limités ───────────────────────────────────────────────────────

    public void setId(UUID id)                         { this.id = id; }
    public void setNotes(String notes)                 { this.notes = notes; }
    public void setAlert80Sent(boolean sent)           { this.alert80Sent = sent; }
    public void setAlert100Sent(boolean sent)          { this.alert100Sent = sent; }
    public void setAlertLevel(AlertLevel level)        { this.alertLevel = level; }
    public void setConsumed(BigDecimal consumed)       { this.consumed = consumed != null ? consumed : BigDecimal.ZERO; }
    public void setUpdatedAt(LocalDateTime updatedAt)  { this.updatedAt = updatedAt; }
}
