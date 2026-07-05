package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Requête de création ou mise à jour d'un planning")
public record ScheduleRequest(

        @NotNull(message = "La flotte est obligatoire")
        @Schema(description = "ID de la flotte", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID fleetId,

        @NotBlank(message = "Le titre est obligatoire")
        @Schema(description = "Titre du planning", example = "Planning semaine 22 — Flotte Yaoundé")
        String title,

        @NotBlank(message = "Le type de période est obligatoire")
        @Schema(description = "Type de période", allowableValues = {"DAILY","WEEKLY","MONTHLY"},
                example = "WEEKLY")
        String periodType,

        @NotNull(message = "La date de début est obligatoire")
        @Schema(description = "Date de début", example = "2026-06-02")
        LocalDate startDate,

        @NotNull(message = "La date de fin est obligatoire")
        @Schema(description = "Date de fin", example = "2026-06-08")
        LocalDate endDate,

        @Schema(description = "Notes ou instructions particulières")
        String notes
) {}
