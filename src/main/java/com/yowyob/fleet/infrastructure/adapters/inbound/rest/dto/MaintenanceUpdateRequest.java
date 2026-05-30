package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

public record MaintenanceUpdateRequest(
    @Schema(description = "Date de la dernière maintenance effectuée", example = "2025-12-01")
    LocalDate lastMaintenanceDate,

    @Schema(description = "Date de la prochaine maintenance prévue", example = "2026-06-01")
    LocalDate nextMaintenanceDue,

    @Schema(
        description = "État technique du moteur", 
        example = "OK", 
        allowableValues = {"OK", "NEEDS_SERVICE", "OUT_OF_SERVICE"}
    )
    String engineStatus,

    @Schema(description = "Santé de la batterie (0 à 100)", example = "98")
    Integer batteryHealth,

    @Schema(
        description = "Statut administratif de la maintenance", 
        example = "UP_TO_DATE", 
        allowableValues = {"UP_TO_DATE", "PENDING", "OVERDUE"}
    )
    String maintenanceStatus
) {}