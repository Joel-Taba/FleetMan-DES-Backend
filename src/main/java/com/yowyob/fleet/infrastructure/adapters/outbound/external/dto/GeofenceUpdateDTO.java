package com.yowyob.fleet.infrastructure.adapters.outbound.external.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalTime;
import java.util.List;

/**
 * DTO spécifique pour la mise à jour partielle d'une zone.
 * Tous les champs sont optionnels.
 */
@JsonInclude(JsonInclude.Include.NON_NULL) // Important pour ne pas écraser avec des nulls
public record GeofenceUpdateDTO(
    
    @Schema(example = "Zone Bastos VIP")
    String title,

    @Schema(example = "Zone mise à jour")
    String description,

    @Schema(description = "Activer/Désactiver la zone", example = "true")
    Boolean isActive,

    // --- TEMPORAL ---
    
    @Schema(description = "Activer les restrictions horaires")
    Boolean isTemporalEnabled,

    @JsonFormat(pattern = "HH:mm:ss")
    @Schema(type = "string", pattern = "HH:mm:ss", example = "09:00:00")
    LocalTime startTime,

    @JsonFormat(pattern = "HH:mm:ss")
    @Schema(type = "string", pattern = "HH:mm:ss", example = "17:00:00")
    LocalTime endTime,

    @Schema(example = "[\"MONDAY\", \"FRIDAY\"]")
    List<String> activeDays,

    // --- CONDITIONAL ---

    @Schema(description = "Activer les conditions de vitesse/temps")
    Boolean isConditionalEnabled,

    @Schema(description = "Vitesse max autorisée (km/h)", example = "80.0")
    Double maxSpeed,

    @Schema(description = "Temps max de stationnement (minutes)", example = "30")
    Integer maxDwellTime,

    @Schema(description = "Temps min de présence pour déclencher (minutes)", example = "5")
    Integer minDwellTime,

    // --- GEOMETRY (Redéfinition) ---

    @Schema(description = "Nouveau rayon (si cercle)", example = "600.0")
    Double radius,

    @Schema(description = "Nouveau centre (si cercle)")
    GeofenceZoneDTORequest.CircleData center,

    @Schema(description = "Nouveau polygone (si polygone)")
    GeofenceZoneDTORequest.PolygonData polygon
) {}