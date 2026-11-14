package com.yowyob.fleet.domain.model.tour;

import java.util.UUID;

/**
 * Représente un dépôt ou une antenne de rattachement pour les véhicules.
 * Conforme au problème multi-dépôts (2 antennes) du cahier des charges G4.
 */
public record Depot(
        UUID id,
        String name,
        double latitude,
        double longitude,
        int openingTimeMinutes,  // Minutes depuis 00:00 (ex: 480 pour 08:00)
        int closingTimeMinutes   // Minutes depuis 00:00 (ex: 1080 pour 18:00)
) {
    public Depot {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Le nom du dépôt est obligatoire.");
        }
        if (openingTimeMinutes < 0 || closingTimeMinutes <= openingTimeMinutes) {
            throw new IllegalArgumentException("Plage horaire d'ouverture du dépôt invalide.");
        }
    }
}

