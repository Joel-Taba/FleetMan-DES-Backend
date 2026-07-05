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
        UUID missionId,
        LocalDateTime startDatetime,
        LocalDateTime endDatetime,
        String status,
        String startLocation,
        String endLocation,
        BigDecimal estimatedKm,
        BigDecimal actualKm,
        String notes,
        LocalDateTime createdAt
) {
    public static AssignmentResponse from(Assignment a) {
        return new AssignmentResponse(
                a.getId(),
                a.getScheduleId(),
                a.getFleetId(),
                a.getVehicleId(),
                a.getDriverId(),
                a.getMissionId(),
                a.getStartDatetime(),
                a.getEndDatetime(),
                a.getStatus().name(),
                a.getStartLocation(),
                a.getEndLocation(),
                a.getEstimatedKm(),
                a.getActualKm(),
                a.getNotes(),
                a.getCreatedAt()
        );
    }
}
