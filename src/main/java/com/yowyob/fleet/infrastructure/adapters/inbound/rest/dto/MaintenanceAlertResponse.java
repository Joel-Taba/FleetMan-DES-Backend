package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import com.yowyob.fleet.domain.model.MaintenanceAlert;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Réponse représentant une alerte de maintenance préventive")
public record MaintenanceAlertResponse(

        UUID id,
        UUID planId,

        @Schema(description = "Type d'intervention (OIL_CHANGE, BRAKE_INSPECTION...)")
        String maintenanceType,

        UUID vehicleId,
        String vehicleRegistration,
        UUID fleetId,

        @Schema(description = "Statut (UPCOMING, DUE, OVERDUE, RESOLVED)")
        String status,

        @Schema(description = "Cause (MILEAGE, DATE, BOTH)")
        String triggerType,

        @Schema(description = "Indique si la maintenance est urgente (DUE ou OVERDUE)")
        boolean urgent,

        // ── Infos kilométriques ────────────────────────────────────────────

        Float targetKm,
        Float currentKm,

        @Schema(description = "Km restants avant le seuil (négatif si dépassé)")
        Float kmRemaining,

        // ── Infos temporelles ─────────────────────────────────────────────

        LocalDate targetDate,

        @Schema(description = "Jours restants avant la date cible (négatif si dépassé)")
        Integer daysRemaining,

        // ── Résolution ────────────────────────────────────────────────────

        UUID resolvedByMaintenanceId,
        LocalDateTime resolvedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static MaintenanceAlertResponse from(MaintenanceAlert a) {
        return new MaintenanceAlertResponse(
                a.getId(), a.getPlanId(),
                a.getMaintenanceType() != null ? a.getMaintenanceType().name() : null,
                a.getVehicleId(), a.getVehicleRegistration(), a.getFleetId(),
                a.getStatus() != null ? a.getStatus().name() : null,
                a.getTriggerType() != null ? a.getTriggerType().name() : null,
                a.isUrgent(),
                a.getTargetKm(), a.getCurrentKm(), a.getKmRemaining(),
                a.getTargetDate(), a.getDaysRemaining(),
                a.getResolvedByMaintenanceId(), a.getResolvedAt(),
                a.getCreatedAt(), a.getUpdatedAt()
        );
    }
}
