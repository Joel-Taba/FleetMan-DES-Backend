package com.yowyob.fleet.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entité du domaine : Recharge de carburant.
 * Domaine pur — aucune dépendance Spring, JPA ou Jackson.
 *
 * Représente un plein de carburant effectué sur un véhicule.
 * Les invariants métier (quantité positive, prix non négatif) sont validés à la construction.
 */
public class FuelRecharge {

    // ── Enum du domaine ───────────────────────────────────────────────────────

    public enum StationName {
        TOTAL,
        SHELL,
        OILIBYA,
        CAMRAIL,
        OTHER
    }

    // ── Champs ───────────────────────────────────────────────────────────────

    private UUID id;

    /** Quantité de carburant rechargée en litres (obligatoire, > 0) */
    private final BigDecimal quantity;

    /** Prix total payé pour la recharge (obligatoire, >= 0) */
    private final BigDecimal price;

    /** Date et heure de la recharge */
    private final LocalDateTime rechargeDateTime;

    /** Localisation GPS de la station (optionnelle) */
    private Coordinates location;

    /** Nom de la station-service */
    private StationName stationName;

    // ── Références croisées par ID uniquement ────────────────────────────────

    /** Véhicule rechargé (obligatoire) */
    private final UUID vehicleId;

    /** Numéro d'immatriculation (dénormalisé pour l'affichage) */
    private final String vehicleRegistration;

    /** Chauffeur ayant effectué la recharge (optionnel) */
    private UUID driverId;

    /** Nom complet du chauffeur (dénormalisé pour l'affichage) */
    private String driverFullName;

    // ─────────────────────────────────────────────────────────────────────────

    public FuelRecharge(UUID id,
                        BigDecimal quantity,
                        BigDecimal price,
                        LocalDateTime rechargeDateTime,
                        Coordinates location,
                        StationName stationName,
                        UUID vehicleId,
                        String vehicleRegistration,
                        UUID driverId,
                        String driverFullName) {

        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("La quantité de carburant doit être strictement positive.");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Le prix de la recharge ne peut pas être négatif.");
        }
        if (vehicleId == null) {
            throw new IllegalArgumentException("Le véhicule est obligatoire pour une recharge de carburant.");
        }

        this.id = id;
        this.quantity = quantity;
        this.price = price;
        this.rechargeDateTime = rechargeDateTime != null ? rechargeDateTime : LocalDateTime.now();
        this.location = location;
        this.stationName = stationName;
        this.vehicleId = vehicleId;
        this.vehicleRegistration = vehicleRegistration;
        this.driverId = driverId;
        this.driverFullName = driverFullName;
    }

    // ── Méthodes métier ───────────────────────────────────────────────────────

    /**
     * Calcule le coût unitaire (prix par litre).
     * Retourne zéro si la quantité est nulle (protection contre la division par zéro).
     */
    public BigDecimal unitCost() {
        if (quantity.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return price.divide(quantity, 2, RoundingMode.HALF_UP);
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public UUID getId()                      { return id; }
    public BigDecimal getQuantity()          { return quantity; }
    public BigDecimal getPrice()             { return price; }
    public LocalDateTime getRechargeDateTime() { return rechargeDateTime; }
    public Coordinates getLocation()         { return location; }
    public StationName getStationName()      { return stationName; }
    public UUID getVehicleId()               { return vehicleId; }
    public String getVehicleRegistration()   { return vehicleRegistration; }
    public UUID getDriverId()                { return driverId; }
    public String getDriverFullName()        { return driverFullName; }

    /** Setter réservé à la couche persistance */
    public void setId(UUID id)               { this.id = id; }
}
