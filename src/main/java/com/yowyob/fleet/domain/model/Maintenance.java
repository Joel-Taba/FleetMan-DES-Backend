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
}
