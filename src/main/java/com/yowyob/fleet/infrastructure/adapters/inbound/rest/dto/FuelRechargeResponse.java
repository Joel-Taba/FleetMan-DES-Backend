package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import com.yowyob.fleet.domain.model.FuelRecharge;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Réponse représentant une recharge de carburant")
public record FuelRechargeResponse(

        @Schema(description = "Identifiant unique de la recharge")
        UUID id,

        @Schema(description = "Quantité rechargée en litres")
        BigDecimal quantity,

        @Schema(description = "Prix total payé en FCFA")
        BigDecimal price,

        @Schema(description = "Coût unitaire calculé (prix / litre) en FCFA")
        BigDecimal unitCost,

        @Schema(description = "Date et heure de la recharge")
        LocalDateTime rechargeDateTime,

        @Schema(description = "Nom de la station-service")
        String stationName,

        @Schema(description = "Localisation GPS de la station")
        MaintenanceResponse.LocationDto location,

        @Schema(description = "ID du véhicule rechargé")
        UUID vehicleId,

        @Schema(description = "Numéro d'immatriculation du véhicule")
        String vehicleRegistration,

        @Schema(description = "ID du chauffeur")
        UUID driverId,

        @Schema(description = "Nom complet du chauffeur")
        String driverFullName
) {

    /**
     * Méthode de fabrique : construit la réponse depuis le modèle domaine.
     * Calcule automatiquement le coût unitaire via la méthode métier de l'entité.
     */
    public static FuelRechargeResponse from(FuelRecharge f) {
        MaintenanceResponse.LocationDto location = null;
        if (f.getLocation() != null) {
            location = new MaintenanceResponse.LocationDto(
                    f.getLocation().longitude(),
                    f.getLocation().latitude(),
                    null
            );
        }

        return new FuelRechargeResponse(
                f.getId(),
                f.getQuantity(),
                f.getPrice(),
                f.unitCost(),   // méthode métier de l'entité domaine
                f.getRechargeDateTime(),
                f.getStationName() != null ? f.getStationName().name() : null,
                location,
                f.getVehicleId(),
                f.getVehicleRegistration(),
                f.getDriverId(),
                f.getDriverFullName()
        );
    }
}
