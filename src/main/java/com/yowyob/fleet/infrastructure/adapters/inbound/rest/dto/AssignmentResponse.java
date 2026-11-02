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
        long tripCount
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
                0
        );
    }

    public AssignmentResponse withTripCount(long count) {
        return new AssignmentResponse(id, scheduleId, fleetId, vehicleId, driverId, startDatetime, endDatetime,
                status, actualKm, notes, createdAt, count);
    }
}
