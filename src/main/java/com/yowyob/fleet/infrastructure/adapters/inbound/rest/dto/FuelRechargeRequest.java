package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import com.yowyob.fleet.domain.model.FuelRecharge;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Requête d'enregistrement d'une recharge de carburant")
public record FuelRechargeRequest(

        @NotNull(message = "La quantité de carburant est obligatoire.")
        @Positive(message = "La quantité doit être strictement positive.")
        @Schema(description = "Quantité rechargée en litres", example = "50.00")
        BigDecimal quantity,

        @NotNull(message = "Le prix est obligatoire.")
        @PositiveOrZero(message = "Le prix ne peut pas être négatif.")
        @Schema(description = "Prix total payé en FCFA", example = "37500.00")
        BigDecimal price,

        @NotNull(message = "Le véhicule est obligatoire.")
        @Schema(description = "ID du véhicule rechargé", example = "123e4567-e89b-12d3-a456-426614174000")
        UUID vehicleId,

        @Schema(description = "ID du chauffeur ayant effectué la recharge (optionnel)", example = "456e7890-e89b-12d3-a456-426614174001")
        UUID driverId,

        @Schema(description = "Nom de la station-service (optionnel)", example = "TOTAL",
                allowableValues = {"TOTAL", "SHELL", "OILIBYA", "CAMRAIL", "OTHER"})
        FuelRecharge.StationName stationName,

        @Schema(description = "Longitude GPS de la station (optionnel)", example = "11.5021")
        Double longitude,

        @Schema(description = "Latitude GPS de la station (optionnel)", example = "3.8480")
        Double latitude
) {}
