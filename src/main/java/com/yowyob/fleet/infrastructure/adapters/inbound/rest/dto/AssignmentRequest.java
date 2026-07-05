package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Requête de création d'une affectation véhicule-conducteur")
public record AssignmentRequest(

        @Schema(description = "ID du planning parent (optionnel)")
        UUID scheduleId,

        @NotNull(message = "La flotte est obligatoire")
        @Schema(description = "ID de la flotte")
        UUID fleetId,

        @NotNull(message = "Le véhicule est obligatoire")
        @Schema(description = "ID du véhicule")
        UUID vehicleId,

        @NotNull(message = "Le conducteur est obligatoire")
        @Schema(description = "ID du conducteur")
        UUID driverId,

        @Schema(description = "ID de la mission associée (optionnel)")
        UUID missionId,

        @NotNull(message = "La date/heure de début est obligatoire")
        @Schema(description = "Début de l'affectation", example = "2026-06-02T08:00:00")
        LocalDateTime startDatetime,

        @NotNull(message = "La date/heure de fin est obligatoire")
        @Schema(description = "Fin de l'affectation", example = "2026-06-02T18:00:00")
        LocalDateTime endDatetime,

        @Schema(description = "Lieu de départ", example = "Dépôt Central Yaoundé")
        String startLocation,

        @Schema(description = "Lieu d'arrivée", example = "Aéroport NSIMALEN")
        String endLocation,

        @Schema(description = "Kilométrage estimé", example = "45.5")
        BigDecimal estimatedKm,

        @Schema(description = "Notes ou instructions")
        String notes
) {}
