package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import com.yowyob.fleet.domain.model.Mission;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Requête de création ou planification d'une mission")
public record MissionRequest(

        @NotNull(message = "L'identifiant du véhicule est obligatoire.")
        @Schema(description = "Identifiant du véhicule affecté", example = "123e4567-e89b-12d3-a456-426614174000")
        UUID vehicleId,

        @NotNull(message = "L'identifiant du conducteur est obligatoire.")
        @Schema(description = "Identifiant du conducteur affecté", example = "456e7890-e89b-12d3-a456-426614174001")
        UUID driverId,

        @Schema(description = "Type de mission (défaut : DELIVERY)", example = "DELIVERY",
                allowableValues = {"DELIVERY", "PICKUP", "TRANSFER", "ROUND_TRIP"})
        Mission.Type type,

        @NotNull(message = "La date de départ planifiée est obligatoire.")
        @Schema(description = "Date et heure prévues de départ", example = "2026-11-15T08:00:00")
        LocalDateTime scheduledStart,

        @Schema(description = "Date et heure prévues d'arrivée", example = "2026-11-15T12:00:00")
        LocalDateTime scheduledEnd,

        @Schema(description = "Identifiant du point de départ", example = "a1b2c3d4-e89b-12d3-a456-426614174000")
        UUID startLocationId,

        @Schema(description = "Identifiant du point d'arrivée", example = "f5g6h7i8-e89b-12d3-a456-426614174000")
        UUID endLocationId,

        @Schema(description = "Identifiant de la livraison correspondante chez TiiBnTick (G1)", example = "b7c8d9e0-e89b-12d3-a456-426614174000")
        UUID tiiBnTickDeliveryId,

        @Schema(description = "Code ou QR code du colis TiiBnTick", example = "QR-YOW-2026-9874")
        String tiiBnTickParcelId,

        @Schema(description = "Identifiant de l'objet suivi dans Geofence (G3)", example = "GEO-OBJ-4590")
        String geofenceObjectId,

        @Schema(description = "Instructions ou notes particulières", example = "Livraison prioritaire de denrées périssables.")
        String notes
) {}

