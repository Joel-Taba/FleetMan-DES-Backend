package com.yowyob.fleet.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entité du domaine : Plan de maintenance préventive.
 * Domaine pur — aucune dépendance Spring, JPA ou Jackson.
 *
 * Un plan définit les seuils de déclenchement d'une alerte de maintenance
 * pour un type d'intervention donné (vidange, freins, courroie...).
 *
 * Deux niveaux de plans :
 * - GLOBAL : applicable à tous les véhicules d'une flotte (scope = FLEET)
 * - INDIVIDUAL : surcharge pour un véhicule spécifique (scope = VEHICLE)
 *
 * Double seuil : l'alerte se déclenche quand le seuil km OU le seuil temporel est atteint
 * (le premier des deux).
 */
public class MaintenancePlan {

    // ── Enums du domaine ─────────────────────────────────────────────────────

    /** Type d'intervention préventive */
    public enum MaintenanceType {
        OIL_CHANGE,         // Vidange moteur
        TIRE_ROTATION,      // Rotation des pneus
        BRAKE_INSPECTION,   // Inspection des freins
        FILTER_CHANGE,      // Remplacement filtres (air, carburant)
        TIMING_BELT,        // Courroie de distribution
        COOLANT_FLUSH,      // Vidange du liquide de refroidissement
        TRANSMISSION_SERVICE, // Révision de la transmission
        GENERAL_INSPECTION, // Révision générale
        OTHER               // Autre
    }

    /** Portée du plan */
    public enum PlanScope {
        FLEET,    // Applicable à tous les véhicules de la flotte
        VEHICLE   // Surcharge pour un véhicule spécifique
    }

    // ── Champs ───────────────────────────────────────────────────────────────

    private UUID id;

    /** Type de maintenance couverte */
    private final MaintenanceType maintenanceType;

    /** Portée du plan */
    private final PlanScope scope;

    /** ID de la flotte (toujours renseigné) */
    private final UUID fleetId;

    /** ID du véhicule (uniquement si scope = VEHICLE) */
    private final UUID vehicleId;

    /** Manager propriétaire */
    private final UUID managerId;

    /** Libellé personnalisé du plan (optionnel) */
    private String label;

    /** Description détaillée (optionnel) */
    private String description;

    // ── Seuils de déclenchement ───────────────────────────────────────────────

    /** Intervalle kilométrique entre deux maintenances (ex: 10 000 km pour vidange) */
    private Integer intervalKm;

    /** Intervalle temporel en jours (ex: 180 jours = 6 mois) */
    private Integer intervalDays;

    /** Seuil de préalerte en km (ex: 500 km avant le seuil → alerte UPCOMING) */
    private Integer preAlertKm;

    /** Seuil de préalerte en jours (ex: 30 jours avant le seuil → alerte UPCOMING) */
    private Integer preAlertDays;

    /** Plan actif ou désactivé */
    private boolean active;

    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ─────────────────────────────────────────────────────────────────────────

    public MaintenancePlan(UUID id,
                           MaintenanceType maintenanceType,
                           PlanScope scope,
                           UUID fleetId,
                           UUID vehicleId,
                           UUID managerId,
                           String label,
                           String description,
                           Integer intervalKm,
                           Integer intervalDays,
                           Integer preAlertKm,
                           Integer preAlertDays,
                           boolean active,
                           LocalDateTime createdAt,
                           LocalDateTime updatedAt) {

        if (maintenanceType == null)
            throw new IllegalArgumentException("Le type de maintenance est obligatoire.");
        if (scope == null)
            throw new IllegalArgumentException("La portée du plan est obligatoire.");
        if (fleetId == null)
            throw new IllegalArgumentException("La flotte est obligatoire.");
        if (managerId == null)
            throw new IllegalArgumentException("Le manager est obligatoire.");
        if (scope == PlanScope.VEHICLE && vehicleId == null)
            throw new IllegalArgumentException("Le véhicule est obligatoire pour un plan individuel.");
        if ((intervalKm == null || intervalKm <= 0) && (intervalDays == null || intervalDays <= 0))
            throw new IllegalArgumentException(
                    "Au moins un seuil (km ou jours) doit être défini pour un plan de maintenance.");

        this.id = id;
        this.maintenanceType = maintenanceType;
        this.scope = scope;
        this.fleetId = fleetId;
        this.vehicleId = vehicleId;
        this.managerId = managerId;
        this.label = label != null ? label : maintenanceType.name();
        this.description = description;
        this.intervalKm = intervalKm;
        this.intervalDays = intervalDays;
        this.preAlertKm = preAlertKm != null ? preAlertKm : (intervalKm != null ? intervalKm / 10 : null);
        this.preAlertDays = preAlertDays != null ? preAlertDays : 30;
        this.active = active;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        this.updatedAt = updatedAt != null ? updatedAt : LocalDateTime.now();
    }

    // ── Méthodes métier ───────────────────────────────────────────────────────

    /**
     * Calcule le kilométrage auquel la prochaine maintenance doit être effectuée.
     * Retourne null si aucun seuil kilométrique n'est défini.
     */
    public Float computeNextMaintenanceKm(Float lastMaintenanceKm) {
        if (intervalKm == null || lastMaintenanceKm == null) return null;
        return lastMaintenanceKm + intervalKm;
    }

    /**
     * Calcule la date à laquelle la prochaine maintenance doit être effectuée.
     * Retourne null si aucun seuil temporel n'est défini.
     */
    public LocalDate computeNextMaintenanceDate(LocalDate lastMaintenanceDate) {
        if (intervalDays == null || lastMaintenanceDate == null) return null;
        return lastMaintenanceDate.plusDays(intervalDays);
    }

    /** Active ou désactive le plan. */
    public void setActive(boolean active) { this.active = active; }

    public void setId(UUID id)                         { this.id = id; }
    public void setLabel(String label)                 { this.label = label; }
    public void setDescription(String description)     { this.description = description; }
    public void setIntervalKm(Integer intervalKm)      { this.intervalKm = intervalKm; }
    public void setIntervalDays(Integer intervalDays)  { this.intervalDays = intervalDays; }
    public void setPreAlertKm(Integer preAlertKm)      { this.preAlertKm = preAlertKm; }
    public void setPreAlertDays(Integer preAlertDays)  { this.preAlertDays = preAlertDays; }
    public void setUpdatedAt(LocalDateTime updatedAt)  { this.updatedAt = updatedAt; }

    // ── Getters ───────────────────────────────────────────────────────────────

    public UUID getId()                        { return id; }
    public MaintenanceType getMaintenanceType(){ return maintenanceType; }
    public PlanScope getScope()                { return scope; }
    public UUID getFleetId()                   { return fleetId; }
    public UUID getVehicleId()                 { return vehicleId; }
    public UUID getManagerId()                 { return managerId; }
    public String getLabel()                   { return label; }
    public String getDescription()             { return description; }
    public Integer getIntervalKm()             { return intervalKm; }
    public Integer getIntervalDays()           { return intervalDays; }
    public Integer getPreAlertKm()             { return preAlertKm; }
    public Integer getPreAlertDays()           { return preAlertDays; }
    public boolean isActive()                  { return active; }
    public LocalDateTime getCreatedAt()        { return createdAt; }
    public LocalDateTime getUpdatedAt()        { return updatedAt; }
}
