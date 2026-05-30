package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

public record ManagerKpiResponse(
    @Schema(description = "Nombre total de flottes enregistrées", example = "2")
    long totalFleets,

    @Schema(description = "Nombre total de véhicules dans toutes les flottes", example = "25")
    long totalVehicles,

    @Schema(description = "Nombre total de chauffeurs actifs/recrutés", example = "18")
    long totalDrivers,

    @Schema(description = "Nombre de trajets actuellement en cours", example = "5")
    long activeTrips,

    // ── KPIs Opérations Terrain (Phase 6) ────────────────────────────────────

    @Schema(description = "Nombre de maintenances enregistrées ce mois-ci", example = "3")
    long maintenancesThisMonth,

    @Schema(description = "Nombre d'incidents encore ouverts (REPORTED ou UNDER_INVESTIGATION)", example = "2")
    long openIncidents,

    @Schema(description = "Coût total des incidents (tous véhicules confondus) en FCFA", example = "450000.00")
    BigDecimal totalIncidentCost,

    @Schema(description = "Consommation totale de carburant ce mois-ci en litres", example = "1250.50")
    BigDecimal totalFuelLitersThisMonth,

    @Schema(description = "Coût total des recharges carburant ce mois-ci en FCFA", example = "937875.00")
    BigDecimal totalFuelCostThisMonth
) {
    /**
     * Constructeur de compatibilité ascendante — conserve les 4 champs existants
     * avec des valeurs par défaut pour les nouveaux champs opérations.
     */
    public static ManagerKpiResponse legacy(long totalFleets, long totalVehicles,
                                            long totalDrivers, long activeTrips) {
        return new ManagerKpiResponse(
            totalFleets, totalVehicles, totalDrivers, activeTrips,
            0L, 0L, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        );
    }
}