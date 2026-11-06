package com.yowyob.fleet.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entité du domaine : Maintenance véhicule.
 * Domaine pur — aucune dépendance Spring, JPA ou Jackson.
 *
 * Représente une intervention technique sur un véhicule.
 * Les invariants métier sont validés à la construction.
 */
public class Maintenance {

    private UUID id;

    /** Objet / titre de l'intervention (obligatoire) */
    private final String subject;

    /** Coût de l'intervention (peut être null si non encore connu) */
    private BigDecimal cost;

    /** Date et heure de l'intervention */
    private final LocalDateTime dateTime;

    /** Rapport détaillé de l'intervention (peut être ajouté après coup) */
    private String report;

    /** Localisation GPS de l'intervention (optionnelle) */
    private Coordinates location;

    /** Nom du lieu (ex : "Garage Central Yaoundé") */
    private String locationName;

    // ── Références croisées par ID uniquement (pas d'entités JPA dans le domaine) ──

    /** Véhicule concerné (obligatoire) */
    private final UUID vehicleId;

    /** Numéro d'immatriculation du véhicule (dénormalisé pour l'affichage) */
    private final String vehicleRegistrationNumber;

    /** Chauffeur impliqué (optionnel) */
    private UUID driverId;

    /** Nom complet du chauffeur (dénormalisé pour l'affichage) */
    private String driverFullName;

    // ─────────────────────────────────────────────────────────────────────────

    public Maintenance(UUID id,
                       String subject,
                       BigDecimal cost,
                       LocalDateTime dateTime,
                       String report,
                       Coordinates location,
                       String locationName,
                       UUID vehicleId,
                       String vehicleRegistrationNumber,
                       UUID driverId,
                       String driverFullName) {

        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("L'objet de la maintenance est obligatoire.");
        }
        if (vehicleId == null) {
            throw new IllegalArgumentException("Le véhicule est obligatoire pour une maintenance.");
        }
        // Même règle qu'updateCost : un coût négatif était refusé à la modification
        // mais accepté à la création.
        if (cost != null && cost.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Le coût de maintenance ne peut pas être négatif.");
        }

        this.id = id;
        this.subject = subject;
        this.cost = cost;
        this.dateTime = dateTime != null ? dateTime : LocalDateTime.now();
        this.report = report;
        this.location = location;
        this.locationName = locationName;
        this.vehicleId = vehicleId;
        this.vehicleRegistrationNumber = vehicleRegistrationNumber;
        this.driverId = driverId;
        this.driverFullName = driverFullName;
    }

    // ── Méthodes métier ───────────────────────────────────────────────────────

    /**
     * Ajoute ou met à jour le rapport de l'intervention.
     */
    public void addReport(String report) {
        this.report = report;
    }

    /**
     * Met à jour le coût de l'intervention.
     * Le coût ne peut pas être négatif.
     */
    public void updateCost(BigDecimal newCost) {
        if (newCost != null && newCost.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Le coût de maintenance ne peut pas être négatif.");
        }
        this.cost = newCost;
    }

    /**
     * Met à jour la localisation de l'intervention.
     */
    public void updateLocation(Coordinates location, String locationName) {
        this.location = location;
        this.locationName = locationName;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public UUID getId()                        { return id; }
    public String getSubject()                 { return subject; }
    public BigDecimal getCost()                { return cost; }
    public LocalDateTime getDateTime()         { return dateTime; }
    public String getReport()                  { return report; }
    public Coordinates getLocation()           { return location; }
    public String getLocationName()            { return locationName; }
    public UUID getVehicleId()                 { return vehicleId; }
    public String getVehicleRegistrationNumber() { return vehicleRegistrationNumber; }
    public UUID getDriverId()                  { return driverId; }
    public String getDriverFullName()          { return driverFullName; }

    /** Setter réservé à la couche persistance (assignation de l'ID après sauvegarde) */
    public void setId(UUID id)                 { this.id = id; }

    // ── Cycle de vie (demande du chauffeur → atelier → clôture) ──────────────

    /**
     * <pre>
     *   REQUESTED ─┬→ PLANNED ─┬→ IN_PROGRESS → COMPLETED
     *              │           └→ COMPLETED
     *              ├→ IN_PROGRESS / COMPLETED
     *              └→ CANCELLED (depuis tout état ouvert, motif obligatoire)
     * </pre>
     * Une intervention saisie par le gestionnaire après coup naît directement COMPLETED.
     */
    public enum Status {
        REQUESTED, PLANNED, IN_PROGRESS, COMPLETED, CANCELLED;

        public boolean isOpen() { return this != COMPLETED && this != CANCELLED; }
    }

    public static final java.util.Set<String> TYPES = java.util.Set.of("PREVENTIVE", "CORRECTIVE", "EMERGENCY");

    private Status status = Status.COMPLETED;
    private String maintenanceType;
    private boolean urgent;
    private LocalDateTime plannedAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    private String cancelReason;
    private UUID requestedBy;

    public Status getStatus()                  { return status; }
    public String getMaintenanceType()         { return maintenanceType; }
    public boolean isUrgent()                  { return urgent; }
    public LocalDateTime getPlannedAt()        { return plannedAt; }
    public LocalDateTime getStartedAt()        { return startedAt; }
    public LocalDateTime getCompletedAt()      { return completedAt; }
    public LocalDateTime getCancelledAt()      { return cancelledAt; }
    public String getCancelReason()            { return cancelReason; }
    public UUID getRequestedBy()               { return requestedBy; }

    /** Restauration depuis la persistance (aucune règle appliquée). */
    public void restoreWorkflow(Status status, String type, boolean urgent, LocalDateTime plannedAt,
                                LocalDateTime startedAt, LocalDateTime completedAt, LocalDateTime cancelledAt,
                                String cancelReason, UUID requestedBy) {
        this.status = status != null ? status : Status.COMPLETED;
        this.maintenanceType = type;
        this.urgent = urgent;
        this.plannedAt = plannedAt;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.cancelledAt = cancelledAt;
        this.cancelReason = cancelReason;
        this.requestedBy = requestedBy;
    }

    /** État initial d'une nouvelle maintenance (demande, ordre planifié, intervention en cours ou terminée). */
    public void initWorkflow(Status initial, String type, boolean urgent, LocalDateTime plannedAt, UUID requestedBy,
                             LocalDateTime now) {
        Status s = initial != null ? initial : Status.COMPLETED;
        if (s == Status.CANCELLED) {
            throw com.yowyob.fleet.domain.exception.MaintenanceWorkflowException.invalidTransition("REQUESTED", "CANCELLED");
        }
        if (s == Status.PLANNED && plannedAt == null) {
            throw com.yowyob.fleet.domain.exception.MaintenanceWorkflowException.plannedDateRequired();
        }
        this.status = s;
        this.maintenanceType = type != null && TYPES.contains(type) ? type : null;
        this.urgent = urgent || "EMERGENCY".equals(this.maintenanceType);
        this.plannedAt = plannedAt;
        this.requestedBy = requestedBy;
        if (s == Status.IN_PROGRESS) this.startedAt = now;
        if (s == Status.COMPLETED) this.completedAt = now;
    }

    private void require(Status target, Status... allowed) {
        for (Status a : allowed) {
            if (a == status) return;
        }
        throw com.yowyob.fleet.domain.exception.MaintenanceWorkflowException.invalidTransition(status.name(), target.name());
    }

    /** Planifie (ou replanifie) l'intervention : date prévue obligatoire, garage optionnel. */
    public void plan(LocalDateTime plannedAt, String garage) {
        require(Status.PLANNED, Status.REQUESTED, Status.PLANNED);
        if (plannedAt == null) {
            throw com.yowyob.fleet.domain.exception.MaintenanceWorkflowException.plannedDateRequired();
        }
        this.plannedAt = plannedAt;
        if (garage != null && !garage.isBlank()) this.locationName = garage.trim();
        this.status = Status.PLANNED;
    }

    /** Le véhicule entre à l'atelier. */
    public void start(LocalDateTime now) {
        require(Status.IN_PROGRESS, Status.REQUESTED, Status.PLANNED);
        this.status = Status.IN_PROGRESS;
        this.startedAt = now;
    }

    /** Intervention terminée : coût et rapport définitifs optionnels. */
    public void complete(LocalDateTime now, BigDecimal finalCost, String finalReport) {
        require(Status.COMPLETED, Status.REQUESTED, Status.PLANNED, Status.IN_PROGRESS);
        if (finalCost != null) updateCost(finalCost);
        if (finalReport != null && !finalReport.isBlank()) this.report = finalReport.trim();
        if (this.startedAt == null) this.startedAt = now;
        this.completedAt = now;
        this.status = Status.COMPLETED;
    }

    /** Annulation d'une maintenance encore ouverte, motif obligatoire. */
    public void cancel(LocalDateTime now, String reason) {
        require(Status.CANCELLED, Status.REQUESTED, Status.PLANNED, Status.IN_PROGRESS);
        if (reason == null || reason.isBlank()) {
            throw com.yowyob.fleet.domain.exception.MaintenanceWorkflowException.cancelReasonRequired();
        }
        this.cancelReason = reason.trim();
        this.cancelledAt = now;
        this.status = Status.CANCELLED;
    }
}
