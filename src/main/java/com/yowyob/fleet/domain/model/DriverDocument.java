package com.yowyob.fleet.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Entité de domaine : Document légal d'un conducteur.
 * Domaine pur — aucune dépendance Spring, JPA ou Jackson.
 *
 * Représente un document administratif attaché à un conducteur :
 * permis de conduire, visite médicale, carte professionnelle, contrat de travail.
 */
public class DriverDocument {

    public enum DocType {
        DRIVING_LICENSE,    // Permis de conduire
        MEDICAL_CERT,       // Certificat médical / Visite médicale
        PROFESSIONAL_CARD,  // Carte professionnelle
        WORK_CONTRACT,      // Contrat de travail
        ID_CARD,            // Carte nationale d'identité
        OTHER
    }

    public enum Status {
        VALID,
        EXPIRING_SOON,
        EXPIRED,
        PENDING_RENEWAL
    }

    // ── Champs ───────────────────────────────────────────────────────────────

    private UUID id;
    private final UUID driverId;
    private final DocType docType;
    private String docNumber;
    private String licenseCategories; // Ex: "B,C,D" pour le permis
    private String issuer;
    private LocalDate issueDate;
    private LocalDate expiryDate;     // Optionnel (ex: CNI sans expiration)
    private String fileUrl;
    private Status status;
    private String notes;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ─────────────────────────────────────────────────────────────────────────

    public DriverDocument(UUID id,
                          UUID driverId,
                          DocType docType,
                          String docNumber,
                          String licenseCategories,
                          String issuer,
                          LocalDate issueDate,
                          LocalDate expiryDate,
                          String fileUrl,
                          Status status,
                          String notes,
                          LocalDateTime createdAt,
                          LocalDateTime updatedAt) {

        if (driverId == null)
            throw new IllegalArgumentException("Le conducteur est obligatoire pour un document.");
        if (docType == null)
            throw new IllegalArgumentException("Le type de document est obligatoire.");

        this.id = id;
        this.driverId = driverId;
        this.docType = docType;
        this.docNumber = docNumber;
        this.licenseCategories = licenseCategories;
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

    public static Status computeStatus(LocalDate expiryDate) {
        if (expiryDate == null) return Status.VALID; // Pas de date = pas d'expiration
        LocalDate today = LocalDate.now();
        long days = ChronoUnit.DAYS.between(today, expiryDate);
        if (days < 0)   return Status.EXPIRED;
        if (days <= 30) return Status.EXPIRING_SOON;
        return Status.VALID;
    }

    public void refreshStatus() {
        if (this.status != Status.PENDING_RENEWAL) {
            this.status = computeStatus(this.expiryDate);
        }
        this.updatedAt = LocalDateTime.now();
    }

    public void markPendingRenewal() {
        this.status = Status.PENDING_RENEWAL;
        this.updatedAt = LocalDateTime.now();
    }

    public long daysUntilExpiry() {
        if (expiryDate == null) return Long.MAX_VALUE;
        return ChronoUnit.DAYS.between(LocalDate.now(), this.expiryDate);
    }

    public boolean isExpired()      { return this.status == Status.EXPIRED; }
    public boolean isExpiringSoon() { return this.status == Status.EXPIRING_SOON; }
    public boolean requiresAlert()  { return isExpired() || isExpiringSoon(); }

    // ── Getters ───────────────────────────────────────────────────────────────

    public UUID getId()                    { return id; }
    public UUID getDriverId()              { return driverId; }
    public DocType getDocType()            { return docType; }
    public String getDocNumber()           { return docNumber; }
    public String getLicenseCategories()   { return licenseCategories; }
    public String getIssuer()              { return issuer; }
    public LocalDate getIssueDate()        { return issueDate; }
    public LocalDate getExpiryDate()       { return expiryDate; }
    public String getFileUrl()             { return fileUrl; }
    public Status getStatus()              { return status; }
    public String getNotes()               { return notes; }
    public LocalDateTime getCreatedAt()    { return createdAt; }
    public LocalDateTime getUpdatedAt()    { return updatedAt; }

    public void setId(UUID id)             { this.id = id; }
    public void setFileUrl(String url)     { this.fileUrl = url; this.updatedAt = LocalDateTime.now(); }
    public void setStatus(Status s)        { this.status = s; this.updatedAt = LocalDateTime.now(); }
    public void setNotes(String notes)     { this.notes = notes; }
}
