package com.yowyob.fleet.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entité du domaine : Alerte de maintenance préventive.
 * Domaine pur — aucune dépendance Spring, JPA ou Jackson.
 *
 * Une alerte est générée pour un véhicule spécifique quand il approche
 * ou dépasse le seuil défini dans un plan de maintenance.
 *
 * Cycle de vie : UPCOMING → DUE → OVERDUE → RESOLVED
 *                             ↘ RESOLVED (si maintenance effectuée avant OVERDUE)
 */
public class MaintenanceAlert {

    // ── Enums du domaine ─────────────────────────────────────────────────────

    /** Niveau d'urgence de l'alerte */
    public enum AlertStatus {
        UPCOMING,   // Seuil approche (dans la zone de préalerte)
        DUE,        // Seuil atteint — maintenance à effectuer
        OVERDUE,    // Seuil dépassé — maintenance en retard
        RESOLVED    // Maintenance effectuée, alerte clôturée
    }

    /** Cause du déclenchement */
    public enum TriggerType {
        MILEAGE,    // Déclenché par le kilométrage
        DATE,       // Déclenché par la date
        BOTH        // Les deux seuils dépassés simultanément
    }

    // ── Champs ───────────────────────────────────────────────────────────────

    private UUID id;

    /** Plan de maintenance source */
    private final UUID planId;

    /** Type de maintenance */
    private final MaintenancePlan.MaintenanceType maintenanceType;

    /** Véhicule concerné */
    private final UUID vehicleId;

    /** Numéro d'immatriculation (dénormalisé) */
    private final String vehicleRegistration;

    /** Flotte d'appartenance */
    private final UUID fleetId;

    /** Manager responsable */
    private final UUID managerId;

    /** Statut courant */
    private AlertStatus status;

    /** Cause du déclenchement */
    private final TriggerType triggerType;

    // ── Données kilométriques ─────────────────────────────────────────────────

    /** Kilométrage au moment du dernier entretien */
    private final Float lastMaintenanceKm;

    /** Kilométrage cible pour la prochaine maintenance */
    private final Float targetKm;

    /** Kilométrage actuel du véhicule */
    private Float currentKm;

    /** Km restants avant d'atteindre le seuil (négatif si dépassé) */
    private Float kmRemaining;

    // ── Données temporelles ───────────────────────────────────────────────────

    /** Date du dernier entretien */
    private final LocalDate lastMaintenanceDate;

    /** Date cible pour la prochaine maintenance */
    private final LocalDate targetDate;

    /** Jours restants avant la date cible (négatif si dépassé) */
    private Integer daysRemaining;

    // ── Résolution ────────────────────────────────────────────────────────────

    /** ID de la Maintenance créée lors de la résolution */
    private UUID resolvedByMaintenanceId;

    /** Date de résolution */
    private LocalDateTime resolvedAt;

    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ─────────────────────────────────────────────────────────────────────────

    public MaintenanceAlert(UUID id,
                            UUID planId,
                            MaintenancePlan.MaintenanceType maintenanceType,
                            UUID vehicleId,
                            String vehicleRegistration,
                            UUID fleetId,
                            UUID managerId,
                            AlertStatus status,
                            TriggerType triggerType,
                            Float lastMaintenanceKm,
                            Float targetKm,
                            Float currentKm,
                            Float kmRemaining,
                            LocalDate lastMaintenanceDate,
                            LocalDate targetDate,
                            Integer daysRemaining,
                            UUID resolvedByMaintenanceId,
                            LocalDateTime resolvedAt,
                            LocalDateTime createdAt,
                            LocalDateTime updatedAt) {

        if (vehicleId == null)
            throw new IllegalArgumentException("Le véhicule est obligatoire pour une alerte.");
        if (planId == null)
            throw new IllegalArgumentException("Le plan de maintenance est obligatoire.");

        this.id = id;
        this.planId = planId;
        this.maintenanceType = maintenanceType;
        this.vehicleId = vehicleId;
        this.vehicleRegistration = vehicleRegistration;
        this.fleetId = fleetId;
        this.managerId = managerId;
        this.status = status != null ? status : AlertStatus.UPCOMING;
        this.triggerType = triggerType != null ? triggerType : TriggerType.DATE;
        this.lastMaintenanceKm = lastMaintenanceKm;
        this.targetKm = targetKm;
        this.currentKm = currentKm;
        this.kmRemaining = kmRemaining;
        this.lastMaintenanceDate = lastMaintenanceDate;
        this.targetDate = targetDate;
        this.daysRemaining = daysRemaining;
        this.resolvedByMaintenanceId = resolvedByMaintenanceId;
        this.resolvedAt = resolvedAt;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        this.updatedAt = updatedAt != null ? updatedAt : LocalDateTime.now();
    }

    // ── Méthodes métier ───────────────────────────────────────────────────────

    /**
     * Met à jour le statut en fonction du kilométrage et de la date actuels.
     * Retourne true si le statut a changé (nécessite une sauvegarde).
     */
    public boolean refreshStatus(Float currentKmValue, LocalDate today) {
        if (this.status == AlertStatus.RESOLVED) return false;

        AlertStatus previous = this.status;
        this.currentKm = currentKmValue;

        boolean kmOverdue  = targetKm != null && currentKmValue != null && currentKmValue >= targetKm;
        boolean dateOverdue = targetDate != null && today != null && !today.isBefore(targetDate);
        boolean kmDue      = targetKm != null && currentKmValue != null
                && (targetKm - currentKmValue) <= 0;
        boolean dateDue    = targetDate != null && today != null && !today.isBefore(targetDate);

        if (kmOverdue || dateOverdue) {
            this.status = AlertStatus.OVERDUE;
        } else if (kmDue || dateDue) {
            this.status = AlertStatus.DUE;
        } else {
            this.status = AlertStatus.UPCOMING;
        }

        // Mise à jour des valeurs restantes
        if (targetKm != null && currentKmValue != null) {
            this.kmRemaining = targetKm - currentKmValue;
        }
        if (targetDate != null && today != null) {
            this.daysRemaining = (int) today.until(targetDate, java.time.temporal.ChronoUnit.DAYS);
        }

        this.updatedAt = LocalDateTime.now();
        return !this.status.equals(previous);
    }

    /**
     * Résout l'alerte (maintenance effectuée).
     */
    public void resolve(UUID maintenanceId) {
        this.status = AlertStatus.RESOLVED;
        this.resolvedByMaintenanceId = maintenanceId;
        this.resolvedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /** Indique si l'alerte nécessite une action urgente. */
    public boolean isUrgent() {
        return this.status == AlertStatus.DUE || this.status == AlertStatus.OVERDUE;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public UUID getId()                                    { return id; }
    public UUID getPlanId()                                { return planId; }
    public MaintenancePlan.MaintenanceType getMaintenanceType() { return maintenanceType; }
    public UUID getVehicleId()                             { return vehicleId; }
    public String getVehicleRegistration()                 { return vehicleRegistration; }
    public UUID getFleetId()                               { return fleetId; }
    public UUID getManagerId()                             { return managerId; }
    public AlertStatus getStatus()                         { return status; }
    public TriggerType getTriggerType()                    { return triggerType; }
    public Float getLastMaintenanceKm()                    { return lastMaintenanceKm; }
    public Float getTargetKm()                             { return targetKm; }
    public Float getCurrentKm()                            { return currentKm; }
    public Float getKmRemaining()                          { return kmRemaining; }
    public LocalDate getLastMaintenanceDate()              { return lastMaintenanceDate; }
    public LocalDate getTargetDate()                       { return targetDate; }
    public Integer getDaysRemaining()                      { return daysRemaining; }
    public UUID getResolvedByMaintenanceId()               { return resolvedByMaintenanceId; }
    public LocalDateTime getResolvedAt()                   { return resolvedAt; }
    public LocalDateTime getCreatedAt()                    { return createdAt; }
    public LocalDateTime getUpdatedAt()                    { return updatedAt; }

    public void setId(UUID id)                             { this.id = id; }
    public void setStatus(AlertStatus status)              { this.status = status; }
    public void setCurrentKm(Float km)                     { this.currentKm = km; }
    public void setKmRemaining(Float remaining)            { this.kmRemaining = remaining; }
    public void setDaysRemaining(Integer days)             { this.daysRemaining = days; }
    public void setUpdatedAt(LocalDateTime updatedAt)      { this.updatedAt = updatedAt; }
    public void setResolvedByMaintenanceId(UUID id)        { this.resolvedByMaintenanceId = id; }
    public void setResolvedAt(LocalDateTime at)            { this.resolvedAt = at; }
}
