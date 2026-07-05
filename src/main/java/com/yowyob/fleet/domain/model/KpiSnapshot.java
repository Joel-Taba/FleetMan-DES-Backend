package com.yowyob.fleet.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Value Object de domaine : Instantané de KPIs.
 * Domaine pur — aucune dépendance Spring, JPA ou Jackson.
 *
 * Stocke les indicateurs de performance calculés pour une entité
 * (flotte, véhicule ou conducteur) sur une période donnée.
 *
 * Immuable par conception : un snapshot est une photographie à un instant T.
 * Pour mettre à jour, on crée un nouveau snapshot.
 */
public record KpiSnapshot(

        UUID id,

        UUID fleetId,

        /** Type d'entité mesurée */
        EntityType entityType,

        /** ID de l'entité (flotte, véhicule ou conducteur) */
        UUID entityId,

        /** Granularité temporelle */
        PeriodType periodType,

        LocalDate periodStart,
        LocalDate periodEnd,

        // ── KPIs Opérationnels ────────────────────────────────────────────────

        /** Kilomètres totaux parcourus sur la période */
        BigDecimal totalKm,

        /** Nombre de trajets effectués */
        Integer totalTrips,

        /** Heures de conduite totales */
        BigDecimal totalDrivingHours,

        /** Taux de disponibilité en % (véhicules disponibles / total) */
        BigDecimal availabilityRate,

        // ── KPIs Financiers ───────────────────────────────────────────────────

        /** Coût total carburant (FCFA) */
        BigDecimal totalFuelCost,

        /** Litres de carburant consommés */
        BigDecimal totalFuelLiters,

        /** Coût total maintenances (FCFA) */
        BigDecimal totalMaintenanceCost,

        /** Coût total incidents (FCFA) */
        BigDecimal totalIncidentCost,

        /** Coût par kilomètre (FCFA/km) */
        BigDecimal costPerKm,

        /** Consommation carburant (L/100km) */
        BigDecimal fuelPer100Km,

        // ── KPIs Sécurité ─────────────────────────────────────────────────────

        /** Nombre total d'incidents */
        Integer totalIncidents,

        /** Taux d'incidents pour 1000 km */
        BigDecimal incidentRate,

        /** Score moyen des conducteurs (sur 100) */
        BigDecimal avgDriverScore,

        // ── KPIs Conformité ───────────────────────────────────────────────────

        /** Taux de conformité documentaire en % */
        BigDecimal docComplianceRate,

        LocalDateTime calculatedAt

) {
    public enum EntityType { FLEET, VEHICLE, DRIVER }
    public enum PeriodType { DAILY, WEEKLY, MONTHLY }

    /**
     * Calcule le coût par km à partir des totaux.
     * Retourne null si le kilométrage est nul ou inconnu.
     */
    public static BigDecimal computeCostPerKm(BigDecimal totalCost, BigDecimal totalKm) {
        if (totalKm == null || totalKm.compareTo(BigDecimal.ZERO) == 0) return null;
        if (totalCost == null) return BigDecimal.ZERO;
        return totalCost.divide(totalKm, 4, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Calcule la consommation L/100km.
     */
    public static BigDecimal computeFuelPer100Km(BigDecimal liters, BigDecimal km) {
        if (km == null || km.compareTo(BigDecimal.ZERO) == 0) return null;
        if (liters == null) return BigDecimal.ZERO;
        return liters.multiply(BigDecimal.valueOf(100))
                .divide(km, 2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Calcule le taux d'incidents pour 1000 km.
     */
    public static BigDecimal computeIncidentRate(int incidents, BigDecimal km) {
        if (km == null || km.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(incidents)
                .multiply(BigDecimal.valueOf(1000))
                .divide(km, 4, java.math.RoundingMode.HALF_UP);
    }
}
