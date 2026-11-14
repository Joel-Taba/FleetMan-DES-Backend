package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import com.yowyob.fleet.domain.model.Mission;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Réponse détaillée pour une mission de transport / livraison")
public record MissionResponse(
        @Schema(description = "Identifiant unique de la mission")
        UUID id,

        @Schema(description = "Identifiant du véhicule")
        UUID vehicleId,

        @Schema(description = "Identifiant du conducteur")
        UUID driverId,

        @Schema(description = "Identifiant du gestionnaire de flotte")
        UUID fleetManagerId,

        @Schema(description = "Identifiant de livraison TiiBnTick (G1)")
        UUID tiiBnTickDeliveryId,

        @Schema(description = "QR Code / Identifiant colis TiiBnTick")
        String tiiBnTickParcelId,

        @Schema(description = "Statut courant de la mission", example = "PENDING")
        Mission.Status status,

        @Schema(description = "Type de mission", example = "DELIVERY")
        Mission.Type type,

        @Schema(description = "Date/heure de départ planifiée")
        LocalDateTime scheduledStart,

        @Schema(description = "Date/heure de fin planifiée")
        LocalDateTime scheduledEnd,

        @Schema(description = "Date/heure de départ effective")
        LocalDateTime actualStart,

        @Schema(description = "Date/heure de fin effective")
        LocalDateTime actualEnd,

        @Schema(description = "Durée réelle en minutes")
        Long durationMinutes,

        @Schema(description = "Point de départ")
        UUID startLocationId,

        @Schema(description = "Point d'arrivée")
        UUID endLocationId,

        @Schema(description = "Identifiant d'objet Geofence (G3)")
        String geofenceObjectId,

        @Schema(description = "Notes de mission")
        String notes,

        @Schema(description = "Motif d'annulation si applicable")
        String cancelReason,

        @Schema(description = "Date de création")
        LocalDateTime createdAt
) {
    public static MissionResponse fromDomain(Mission m) {
        return new MissionResponse(
                m.getId(),
                m.getVehicleId(),
                m.getDriverId(),
                m.getFleetManagerId(),
                m.getTiiBnTickDeliveryId(),
                m.getTiiBnTickParcelId(),
                m.getStatus(),
                m.getType(),
                m.getScheduledStart(),
                m.getScheduledEnd(),
                m.getActualStart(),
                m.getActualEnd(),
                m.getDurationMinutes(),
                m.getStartLocationId(),
                m.getEndLocationId(),
                m.getGeofenceObjectId(),
                m.getNotes(),
                m.getCancelReason(),
                m.getCreatedAt()
        );
    }
}

