package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import com.yowyob.fleet.domain.model.MaintenancePlan;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

@Schema(description = "Requête de création d'un plan de maintenance préventive")
public record MaintenancePlanRequest(

        @NotNull(message = "Le type de maintenance est obligatoire.")
        @Schema(description = "Type d'intervention",
                allowableValues = {"OIL_CHANGE","TIRE_ROTATION","BRAKE_INSPECTION","FILTER_CHANGE",
                        "TIMING_BELT","COOLANT_FLUSH","TRANSMISSION_SERVICE","GENERAL_INSPECTION","OTHER"})
        MaintenancePlan.MaintenanceType maintenanceType,

        @NotNull(message = "La portée est obligatoire.")
        @Schema(description = "Portée du plan (FLEET = tous les véhicules, VEHICLE = un seul véhicule)",
                allowableValues = {"FLEET", "VEHICLE"})
        MaintenancePlan.PlanScope scope,

        @NotNull(message = "La flotte est obligatoire.")
        @Schema(description = "ID de la flotte ciblée")
        UUID fleetId,

        @Schema(description = "ID du véhicule (obligatoire si scope = VEHICLE)")
        UUID vehicleId,

        @Schema(description = "Libellé personnalisé du plan", example = "Vidange 10 000 km")
        String label,

        @Schema(description = "Description détaillée (optionnel)")
        String description,

        @Positive(message = "L'intervalle km doit être positif.")
        @Schema(description = "Intervalle kilométrique entre deux maintenances (ex: 10000 pour 10 000 km)",
                example = "10000")
        Integer intervalKm,

        @Positive(message = "L'intervalle jours doit être positif.")
        @Schema(description = "Intervalle temporel en jours (ex: 180 pour 6 mois)", example = "180")
        Integer intervalDays,

        @Positive
        @Schema(description = "Km de préalerte avant le seuil (ex: 500 km avant = alerte UPCOMING)",
                example = "500")
        Integer preAlertKm,

        @Positive
        @Schema(description = "Jours de préalerte avant le seuil (ex: 30 jours avant = alerte UPCOMING)",
                example = "30")
        Integer preAlertDays
) {}
