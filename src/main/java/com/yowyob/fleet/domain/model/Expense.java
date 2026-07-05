package com.yowyob.fleet.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entité du domaine : Dépense opérationnelle.
 * Domaine pur — aucune dépendance Spring, JPA ou Jackson.
 *
 * Une dépense représente un coût engagé pour exploiter un véhicule.
 * Elle peut être générée automatiquement depuis les opérations existantes
 * (carburant, maintenance, incident) ou saisie manuellement (amende, péage, autre).
 *
 * Cycle de vie des dépenses manuelles : PENDING → APPROVED / REJECTED
 * Les dépenses auto-générées naissent directement en APPROVED.
 */
public class Expense {

    // ── Enums du domaine ─────────────────────────────────────────────────────

    /** Source / type de la dépense */
    public enum ExpenseType {
        FUEL,           // Plein de carburant (généré depuis FuelRecharge)
        MAINTENANCE,    // Maintenance véhicule (généré depuis Maintenance)
        INCIDENT,       // Coût incident (généré depuis IncidentReport)
        FINE,           // Amende (saisie manuelle)
        TOLL,           // Péage (saisie manuelle)
        OTHER           // Autre frais divers (saisie manuelle)
    }

    /** Statut de validation */
    public enum ExpenseStatus {
        PENDING,    // En attente de validation (dépenses manuelles uniquement)
        APPROVED,   // Approuvée
        REJECTED    // Rejetée
    }

    /** Source de création */
    public enum SourceType {
        AUTO,   // Générée automatiquement depuis une opération existante
        MANUAL  // Saisie manuellement par le Manager ou le Driver
    }

    // ── Champs ───────────────────────────────────────────────────────────────

    private UUID id;

    /** Type de dépense (obligatoire) */
    private final ExpenseType expenseType;

    /** Montant en FCFA (obligatoire, > 0) */
    private BigDecimal amount;

    /** Description libre de la dépense (optionnelle pour les auto-générées) */
    private String description;

    /** Date et heure de la dépense */
    private final LocalDateTime expenseDate;

    /** Statut de validation */
    private ExpenseStatus status;

    /** Source de création */
    private final SourceType sourceType;

    /** ID de l'opération source (FuelRecharge, Maintenance ou Incident) — null si MANUAL */
    private UUID sourceId;

    /** Commentaire de rejet (renseigné lors d'un REJECTED) */
    private String rejectionReason;

    /** Date de validation/rejet */
    private LocalDateTime validatedAt;

    /** Manager ayant validé/rejeté */
    private UUID validatedBy;

    // ── Références croisées par ID uniquement ────────────────────────────────

    /** Véhicule concerné (obligatoire) */
    private final UUID vehicleId;

    /** Numéro d'immatriculation (dénormalisé pour l'affichage) */
    private final String vehicleRegistration;

    /** Flotte d'appartenance du véhicule */
    private final UUID fleetId;

    /** Manager propriétaire de la flotte */
    private final UUID managerId;

    /** Chauffeur impliqué (optionnel) */
    private UUID driverId;

    /** Nom complet du chauffeur (dénormalisé) */
    private String driverFullName;

    /** Date de création */
    private final LocalDateTime createdAt;

    // ─────────────────────────────────────────────────────────────────────────

    public Expense(UUID id,
                   ExpenseType expenseType,
                   BigDecimal amount,
                   String description,
                   LocalDateTime expenseDate,
                   SourceType sourceType,
                   UUID sourceId,
                   UUID vehicleId,
                   String vehicleRegistration,
                   UUID fleetId,
                   UUID managerId,
                   UUID driverId,
                   String driverFullName,
                   LocalDateTime createdAt) {

        if (expenseType == null) {
            throw new IllegalArgumentException("Le type de dépense est obligatoire.");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Le montant de la dépense doit être strictement positif.");
        }
        if (vehicleId == null) {
            throw new IllegalArgumentException("Le véhicule est obligatoire pour une dépense.");
        }
        if (managerId == null) {
            throw new IllegalArgumentException("Le manager est obligatoire pour une dépense.");
        }
        if (fleetId == null) {
            throw new IllegalArgumentException("La flotte est obligatoire pour une dépense.");
        }

        this.id = id;
        this.expenseType = expenseType;
        this.amount = amount;
        this.description = description;
        this.expenseDate = expenseDate != null ? expenseDate : LocalDateTime.now();
        this.sourceType = sourceType != null ? sourceType : SourceType.MANUAL;
        this.sourceId = sourceId;
        this.vehicleId = vehicleId;
        this.vehicleRegistration = vehicleRegistration;
        this.fleetId = fleetId;
        this.managerId = managerId;
        this.driverId = driverId;
        this.driverFullName = driverFullName;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();

        // Les dépenses auto-générées sont directement approuvées
        this.status = (this.sourceType == SourceType.AUTO)
                ? ExpenseStatus.APPROVED
                : ExpenseStatus.PENDING;
    }

    // ── Méthodes métier ───────────────────────────────────────────────────────

    /**
     * Approuve la dépense.
     * Interdit si déjà validée ou rejetée.
     */
    public void approve(UUID validatedBy) {
        if (this.status != ExpenseStatus.PENDING) {
            throw new IllegalStateException(
                    "Seule une dépense en attente peut être approuvée. Statut actuel : " + this.status);
        }
        this.status = ExpenseStatus.APPROVED;
        this.validatedBy = validatedBy;
        this.validatedAt = LocalDateTime.now();
    }

    /**
     * Rejette la dépense avec un motif obligatoire.
     */
    public void reject(UUID validatedBy, String reason) {
        if (this.status != ExpenseStatus.PENDING) {
            throw new IllegalStateException(
                    "Seule une dépense en attente peut être rejetée. Statut actuel : " + this.status);
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Le motif de rejet est obligatoire.");
        }
        this.status = ExpenseStatus.REJECTED;
        this.validatedBy = validatedBy;
        this.validatedAt = LocalDateTime.now();
        this.rejectionReason = reason;
    }

    /**
     * Met à jour le montant (autorisé uniquement sur les dépenses PENDING manuelles).
     */
    public void updateAmount(BigDecimal newAmount) {
        if (this.sourceType == SourceType.AUTO) {
            throw new IllegalStateException("Le montant d'une dépense auto-générée ne peut pas être modifié.");
        }
        if (newAmount == null || newAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Le montant doit être strictement positif.");
        }
        this.amount = newAmount;
    }

    /**
     * Indique si cette dépense est une dépense manuelle modifiable.
     */
    public boolean isManualAndPending() {
        return this.sourceType == SourceType.MANUAL && this.status == ExpenseStatus.PENDING;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public UUID getId()                        { return id; }
    public ExpenseType getExpenseType()        { return expenseType; }
    public BigDecimal getAmount()              { return amount; }
    public String getDescription()             { return description; }
    public LocalDateTime getExpenseDate()      { return expenseDate; }
    public ExpenseStatus getStatus()           { return status; }
    public SourceType getSourceType()          { return sourceType; }
    public UUID getSourceId()                  { return sourceId; }
    public String getRejectionReason()         { return rejectionReason; }
    public LocalDateTime getValidatedAt()      { return validatedAt; }
    public UUID getValidatedBy()               { return validatedBy; }
    public UUID getVehicleId()                 { return vehicleId; }
    public String getVehicleRegistration()     { return vehicleRegistration; }
    public UUID getFleetId()                   { return fleetId; }
    public UUID getManagerId()                 { return managerId; }
    public UUID getDriverId()                  { return driverId; }
    public String getDriverFullName()          { return driverFullName; }
    public LocalDateTime getCreatedAt()        { return createdAt; }

    // ── Setters limités ───────────────────────────────────────────────────────

    public void setId(UUID id)                             { this.id = id; }
    public void setDescription(String description)         { this.description = description; }
    public void setStatus(ExpenseStatus status)            { this.status = status; }
    public void setValidatedAt(LocalDateTime validatedAt)  { this.validatedAt = validatedAt; }
    public void setValidatedBy(UUID validatedBy)           { this.validatedBy = validatedBy; }
    public void setRejectionReason(String reason)          { this.rejectionReason = reason; }
    public void setDriverId(UUID driverId)                 { this.driverId = driverId; }
    public void setDriverFullName(String name)             { this.driverFullName = name; }
}
