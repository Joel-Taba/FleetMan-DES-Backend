package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import com.yowyob.fleet.domain.model.Schedule;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Réponse planning")
public record ScheduleResponse(
        UUID id,
        UUID fleetId,
        UUID managerId,
        String title,
        String periodType,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        String notes,
        LocalDateTime createdAt,
        @Schema(description = "Postes à venir ou en cours")
        Long upcomingShifts,
        @Schema(description = "Postes à venir dont le chauffeur a accusé réception")
        Long acknowledgedShifts,
        @Schema(description = "Postes modifiés après publication et pas encore confirmés")
        Long changedUnacknowledged
) {
    public ScheduleResponse withAcks(long upcoming, long acknowledged, long changed) {
        return new ScheduleResponse(id, fleetId, managerId, title, periodType, startDate, endDate, status, notes, createdAt,
                upcoming, acknowledged, changed);
    }

    public static ScheduleResponse from(Schedule s) {
        return new ScheduleResponse(
                s.getId(),
                s.getFleetId(),
                s.getManagerId(),
                s.getTitle(),
                s.getPeriodType().name(),
                s.getStartDate(),
                s.getEndDate(),
                s.getStatus().name(),
                s.getNotes(),
                s.getCreatedAt(),
                null, null, null
        );
    }
}
