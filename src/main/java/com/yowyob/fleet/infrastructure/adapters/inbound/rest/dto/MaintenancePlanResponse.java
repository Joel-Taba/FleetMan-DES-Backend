package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import com.yowyob.fleet.domain.model.MaintenancePlan;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Réponse représentant un plan de maintenance préventive")
public record MaintenancePlanResponse(
        UUID id,
        String maintenanceType,
        String scope,
        UUID fleetId,
        UUID vehicleId,
        UUID managerId,
        String label,
        String description,
        Integer intervalKm,
        Integer intervalDays,
        Integer preAlertKm,
        Integer preAlertDays,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static MaintenancePlanResponse from(MaintenancePlan p) {
        return new MaintenancePlanResponse(
                p.getId(),
                p.getMaintenanceType() != null ? p.getMaintenanceType().name() : null,
                p.getScope() != null ? p.getScope().name() : null,
                p.getFleetId(), p.getVehicleId(), p.getManagerId(),
                p.getLabel(), p.getDescription(),
                p.getIntervalKm(), p.getIntervalDays(),
                p.getPreAlertKm(), p.getPreAlertDays(),
                p.isActive(), p.getCreatedAt(), p.getUpdatedAt()
        );
    }
}
