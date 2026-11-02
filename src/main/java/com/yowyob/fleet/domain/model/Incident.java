package com.yowyob.fleet.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entité du domaine : Incident terrain.
 * Domaine pur — aucune dépendance Spring, JPA ou Jackson.
 *
 * Représente un événement imprévu survenu sur un véhicule
 * (accident, panne, vol, vandalisme, infraction, autre).
 *
 * Machine à états : REPORTED → ACKNOWLEDGED → UNDER_INVESTIGATION → RESOLVED → CLOSED, ou CANCELLED (IncidentRules).
 */
public class Incident {

    // ── Enums du domaine ─────────────────────────────────────────────────────

    public enum Type {
        ACCIDENT("Accident"),
        BREAKDOWN("Panne"),
        THEFT("Vol"),
        VANDALISM("Vandalisme"),
        TRAFFIC_VIOLATION("Infraction routière"),
        OTHER("Autre");

        private final String label;

        Type(String label) {
            this.label = label;
        }

        /** Libellé lisible (notifications, emails), à la place du code technique. */
        public String label() {
            return label;
        }
    }

    public enum Severity {
        LOW("faible"),
        MEDIUM("moyenne"),
        HIGH("élevée"),
        CRITICAL("critique");

        private final String label;

        Severity(String label) {
            this.label = label;
        }

        /** Libellé lisible (notifications, emails), à la place du code technique. */
        public String label() {
            return label;
        }
    }

    public enum Status {
        REPORTED,
        ACKNOWLEDGED,
        UNDER_INVESTIGATION,
        RESOLVED,
        CLOSED,
        CANCELLED
    }

    // ── Champs ───────────────────────────────────────────────────────────────

    private UUID id;

    /** Type d'incident (obligatoire) */
    private final Type type;

    /** Description libre de l'incident */
    private String description;

    /** Niveau de gravité */
    private Severity severity;

    /** Date et heure de l'incident */
    private final LocalDateTime incidentDateTime;

    /** Localisation GPS de l'incident (optionnelle) */
    private Coordinates location;

    /** Coût estimé ou réel de l'incident */
    private BigDecimal cost;

    /** Statut courant dans le cycle de vie */
    private Status status;
    private boolean vehicleImmobilized;

    /** Le véhicule peut-il rouler ? Déclaré au signalement. */
    private boolean vehicleDrivable = true;

    /** Décision motivée du gestionnaire ; sinon l'immobilisation suit la règle automatique. */
    private Boolean immobilizationOverride;

    /** Rapport de résolution */
    private String report;

    /** Informations sur le témoin éventuel */
    private String witnessName;
    private String witnessContact;

    /** Numéro de procès-verbal de police */
    private String policeReportNumber;

    /** Numéro de déclaration d'assurance */
    private String insuranceClaimNumber;

    /** Identifiant de la personne ayant signalé l'incident */
    private String reportedBy;

    /** Date et heure de résolution */
    private LocalDateTime resolvedAt;

    // ── Références croisées par ID uniquement ────────────────────────────────

    /** Véhicule impliqué (obligatoire) */
    private final UUID vehicleId;

    /** Numéro d'immatriculation (dénormalisé pour l'affichage) */
    private final String vehicleRegistration;

    /** Chauffeur impliqué (optionnel) */
    private UUID driverId;

    /** Nom complet du chauffeur (dénormalisé pour l'affichage) */
    private String driverFullName;

    // ─────────────────────────────────────────────────────────────────────────

    public Incident(UUID id,
                    Type type,
                    String description,
                    Severity severity,
                    LocalDateTime incidentDateTime,
                    Coordinates location,
                    BigDecimal cost,
                    String reportedBy,
                    UUID vehicleId,
                    String vehicleRegistration,
                    UUID driverId,
                    String driverFullName) {

        if (type == null) {
            throw new IllegalArgumentException("Le type d'incident est obligatoire.");
        }
        if (vehicleId == null) {
            throw new IllegalArgumentException("Le véhicule est obligatoire pour un incident.");
        }
        requireNonNegativeCost(cost);

        this.id = id;
        this.type = type;
        this.description = description;
        this.severity = severity != null ? severity : Severity.MEDIUM;
        this.incidentDateTime = incidentDateTime != null ? incidentDateTime : LocalDateTime.now();
        this.location = location;
        this.cost = cost;
        this.status = Status.REPORTED;
        this.reportedBy = reportedBy;
        this.vehicleId = vehicleId;
        this.vehicleRegistration = vehicleRegistration;
        this.driverId = driverId;
        this.driverFullName = driverFullName;
    }

    // ── Méthodes métier (machine à états) ────────────────────────────────────

    /**
     * Résout l'incident avec un rapport de clôture.
     * Interdit si l'incident est déjà clôturé.
     */
    public void resolve(String report) {
        if (this.status == Status.CLOSED) {
            throw new IllegalStateException("Un incident clôturé ne peut pas être résolu.");
        }
        this.status = Status.RESOLVED;
        this.report = report;
        this.resolvedAt = LocalDateTime.now();
    }

    /**
     * Clôture définitivement l'incident.
     * Horodate automatiquement si pas encore résolu.
     */
    public void close() {
        this.status = Status.CLOSED;
        if (this.resolvedAt == null) {
            this.resolvedAt = LocalDateTime.now();
        }
    }

    /**
     * Enregistre les informations d'un témoin.
     */
    public void addWitness(String name, String contact) {
        this.witnessName = name;
        this.witnessContact = contact;
    }

    /**
     * Met à jour le statut de l'incident.
     * Horodate automatiquement si le nouveau statut est terminal.
     */
    public void updateStatus(Status newStatus) {
        if (newStatus == Status.RESOLVED || newStatus == Status.CLOSED) {
            this.resolvedAt = LocalDateTime.now();
        } else {
            // Réouverture (REPORTED / UNDER_INVESTIGATION) : l'incident n'est plus résolu,
            // sa date de résolution ne doit plus fausser les délais de traitement.
            this.resolvedAt = null;
        }
        this.status = newStatus;
    }

    private static void requireNonNegativeCost(BigDecimal cost) {
        if (cost != null && cost.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Le coût d'un incident ne peut pas être négatif.");
        }
    }

    /**
     * Indique si l'incident est critique ou grave (nécessite une alerte prioritaire).
     */
    public boolean isCritical() {
        return this.severity == Severity.CRITICAL || this.severity == Severity.HIGH;
    }

    /**
     * Indique si l'incident est encore ouvert (non résolu, non clôturé).
     */
    public boolean isOpen() {
        return this.status == Status.REPORTED || this.status == Status.ACKNOWLEDGED
                || this.status == Status.UNDER_INVESTIGATION;
    }

    /**
     * Un incident ouvert rend le véhicule indisponible s'il est grave (HIGH/CRITICAL), si c'est un
     * vol ou si le véhicule ne roule plus, sauf décision contraire du gestionnaire.
     */
    public boolean immobilizesVehicle() {
        if (!isOpen()) {
            return false;
        }
        return immobilizationOverride != null
                ? immobilizationOverride
                : isCritical() || type == Type.THEFT || !vehicleDrivable;
    }

    public boolean isVehicleDrivable()       { return vehicleDrivable; }
    public void setVehicleDrivable(boolean drivable) { this.vehicleDrivable = drivable; }
    public Boolean getImmobilizationOverride() { return immobilizationOverride; }
    public void setImmobilizationOverride(Boolean override) { this.immobilizationOverride = override; }

    /** Annulation motivée : le dossier reste dans l'historique. */
    public void cancel() {
        this.status = Status.CANCELLED;
    }

    /** Relecture depuis la base : ne recalcule rien (la date de résolution était écrasée à chaque lecture). */
    public void restoreStatus(Status status, LocalDateTime resolvedAt) {
        this.status = status;
        this.resolvedAt = resolvedAt;
    }

    /** true si c'est (aussi) cet incident qui maintient le véhicule immobilisé. */
    public boolean isVehicleImmobilized()    { return vehicleImmobilized; }
    public void setVehicleImmobilized(boolean vehicleImmobilized) { this.vehicleImmobilized = vehicleImmobilized; }

    // ── Getters ───────────────────────────────────────────────────────────────

    public UUID getId()                      { return id; }
    public Type getType()                    { return type; }
    public String getDescription()           { return description; }
    public Severity getSeverity()            { return severity; }
    public LocalDateTime getIncidentDateTime() { return incidentDateTime; }
    public Coordinates getLocation()         { return location; }
    public BigDecimal getCost()              { return cost; }
    public Status getStatus()                { return status; }
    public String getReport()                { return report; }
    public String getWitnessName()           { return witnessName; }
    public String getWitnessContact()        { return witnessContact; }
    public String getPoliceReportNumber()    { return policeReportNumber; }
    public String getInsuranceClaimNumber()  { return insuranceClaimNumber; }
    public String getReportedBy()            { return reportedBy; }
    public LocalDateTime getResolvedAt()     { return resolvedAt; }
    public UUID getVehicleId()               { return vehicleId; }
    public String getVehicleRegistration()   { return vehicleRegistration; }
    public UUID getDriverId()                { return driverId; }
    public String getDriverFullName()        { return driverFullName; }

    // ── Setters limités ───────────────────────────────────────────────────────

    /** Setter réservé à la couche persistance */
    public void setId(UUID id)                              { this.id = id; }
    public void setSeverity(Severity severity)              { this.severity = severity; }
    public void setDescription(String description)          { this.description = description; }
    public void setCost(BigDecimal cost)                    { requireNonNegativeCost(cost); this.cost = cost; }
    public void setPoliceReportNumber(String number)        { this.policeReportNumber = number; }
    public void setInsuranceClaimNumber(String number)      { this.insuranceClaimNumber = number; }
    public void setLocation(Coordinates location)           { this.location = location; }
    public void setReport(String report)                    { this.report = report; }
}
