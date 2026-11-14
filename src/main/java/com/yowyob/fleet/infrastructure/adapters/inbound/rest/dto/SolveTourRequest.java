package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import com.yowyob.fleet.domain.model.tour.DeliveryDemand;
import com.yowyob.fleet.domain.model.tour.Depot;
import com.yowyob.fleet.domain.model.tour.VehicleCapacity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "Données d'entrée pour la résolution et l'optimisation de tournées (VRP-TW)")
public record SolveTourRequest(

        @Schema(description = "Date prévue d'exécution de la tournée", example = "2026-11-15")
        LocalDate planningDate,

        @NotEmpty(message = "Au moins un dépôt / antenne est requis.")
        @Schema(description = "Liste des dépôts / antennes de rattachement (Périmètre G4 : 2 antennes)")
        List<Depot> depots,

        @NotEmpty(message = "Au moins un véhicule est requis.")
        @Schema(description = "Liste des véhicules disponibles et leurs capacités (Périmètre G4 : 4 véhicules)")
        List<VehicleCapacity> vehicles,

        @NotEmpty(message = "Au moins une demande de livraison est requise.")
        @Schema(description = "Liste des livraisons à planifier avec fenêtres et colis (Périmètre G4 : 20 livraisons)")
        List<DeliveryDemand> demands,

        @Schema(description = "Autoriser les retards pénalisés (false = fenêtres horaires strictes)", example = "true")
        boolean allowPenalizedLateArrival
) {}

