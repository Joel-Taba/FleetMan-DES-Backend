package com.yowyob.fleet.domain.ports.in;

import com.yowyob.fleet.domain.model.Trip;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * Port d'entrée pour la gestion des courses (Trips).
 */
public interface ManageTripUseCase {

    // --- 11a. OPÉRATIONS CHAUFFEUR ---
    /**
     * Démarre une course. Vérifie l'assignation et la disponibilité.
     */
    Mono<Trip> startTrip(UUID driverId, UUID vehicleId);

    /**
     * Enregistre un point GPS et vérifie les zones de Geofencing.
     */
    Mono<Void> sendTelemetry(UUID tripId, Double lat, Double lng, Double speed);

    /**
     * Termine la course, calcule la distance et libère les ressources.
     */
    Mono<Trip> endTrip(UUID tripId);

    /**
     * Récupère la course active du chauffeur connecté.
     */
    Mono<Trip> getMyActiveTrip(UUID driverId);

    /**
     * Récupère l'historique personnel du chauffeur.
     */
    Flux<Trip> getMyTripHistory(UUID driverId);


    // --- 11b. SUIVI MANAGER ---
    /**
     * Liste tous les trajets pour les flottes du manager.
     */
    Flux<Trip> getManagerTrips(UUID managerId, UUID optionalFleetId);

    /**
     * Récupère le détail d'un trajet spécifique.
     */
    Mono<Trip> getTripById(UUID tripId);
}