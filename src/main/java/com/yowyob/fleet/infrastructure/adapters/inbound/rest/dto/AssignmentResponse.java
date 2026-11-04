package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import com.yowyob.fleet.domain.model.Assignment;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Réponse affectation")
public record AssignmentResponse(
        UUID id,
        UUID scheduleId,
        UUID fleetId,
        UUID vehicleId,
        UUID driverId,
        LocalDateTime startDatetime,
        LocalDateTime endDatetime,
        String status,
        @Schema(description = "Kilomètres parcourus par les trajets du poste")
        BigDecimal actualKm,
        String notes,
        LocalDateTime createdAt,
        @Schema(description = "Trajets effectués ou prévus pendant le poste")
        long tripCount,
        @Schema(description = "Accusé de réception du chauffeur")
        LocalDateTime acknowledgedAt,
        @Schema(description = "Motif du dernier changement après publication")
        String changeReason,
        LocalDateTime changedAt,
        @Schema(description = "Motif d'un dépassement des règles de temps de travail")
        String workRuleOverride,
        String cancelReason
) {
    public static AssignmentResponse from(Assignment a) {
        return new AssignmentResponse(
                a.getId(),
                a.getScheduleId(),
                a.getFleetId(),
                a.getVehicleId(),
                a.getDriverId(),
                a.getStartDatetime(),
                a.getEndDatetime(),
                a.getStatus().name(),
                a.getActualKm(),
                a.getNotes(),
                a.getCreatedAt(),
                0, null, null, null, null, null
        );
    }

    public AssignmentResponse withTripCount(long count) {
        return new AssignmentResponse(id, scheduleId, fleetId, vehicleId, driverId, startDatetime, endDatetime,
                status, actualKm, notes, createdAt, count, acknowledgedAt, changeReason, changedAt, workRuleOverride, cancelReason);
    }

    public AssignmentResponse withTracking(LocalDateTime acknowledged, String change, LocalDateTime changed,
                                           String override, String cancel) {
        return new AssignmentResponse(id, scheduleId, fleetId, vehicleId, driverId, startDatetime, endDatetime,
                status, actualKm, notes, createdAt, tripCount, acknowledged, change, changed, override, cancel);
    }
}
