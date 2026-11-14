package com.yowyob.fleet.domain.model.tour;

import java.util.UUID;

/**
 * Représente une demande de livraison unitaire avec contraintes de capacité et de fenêtres horaires.
 * Périmètre G4 : 20 livraisons par instance d'optimisation.
 */
public record DeliveryDemand(
        UUID id,
        String parcelId,
        String recipientName,
        double latitude,
        double longitude,
        double weightKg,
        double volumeM3,
        int windowStartMinutes, // Début fenêtre horaire (minutes depuis 00:00)
        int windowEndMinutes,   // Fin fenêtre horaire
        int serviceDurationMinutes // Temps de déchargement/signature (ex: 10 min)
) {
    public DeliveryDemand {
        if (id == null) {
            throw new IllegalArgumentException("L'identifiant de la demande est obligatoire.");
        }
        if (weightKg < 0 || volumeM3 < 0) {
            throw new IllegalArgumentException("Le poids et le volume doivent être positifs.");
        }
        if (windowStartMinutes < 0 || windowEndMinutes < windowStartMinutes) {
            throw new IllegalArgumentException("Fenêtre horaire invalide.");
        }
    }
}

