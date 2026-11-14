package com.yowyob.fleet.domain.model;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Entité du domaine : Mission de transport / livraison.
 * Domaine pur — aucune dépendance Spring, JPA ou Jackson.
 *
 * <p>Une {@code Mission} constitue le lien opérationnel entre :
 * <ul>
 *   <li>un trajet <strong>Fleetman</strong> (véhicule + conducteur),</li>
 *   <li>une livraison <strong>TiiBnTick</strong> (service G1 externe), identifiée par
 *       {@link #tiiBnTickDeliveryId} et le QR code {@link #tiiBnTickParcelId},</li>
 *   <li>un objet suivi de <strong>Géofence</strong> (service G3 externe), identifié par
 *       {@link #geofenceObjectId}.</li>
 * </ul>
 *
 * <p>Machine à états :
 * <pre>
 *   PENDING ──start()──▶ ACTIVE ──complete()──▶ COMPLETED
 *      │                   │
 *      └────cancel()───────┘
 *                          ▼
 *                      CANCELLED
 * </pre>
 */
public class Mission {

    // ── Enums du domaine ─────────────────────────────────────────────────────

    /** Cycle de vie d'une mission. */
    public enum Status {
        /** Mission planifiée, en attente de démarrage. */
        PENDING,
        /** Mission en cours. */
        ACTIVE,
        /** Mission terminée avec succès. */
        COMPLETED,
        /** Mission annulée avant ou pendant son exécution. */
        CANCELLED
    }

    /** Nature de la mission. */
    public enum Type {
        /** Livraison d'un colis au destinataire. */
        DELIVERY,
        /** Collecte d'un colis chez l'expéditeur. */
        PICKUP,
        /** Transfert entre deux dépôts. */
        TRANSFER,
        /** Aller-retour sur le même point. */
        ROUND_TRIP
    }

    // ── Champs ───────────────────────────────────────────────────────────────

    /** Identifiant unique de la mission (UUID v4). */
    private final UUID id;

    /** Véhicule affecté à cette mission (obligatoire). */
    private final UUID vehicleId;

    /** Conducteur affecté à cette mission (obligatoire). */
    private final UUID driverId;

    /** Responsable flotte ayant planifié la mission. */
    private final UUID fleetManagerId;

    /**
     * Identifiant de la livraison correspondante dans le service TiiBnTick (G1).
     * Nullable : la mission peut exister avant l'assignation d'une livraison externe.
     */
    private UUID tiiBnTickDeliveryId;

    /**
     * QR code du colis dans le service TiiBnTick (G1).
     * Nullable.
     */
    private String tiiBnTickParcelId;

    /** Statut courant dans le cycle de vie. */
    private Status status;

    /** Type de mission. */
    private final Type type;

    /** Date/heure de départ planifiée (obligatoire). */
    private final LocalDateTime scheduledStart;

    /** Date/heure d'arrivée planifiée (nullable). */
    private final LocalDateTime scheduledEnd;

    /** Date/heure de départ effective (renseignée au démarrage réel). */
    private LocalDateTime actualStart;

    /** Date/heure de fin effective (renseignée à la clôture réelle). */
    private LocalDateTime actualEnd;

    /** Point de départ (référence vers une entité Location). */
    private final UUID startLocationId;

    /** Point d'arrivée (référence vers une entité Location). */
    private final UUID endLocationId;

    /**
     * Identifiant de l'objet suivi dans le service Géofence (G3).
     * Nullable : renseigné après la création si le suivi géographique est activé.
     */
    private String geofenceObjectId;

    /** Notes libres sur la mission. */
    private String notes;

    /** Raison de l'annulation (renseignée uniquement si {@code status == CANCELLED}). */
    private String cancelReason;

    /** Date/heure de création de la mission. */
    private final LocalDateTime createdAt;

    /** Tenant propriétaire de cette mission (architecture multi-tenant). */
    private final UUID tenantId;

    // ── Constructeur ─────────────────────────────────────────────────────────

    /**
     * Crée une nouvelle mission en statut {@link Status#PENDING}.
     *
     * @param vehicleId       identifiant du véhicule affecté (obligatoire)
     * @param driverId        identifiant du conducteur affecté (obligatoire)
     * @param fleetManagerId  identifiant du responsable flotte
     * @param type            type de mission
     * @param scheduledStart  date/heure de départ planifiée (obligatoire)
     * @param scheduledEnd    date/heure d'arrivée planifiée (nullable, doit être postérieure à {@code scheduledStart})
     * @param startLocationId identifiant du point de départ
     * @param endLocationId   identifiant du point d'arrivée
     * @param tenantId        identifiant du tenant
     * @throws IllegalArgumentException si {@code vehicleId}, {@code driverId} ou {@code scheduledStart} est null,
     *                                  ou si {@code scheduledEnd} n'est pas postérieur à {@code scheduledStart}
     */
    public Mission(UUID vehicleId,
                   UUID driverId,
                   UUID fleetManagerId,
                   Type type,
                   LocalDateTime scheduledStart,
                   LocalDateTime scheduledEnd,
                   UUID startLocationId,
                   UUID endLocationId,
                   UUID tenantId) {

        if (vehicleId == null) {
            throw new IllegalArgumentException("Le véhicule est obligatoire pour une mission.");
        }
        if (driverId == null) {
            throw new IllegalArgumentException("Le conducteur est obligatoire pour une mission.");
        }
        if (scheduledStart == null) {
            throw new IllegalArgumentException("La date de départ planifiée est obligatoire.");
        }
        if (scheduledEnd != null && !scheduledEnd.isAfter(scheduledStart)) {
            throw new IllegalArgumentException(
                    "La date de fin planifiée doit être postérieure à la date de début planifiée.");
        }

        this.id              = UUID.randomUUID();
        this.vehicleId       = vehicleId;
        this.driverId        = driverId;
        this.fleetManagerId  = fleetManagerId;
        this.type            = type;
        this.scheduledStart  = scheduledStart;
        this.scheduledEnd    = scheduledEnd;
        this.startLocationId = startLocationId;
        this.endLocationId   = endLocationId;
        this.tenantId        = tenantId;
        this.status          = Status.PENDING;
        this.createdAt       = LocalDateTime.now();
    }

    /**
     * Constructeur complet pour reconstitution depuis la persistance (infrastructure).
     */
    public Mission(UUID id,
                   UUID vehicleId,
                   UUID driverId,
                   UUID fleetManagerId,
                   UUID tiiBnTickDeliveryId,
                   String tiiBnTickParcelId,
                   Status status,
                   Type type,
                   LocalDateTime scheduledStart,
                   LocalDateTime scheduledEnd,
                   LocalDateTime actualStart,
                   LocalDateTime actualEnd,
                   UUID startLocationId,
                   UUID endLocationId,
                   String geofenceObjectId,
                   String notes,
                   String cancelReason,
                   LocalDateTime createdAt,
                   UUID tenantId) {
        this.id = id;
        this.vehicleId = vehicleId;
        this.driverId = driverId;
        this.fleetManagerId = fleetManagerId;
        this.tiiBnTickDeliveryId = tiiBnTickDeliveryId;
        this.tiiBnTickParcelId = tiiBnTickParcelId;
        this.status = status;
        this.type = type;
        this.scheduledStart = scheduledStart;
        this.scheduledEnd = scheduledEnd;
        this.actualStart = actualStart;
        this.actualEnd = actualEnd;
        this.startLocationId = startLocationId;
        this.endLocationId = endLocationId;
        this.geofenceObjectId = geofenceObjectId;
        this.notes = notes;
        this.cancelReason = cancelReason;
        this.createdAt = createdAt;
        this.tenantId = tenantId;
    }

    // ── Méthodes métier (machine à états) ────────────────────────────────────

    /**
     * Démarre la mission : {@code PENDING → ACTIVE}.
     * Enregistre l'heure de départ effective.
     *
     * @throws IllegalStateException si la mission n'est pas en statut {@link Status#PENDING}
     */
    public void start() {
        if (this.status != Status.PENDING) {
            throw new IllegalStateException(
                    "Impossible de démarrer la mission : statut actuel = " + this.status
                    + " (attendu : PENDING).");
        }
        this.status      = Status.ACTIVE;
        this.actualStart = LocalDateTime.now();
    }

    /**
     * Clôture la mission avec succès : {@code ACTIVE → COMPLETED}.
     * Enregistre l'heure de fin effective.
     *
     * @throws IllegalStateException si la mission n'est pas en statut {@link Status#ACTIVE}
     */
    public void complete() {
        if (this.status != Status.ACTIVE) {
            throw new IllegalStateException(
                    "Impossible de terminer la mission : statut actuel = " + this.status
                    + " (attendu : ACTIVE).");
        }
        this.status    = Status.COMPLETED;
        this.actualEnd = LocalDateTime.now();
    }

    /**
     * Annule la mission : {@code PENDING | ACTIVE → CANCELLED}.
     * Enregistre la raison de l'annulation.
     *
     * @param reason motif de l'annulation (peut être null)
     * @throws IllegalStateException si la mission est déjà {@link Status#COMPLETED}
     */
    public void cancel(String reason) {
        if (this.status == Status.COMPLETED) {
            throw new IllegalStateException(
                    "Impossible d'annuler une mission déjà terminée (COMPLETED).");
        }
        this.status       = Status.CANCELLED;
        this.cancelReason = reason;
    }

    /**
     * Associe cette mission à une livraison TiiBnTick (service G1 externe).
     *
     * @param deliveryId identifiant de la livraison chez TiiBnTick
     * @param parcelId   QR code du colis (peut être null)
     * @throws IllegalStateException si la mission est {@link Status#COMPLETED} ou {@link Status#CANCELLED}
     */
    public void linkToDelivery(UUID deliveryId, String parcelId) {
        if (this.status == Status.COMPLETED || this.status == Status.CANCELLED) {
            throw new IllegalStateException(
                    "Impossible de lier une livraison TiiBnTick : la mission est " + this.status + ".");
        }
        this.tiiBnTickDeliveryId = deliveryId;
        this.tiiBnTickParcelId   = parcelId;
    }

    /**
     * Associe cette mission à un objet suivi dans le service Géofence (G3 externe).
     *
     * @param objectId identifiant de l'objet géofence
     */
    public void linkToGeofenceObject(String objectId) {
        this.geofenceObjectId = objectId;
    }

    /**
     * Indique si la mission est actuellement en cours.
     *
     * @return {@code true} si le statut est {@link Status#ACTIVE}
     */
    public boolean isActive() {
        return this.status == Status.ACTIVE;
    }

    /**
     * Indique si la mission est terminée avec succès.
     *
     * @return {@code true} si le statut est {@link Status#COMPLETED}
     */
    public boolean isCompleted() {
        return this.status == Status.COMPLETED;
    }

    /**
     * Calcule la durée effective de la mission en minutes.
     *
     * @return la durée en minutes entre {@code actualStart} et {@code actualEnd},
     *         ou {@code null} si l'une ou l'autre n'est pas renseignée
     */
    public Long getDurationMinutes() {
        if (this.actualStart == null || this.actualEnd == null) {
            return null;
        }
        return ChronoUnit.MINUTES.between(this.actualStart, this.actualEnd);
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public UUID getId()                       { return id; }
    public UUID getVehicleId()                { return vehicleId; }
    public UUID getDriverId()                 { return driverId; }
    public UUID getFleetManagerId()           { return fleetManagerId; }
    public UUID getTiiBnTickDeliveryId()      { return tiiBnTickDeliveryId; }
    public String getTiiBnTickParcelId()      { return tiiBnTickParcelId; }
    public Status getStatus()                 { return status; }
    public Type getType()                     { return type; }
    public LocalDateTime getScheduledStart()  { return scheduledStart; }
    public LocalDateTime getScheduledEnd()    { return scheduledEnd; }
    public LocalDateTime getActualStart()     { return actualStart; }
    public LocalDateTime getActualEnd()       { return actualEnd; }
    public UUID getStartLocationId()          { return startLocationId; }
    public UUID getEndLocationId()            { return endLocationId; }
    public String getGeofenceObjectId()       { return geofenceObjectId; }
    public String getNotes()                  { return notes; }
    public String getCancelReason()           { return cancelReason; }
    public LocalDateTime getCreatedAt()       { return createdAt; }
    public UUID getTenantId()                 { return tenantId; }
}
