package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import com.yowyob.fleet.domain.model.DriverScore;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "Réponse représentant le score de conduite d'un chauffeur")
public record DriverScoreResponse(

        @Schema(description = "Identifiant unique du score")
        UUID id,

        @Schema(description = "ID du chauffeur évalué")
        UUID driverId,

        @Schema(description = "ID de la flotte")
        UUID fleetId,

        @Schema(description = "Type de période (WEEKLY, MONTHLY)")
        String periodType,

        @Schema(description = "Début de la période évaluée")
        LocalDate periodStart,

        @Schema(description = "Fin de la période évaluée")
        LocalDate periodEnd,

        // ── Score final ─────────────────────────────────────────────────────

        @Schema(description = "Score global sur 100 points")
        BigDecimal finalScore,

        @Schema(description = "Badge attribué (EXCELLENCE, GOOD, SATISFACTORY, WARNING, INSUFFICIENT)")
        String badge,

        // ── Données brutes ──────────────────────────────────────────────────

        @Schema(description = "Nombre d'incidents sur la période")
        int incidentCount,

        @Schema(description = "Nombre total de trajets (affectations)")
        int totalTrips,

        @Schema(description = "Consommation carburant en L/100km (null si pas de données)")
        BigDecimal fuelPer100Km,

        @Schema(description = "Consommation moyenne de la flotte en L/100km")
        BigDecimal fleetAvgFuelPer100Km,

        @Schema(description = "Taux de conformité documentaire en %")
        BigDecimal docComplianceRate,

        @Schema(description = "Nombre de maintenances anormales")
        int abnormalMaintenanceCount,

        @Schema(description = "Affectations terminées avec succès")
        int completedAssignments,

        @Schema(description = "Absences (NO_SHOW)")
        int noShowAssignments,

        // ── Détail des composantes ──────────────────────────────────────────

        @Schema(description = "Détail des 5 composantes du score avec leurs poids")
        List<ComponentDto> components,

        @Schema(description = "Date de calcul du score")
        LocalDateTime calculatedAt
) {

    @Schema(description = "Composante individuelle du score")
    public record ComponentDto(
            String label,
            int weight,
            String rawValue,
            BigDecimal score
    ) {}

    public static DriverScoreResponse from(DriverScore s) {
        List<ComponentDto> components = s.getComponents().stream()
                .map(c -> new ComponentDto(c.label(), c.weight(), c.rawValue(), c.score()))
                .toList();

        return new DriverScoreResponse(
                s.getId(), s.getDriverId(), s.getFleetId(),
                s.getPeriodType() != null ? s.getPeriodType().name() : null,
                s.getPeriodStart(), s.getPeriodEnd(),
                s.getFinalScore(),
                s.getBadge() != null ? s.getBadge().name() : null,
                s.getIncidentCount(), s.getTotalTrips(),
                s.getFuelPer100Km(), s.getFleetAvgFuelPer100Km(),
                s.getDocComplianceRate(), s.getAbnormalMaintenanceCount(),
                s.getCompletedAssignments(), s.getNoShowAssignments(),
                components, s.getCalculatedAt()
        );
    }
}
