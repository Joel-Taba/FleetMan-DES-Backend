package com.yowyob.fleet.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Entité de domaine : Document légal d'un véhicule.
 * Domaine pur — aucune dépendance Spring, JPA ou Jackson.
 *
 * Représente un document administratif obligatoire attaché à un véhicule :
 * assurance, carte grise, visite technique, vignette, autorisation de transport.
 */
public class VehicleDocument {

    public enum DocType {
        INSURANCE,          // Assurance
        REGISTRATION,       // Carte grise
        TECHNICAL_CONTROL,  // Visite technique
        TAX_STICKER,        // Vignette
        TRANSPORT_PERMIT,   // Autorisation de transport
        OTHER
    }

    public enum Status {
        VALID,            // Valide
        EXPIRING_SOON,    // Expire dans moins de 30 jours
        EXPIRED,          // Expiré
        PENDING_RENEWAL   // En cours de renouvellement
    }

    // ── Champs ───────────────────────────────────────────────────────────────

    private UUID id;
    private final UUID vehicleId;
    private final DocType docType;
    private String docNumber;
    private String issuer;
    private LocalDate issueDate;
    private final LocalDate expiryDate;
    private String fileUrl;
    private Status status;
    private String notes;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ─────────────────────────────────────────────────────────────────────────

    public VehicleDocument(UUID id,
                           UUID vehicleId,
                           DocType docType,
                           String docNumber,
                           String issuer,
                           LocalDate issueDate,
                           LocalDate expiryDate,
                           String fileUrl,
                           Status status,
                           String notes,
                           LocalDateTime createdAt,
                           LocalDateTime updatedAt) {

        if (vehicleId == null)
            throw new IllegalArgumentException("Le véhicule est obligatoire pour un document.");
        if (docType == null)
            throw new IllegalArgumentException("Le type de document est obligatoire.");
        if (expiryDate == null)
            throw new IllegalArgumentException("La date d'expiration est obligatoire.");

        this.id = id;
        this.vehicleId = vehicleId;
        this.docType = docType;
        this.docNumber = docNumber;
        this.issuer = issuer;
        this.issueDate = issueDate;
        this.expiryDate = expiryDate;
        this.fileUrl = fileUrl;
        this.status = status != null ? status : computeStatus(expiryDate);
        this.notes = notes;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        this.updatedAt = updatedAt != null ? updatedAt : LocalDateTime.now();
    }

    // ── Méthodes métier ───────────────────────────────────────────────────────

    /**
     * Calcule le statut en fonction de la date d'expiration et de la date du jour.
     */
    public static Status computeStatus(LocalDate expiryDate) {
        if (expiryDate == null) return Status.VALID;
        LocalDate today = LocalDate.now();
        long daysUntilExpiry = ChronoUnit.DAYS.between(today, expiryDate);
        if (daysUntilExpiry < 0)  return Status.EXPIRED;
        if (daysUntilExpiry <= 30) return Status.EXPIRING_SOON;
        return Status.VALID;
    }

    /** Rafraîchit le statut calculé (appelé par le job nocturne). */
    public void refreshStatus() {
        if (this.status != Status.PENDING_RENEWAL) {
            this.status = computeStatus(this.expiryDate);
        }
        this.updatedAt = LocalDateTime.now();
    }

    /** Marque le document comme en cours de renouvellement. */
    public void markPendingRenewal() {
        this.status = Status.PENDING_RENEWAL;
        this.updatedAt = LocalDateTime.now();
    }

    /** Retourne le nombre de jours avant expiration (négatif si expiré). */
    public long daysUntilExpiry() {
        return ChronoUnit.DAYS.between(LocalDate.now(), this.expiryDate);
    }

    public boolean isExpired()       { return this.status == Status.EXPIRED; }
    public boolean isExpiringSoon()  { return this.status == Status.EXPIRING_SOON; }
    public boolean requiresAlert()   { return isExpired() || isExpiringSoon(); }

    // ── Getters ───────────────────────────────────────────────────────────────

    public UUID getId()              { return id; }
    public UUID getVehicleId()       { return vehicleId; }
    public DocType getDocType()      { return docType; }
    public String getDocNumber()     { return docNumber; }
    public String getIssuer()        { return issuer; }
    public LocalDate getIssueDate()  { return issueDate; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public String getFileUrl()       { return fileUrl; }
    public Status getStatus()        { return status; }
    public String getNotes()         { return notes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setId(UUID id)           { this.id = id; }
    public void setFileUrl(String url)   { this.fileUrl = url; this.updatedAt = LocalDateTime.now(); }
    public void setStatus(Status status) { this.status = status; this.updatedAt = LocalDateTime.now(); }
    public void setNotes(String notes)   { this.notes = notes; }
}
