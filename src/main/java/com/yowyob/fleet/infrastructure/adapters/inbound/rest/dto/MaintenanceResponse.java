package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import com.yowyob.fleet.domain.model.Maintenance;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Réponse représentant une maintenance véhicule")
public record MaintenanceResponse(

        @Schema(description = "Identifiant unique de la maintenance")
        UUID id,

        @Schema(description = "Objet / titre de l'intervention")
        String subject,

        @Schema(description = "Coût de l'intervention en FCFA")
        BigDecimal cost,

        @Schema(description = "Date et heure de l'intervention")
        LocalDateTime dateTime,

        @Schema(description = "Rapport détaillé de l'intervention")
        String report,

        @Schema(description = "Localisation GPS de l'intervention")
        LocationDto location,

        @Schema(description = "ID du véhicule concerné")
        UUID vehicleId,

        @Schema(description = "Numéro d'immatriculation du véhicule")
        String vehicleRegistration,

        @Schema(description = "ID du chauffeur impliqué")
        UUID driverId,

        @Schema(description = "Nom complet du chauffeur")
        String driverFullName
) {

    /**
     * DTO imbriqué pour la localisation GPS.
     */
    public record LocationDto(Double longitude, Double latitude, String locationName) {}

    /**
     * Méthode de fabrique : construit la réponse depuis le modèle domaine.
     */
    public static MaintenanceResponse from(Maintenance m) {
        LocationDto location = null;
        if (m.getLocation() != null) {
            location = new LocationDto(
                    m.getLocation().longitude(),
                    m.getLocation().latitude(),
                    m.getLocationName()
            );
        } else if (m.getLocationName() != null) {
            location = new LocationDto(null, null, m.getLocationName());
        }

        return new MaintenanceResponse(
                m.getId(),
                m.getSubject(),
                m.getCost(),
                m.getDateTime(),
                m.getReport(),
                location,
                m.getVehicleId(),
                m.getVehicleRegistrationNumber(),
                m.getDriverId(),
                m.getDriverFullName()
        );
    }
}
