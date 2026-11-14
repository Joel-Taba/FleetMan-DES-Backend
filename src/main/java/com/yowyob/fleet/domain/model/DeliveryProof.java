package com.yowyob.fleet.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entité du domaine : Preuve de livraison.
 * Domaine pur — aucune dépendance Spring, JPA ou Jackson.
 *
 * <p>Représente la preuve de livraison déposée par le conducteur à l'issue
 * d'une mission. La preuve peut prendre plusieurs formes (photo, signature,
 * scan QR, confirmation manuelle) et son intégrité est vérifiée via un hash
 * HMAC-SHA256 calculé après l'upload du fichier associé.</p>
 *
 * <p>Cycle de vie du statut :
 * <pre>
 *   PENDING_REVIEW ──► ACCEPTED
 *                  ──► REJECTED
 *                  ──► TAMPERED  (à tout moment si l'intégrité est compromise)
 * </pre>
 * </p>
 *
 * @see DeliveryStatus
 */
public class DeliveryProof {

    // ── Enums du domaine ─────────────────────────────────────────────────────

    /** Type de preuve fournie par le conducteur. */
    public enum ProofType {
        /** Photographie du colis / destinataire. */
        PHOTO,
        /** Signature manuscrite numérisée du destinataire. */
        SIGNATURE,
        /** Scan d'un QR code sur le colis ou le bon de livraison. */
        QR_SCAN,
        /** Confirmation manuelle saisie par le conducteur. */
        MANUAL_CONFIRMATION
    }

    /** Statut de révision de la preuve. */
    public enum ProofStatus {
        /** En attente de révision par un opérateur. */
        PENDING_REVIEW,
        /** Preuve acceptée comme valide. */
        ACCEPTED,
        /** Preuve rejetée (non conforme, illisible, etc.). */
        REJECTED,
        /** Preuve dont l'intégrité a été compromise (hash invalide). */
        TAMPERED
    }

    // ── Champs ───────────────────────────────────────────────────────────────

    /** Identifiant unique de la preuve (UUID v4). */
    private final UUID id;

    /** Identifiant de la mission associée (obligatoire). */
    private final UUID missionId;

    /** Identifiant du conducteur ayant déposé la preuve (obligatoire). */
    private final UUID driverId;

    /** Type de preuve. */
    private final ProofType type;

    /** Statut courant dans le cycle de révision. */
    private ProofStatus status;

    /** Identifiant du fichier dans le service File Yowyob (nullable). */
    private String fileId;

    /** URL d'accès public au fichier (nullable). */
    private String fileUrl;

    /**
     * Hash HMAC-SHA256 du contenu du fichier, calculé après upload (nullable).
     * Permet de détecter toute altération ultérieure du fichier.
     */
    private String integrityHash;

    /** Nom du destinataire ayant réceptionné la livraison (nullable). */
    private String recipientName;

    /** Signature du destinataire encodée en base64 (nullable). */
    private String recipientSignature;

    /** Latitude GPS au moment du dépôt de la preuve (nullable). */
    private Double latitude;

    /** Longitude GPS au moment du dépôt de la preuve (nullable). */
    private Double longitude;

    /** Notes libres du conducteur ou motif de rejet (nullable). */
    private String notes;

    /** Horodatage de soumission de la preuve. */
    private final LocalDateTime submittedAt;

    /** Horodatage de la révision (nullable tant que non révisée). */
    private LocalDateTime reviewedAt;

    /** Identifiant de l'opérateur ayant révisé la preuve (nullable). */
    private UUID reviewedBy;

    /** Identifiant du tenant (obligatoire). */
    private final UUID tenantId;

    /** Horodatage de création de l'entité. */
    private final LocalDateTime createdAt;

    // ── Constructeur ─────────────────────────────────────────────────────────

    /**
     * Crée une nouvelle preuve de livraison en attente de révision.
     *
     * @param missionId identifiant de la mission (non null)
     * @param driverId  identifiant du conducteur (non null)
     * @param type      type de preuve (non null)
     * @param tenantId  identifiant du tenant (non null)
     * @throws IllegalArgumentException si {@code missionId} ou {@code driverId} est null
     */
    public DeliveryProof(UUID missionId, UUID driverId, ProofType type, UUID tenantId) {
        if (missionId == null) {
            throw new IllegalArgumentException("missionId est obligatoire pour une preuve de livraison.");
        }
        if (driverId == null) {
            throw new IllegalArgumentException("driverId est obligatoire pour une preuve de livraison.");
        }

        this.id          = UUID.randomUUID();
        this.missionId   = missionId;
        this.driverId    = driverId;
        this.type        = type;
        this.tenantId    = tenantId;
        this.status      = ProofStatus.PENDING_REVIEW;
        this.createdAt   = LocalDateTime.now();
        this.submittedAt = this.createdAt;
    }

    /**
     * Constructeur complet pour reconstitution depuis la persistance (infrastructure).
     */
    public DeliveryProof(UUID id,
                         UUID missionId,
                         UUID driverId,
                         ProofType type,
                         ProofStatus status,
                         String fileId,
                         String fileUrl,
                         String integrityHash,
                         String recipientName,
                         String recipientSignature,
                         Double latitude,
                         Double longitude,
                         String notes,
                         LocalDateTime submittedAt,
                         LocalDateTime reviewedAt,
                         UUID reviewedBy,
                         UUID tenantId,
                         LocalDateTime createdAt) {
        this.id = id;
        this.missionId = missionId;
        this.driverId = driverId;
        this.type = type;
        this.status = status;
        this.fileId = fileId;
        this.fileUrl = fileUrl;
        this.integrityHash = integrityHash;
        this.recipientName = recipientName;
        this.recipientSignature = recipientSignature;
        this.latitude = latitude;
        this.longitude = longitude;
        this.notes = notes;
        this.submittedAt = submittedAt;
        this.reviewedAt = reviewedAt;
        this.reviewedBy = reviewedBy;
        this.tenantId = tenantId;
        this.createdAt = createdAt;
    }

    // ── Méthodes métier ───────────────────────────────────────────────────────

    /**
     * Accepte la preuve de livraison.
     *
     * @param reviewerId identifiant de l'opérateur qui accepte
     * @throws IllegalStateException si la preuve a déjà été traitée (non PENDING_REVIEW)
     */
    public void accept(UUID reviewerId) {
        if (this.status != ProofStatus.PENDING_REVIEW) {
            throw new IllegalStateException(
                "La preuve ne peut être acceptée que depuis l'état PENDING_REVIEW. État actuel : " + this.status);
        }
        this.status     = ProofStatus.ACCEPTED;
        this.reviewedAt = LocalDateTime.now();
        this.reviewedBy = reviewerId;
    }

    /**
     * Rejette la preuve de livraison.
     *
     * @param reviewerId identifiant de l'opérateur qui rejette
     * @param reason     motif du rejet (stocké dans {@code notes})
     * @throws IllegalStateException si la preuve a déjà été traitée (non PENDING_REVIEW)
     */
    public void reject(UUID reviewerId, String reason) {
        if (this.status != ProofStatus.PENDING_REVIEW) {
            throw new IllegalStateException(
                "La preuve ne peut être rejetée que depuis l'état PENDING_REVIEW. État actuel : " + this.status);
        }
        this.status     = ProofStatus.REJECTED;
        this.reviewedAt = LocalDateTime.now();
        this.reviewedBy = reviewerId;
        this.notes      = reason;
    }

    /**
     * Marque la preuve comme altérée (intégrité compromise).
     *
     * @throws IllegalStateException si la preuve est déjà marquée TAMPERED
     */
    public void markAsTampered() {
        if (this.status == ProofStatus.TAMPERED) {
            throw new IllegalStateException("La preuve est déjà marquée comme altérée (TAMPERED).");
        }
        this.status = ProofStatus.TAMPERED;
    }

    /**
     * Vérifie l'intégrité du fichier en comparant le hash calculé au hash stocké.
     *
     * @param computedHash hash HMAC-SHA256 recalculé par l'appelant
     * @return {@code true} si les hashes correspondent, {@code false} sinon
     * @throws IllegalStateException si {@code integrityHash} n'a pas encore été défini
     */
    public boolean verifyIntegrity(String computedHash) {
        if (this.integrityHash == null) {
            throw new IllegalStateException(
                "Impossible de vérifier l'intégrité : le hash n'a pas encore été défini.");
        }
        return this.integrityHash.equals(computedHash);
    }

    /**
     * Définit le hash HMAC-SHA256 de la preuve. À appeler une seule fois, après l'upload du fichier.
     *
     * @param hash hash HMAC-SHA256 du contenu du fichier
     */
    public void setIntegrityHash(String hash) {
        this.integrityHash = hash;
    }

    /**
     * Renseigne les informations du fichier uploadé dans le service File Yowyob.
     *
     * @param fileId  identifiant du fichier dans le service
     * @param fileUrl URL d'accès public au fichier
     */
    public void setFileInfo(String fileId, String fileUrl) {
        this.fileId  = fileId;
        this.fileUrl = fileUrl;
    }

    /**
     * Renseigne la position GPS au moment du dépôt de la preuve.
     *
     * @param latitude  latitude en degrés décimaux
     * @param longitude longitude en degrés décimaux
     */
    public void setLocation(Double latitude, Double longitude) {
        this.latitude  = latitude;
        this.longitude = longitude;
    }

    /**
     * Renseigne les informations du destinataire.
     *
     * @param name      nom du destinataire
     * @param signature signature encodée en base64
     */
    public void setRecipientInfo(String name, String signature) {
        this.recipientName      = name;
        this.recipientSignature = signature;
    }

    /**
     * Indique si la preuve a été acceptée.
     *
     * @return {@code true} si le statut est {@link ProofStatus#ACCEPTED}
     */
    public boolean isAccepted() {
        return this.status == ProofStatus.ACCEPTED;
    }

    /**
     * Indique si une position GPS a été enregistrée.
     *
     * @return {@code true} si latitude et longitude sont toutes deux non null
     */
    public boolean hasGpsLocation() {
        return this.latitude != null && this.longitude != null;
    }

    /**
     * Retourne un résumé lisible de la position GPS.
     *
     * @return chaîne de la forme {@code "lat=X, lng=Y"}, ou {@code null} si absente
     */
    public String getLocationSummary() {
        if (!hasGpsLocation()) {
            return null;
        }
        return "lat=" + this.latitude + ", lng=" + this.longitude;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public UUID getId()                      { return id; }
    public UUID getMissionId()               { return missionId; }
    public UUID getDriverId()                { return driverId; }
    public ProofType getType()               { return type; }
    public ProofStatus getStatus()           { return status; }
    public String getFileId()                { return fileId; }
    public String getFileUrl()               { return fileUrl; }
    public String getIntegrityHash()         { return integrityHash; }
    public String getRecipientName()         { return recipientName; }
    public String getRecipientSignature()    { return recipientSignature; }
    public Double getLatitude()              { return latitude; }
    public Double getLongitude()             { return longitude; }
    public String getNotes()                 { return notes; }
    public LocalDateTime getSubmittedAt()    { return submittedAt; }
    public LocalDateTime getReviewedAt()     { return reviewedAt; }
    public UUID getReviewedBy()              { return reviewedBy; }
    public UUID getTenantId()                { return tenantId; }
    public LocalDateTime getCreatedAt()      { return createdAt; }
}
