package com.yowyob.fleet.infrastructure.adapters.outbound.external.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

public record GeofenceZoneDTORequest (
    @Schema(example = "123e4567-e89b-12d3-a456-426614174000")
    @NotNull UUID fleetManagerId, // Identifiant du FleetManager qui crée la zone
 
    @Schema(example = "POLYGON", allowableValues = {"CIRCLE", "POLYGON"})
    @NotBlank String type,

    @Schema(example = "Zone Bastos Sécurité")
    @NotBlank String title,

    @Schema(example = "Surveillance du quartier résidentiel")
    String description,

    @JsonProperty("isTemporalEnabled")
    @Schema(description = "Active la restriction horaire", example = "true")
    Boolean isTemporalEnabled,

    @JsonProperty("isConditionalEnabled")
    @Schema(description = "Active les conditions de vitesse/temps", example = "false")
    Boolean isConditionalEnabled,

    @JsonFormat(pattern = "HH:mm:ss")
    @Schema(type = "string", pattern = "HH:mm:ss", example = "08:00:00")
    LocalTime startTime,

    @JsonFormat(pattern = "HH:mm:ss")
    @Schema(type = "string", pattern = "HH:mm:ss", example = "18:00:00")
    LocalTime endTime,

    @Schema(description = "Données pour un cercle (obligatoire si type=CIRCLE)")
    CircleData center,

    @Schema(description = "Rayon en mètres (si type=CIRCLE)", example = "500")
    Double radius,

    @Schema(description = "Données pour un polygone (obligatoire si type=POLYGON)")
    PolygonData polygon
) {
    public record CircleData(
        @Schema(description = "Coordonnées [longitude, latitude]", example = "[11.5021, 3.8480]")
        List<Double> coordinates
    ) {}

    public record PolygonData(
        @Schema(example = "Polygon")
        String type,
        
        @Schema(description = "Liste de anneaux de coordonnées. Le premier est le contour extérieur.", 
                example = "[[[11.50, 3.84], [11.51, 3.84], [11.51, 3.85], [11.50, 3.85], [11.50, 3.84]]]")
        List<List<List<Double>>> coordinates
    ) {}
}