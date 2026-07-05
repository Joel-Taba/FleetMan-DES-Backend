package com.yowyob.fleet.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entité de domaine : Affectation véhicule-conducteur.
 * Domaine pur — aucune dépendance Spring, JPA ou Jackson.
 *
 * Une affectation associe un conducteur à un véhicule pour une plage horaire
 * précise. Elle peut être liée à un planning et/ou à une mission.
 *
 * Cycle de vie : PENDING → IN_PROGRESS → COMPLETED
 *                        ↘ CANCELLED
 *                        ↘ NO_SHOW
 */
public class Assignment {

    public enum Status {
        PENDING,        // En attente de démarrage
        IN_PROGRESS,    // En cours
        COMPLETED,      // Terminée avec succès
        CANCELLED,      // Annulée avant démarrage
        NO_SHOW         // Conducteur absent
    }

    // ── Champs ───────────────────────────────────────────────────────────────

    private UUID id;
    private UUID scheduleId;        // Optionnel — lien vers le planning
    private final UUID fleetId;
    private final UUID vehicleId;
    private final UUID driverId;
    private UUID missionId;         // Optionnel — lien vers une mission
    private final LocalDateTime startDatetime;
    private final LocalDateTime endDatetime;
    private Status status;
    private String startLocation;
    private String endLocation;
    private BigDecimal estimatedKm;
    private BigDecimal actualKm;
    private String notes;
    private final LocalDateTime createdAt;

    // ─────────────────────────────────────────────────────────────────────────

    public Assignment(UUID id,
                      UUID scheduleId,
                      UUID fleetId,
                      UUID vehicleId,
                      UUID driverId,
                      UUID missionId,
                      LocalDateTime startDatetime,
                      LocalDateTime endDatetime,
                      Status status,
                      String startLocation,
                      String endLocation,
                      BigDecimal estimatedKm,
                      BigDecimal actualKm,
                      String notes,
                      LocalDateTime createdAt) {

        if (fleetId == null)
            throw new IllegalArgumentException("La flotte est obligatoire pour une affectation.");
        if (vehicleId == null)
            throw new IllegalArgumentException("Le véhicule est obligatoire pour une affectation.");
        if (driverId == null)
            throw new IllegalArgumentException("Le conducteur est obligatoire pour une affectation.");
        if (startDatetime == null || endDatetime == null)
            throw new IllegalArgumentException("Les dates de début et de fin sont obligatoires.");
        if (!endDatetime.isAfter(startDatetime))
            throw new IllegalArgumentException("La date de fin doit être postérieure à la date de début.");

        this.id = id;
        this.scheduleId = scheduleId;
        this.fleetId = fleetId;
        this.vehicleId = vehicleId;
        this.driverId = driverId;
        this.missionId = missionId;
        this.startDatetime = startDatetime;
        this.endDatetime = endDatetime;
        this.status = status != null ? status : Status.PENDING;
        this.startLocation = startLocation;
        this.endLocation = endLocation;
        this.estimatedKm = estimatedKm;
        this.actualKm = actualKm;
        this.notes = notes;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }

    // ── Méthodes métier ───────────────────────────────────────────────────────

    /** Démarre l'affectation. */
    public void start() {
        if (this.status != Status.PENDING)
            throw new IllegalStateException("Seule une affectation en attente peut être démarrée.");
        this.status = Status.IN_PROGRESS;
    }

    /** Termine l'affectation avec le kilométrage réel. */
    public void complete(BigDecimal actualKm) {
        if (this.status != Status.IN_PROGRESS)
            throw new IllegalStateException("Seule une affectation en cours peut être terminée.");
        this.status = Status.COMPLETED;
        this.actualKm = actualKm;
    }

    /** Annule l'affectation. */
    public void cancel(String reason) {
        if (this.status == Status.COMPLETED)
            throw new IllegalStateException("Une affectation terminée ne peut pas être annulée.");
        this.status = Status.CANCELLED;
        this.notes = reason;
    }

    /** Marque le conducteur comme absent. */
    public void markNoShow() {
        if (this.status != Status.PENDING)
            throw new IllegalStateException("Seule une affectation en attente peut être marquée NO_SHOW.");
        this.status = Status.NO_SHOW;
    }

    /**
     * Vérifie si cette affectation chevauche une plage horaire donnée
     * pour le même véhicule ou conducteur.
     * Utilisé pour la détection de conflits.
     */
    public boolean overlapsWith(LocalDateTime otherStart, LocalDateTime otherEnd) {
        return this.startDatetime.isBefore(otherEnd)
                && this.endDatetime.isAfter(otherStart);
    }

    public boolean isActive() {
        return this.status == Status.PENDING || this.status == Status.IN_PROGRESS;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public UUID getId()                     { return id; }
    public UUID getScheduleId()             { return scheduleId; }
    public UUID getFleetId()                { return fleetId; }
    public UUID getVehicleId()              { return vehicleId; }
    public UUID getDriverId()               { return driverId; }
    public UUID getMissionId()              { return missionId; }
    public LocalDateTime getStartDatetime() { return startDatetime; }
    public LocalDateTime getEndDatetime()   { return endDatetime; }
    public Status getStatus()               { return status; }
    public String getStartLocation()        { return startLocation; }
    public String getEndLocation()          { return endLocation; }
    public BigDecimal getEstimatedKm()      { return estimatedKm; }
    public BigDecimal getActualKm()         { return actualKm; }
    public String getNotes()                { return notes; }
    public LocalDateTime getCreatedAt()     { return createdAt; }

    public void setId(UUID id)              { this.id = id; }
    public void setScheduleId(UUID sid)     { this.scheduleId = sid; }
    public void setMissionId(UUID mid)      { this.missionId = mid; }
    public void setNotes(String notes)      { this.notes = notes; }
}
