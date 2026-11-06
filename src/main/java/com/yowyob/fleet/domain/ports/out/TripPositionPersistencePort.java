package com.yowyob.fleet.domain.ports.out;

import com.yowyob.fleet.domain.model.TripTracking.LiveTripPosition;
import com.yowyob.fleet.domain.model.TripTracking.PositionPoint;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Positions GPS des trajets : historique borné et dernière position connue (trajet et véhicule). */
public interface TripPositionPersistencePort {

    /** Horodatage du dernier point enregistré pour ce trajet (vide si aucun). */
    Mono<Instant> lastPositionAt(UUID tripId);

    /**
     * Enregistre le point si le précédent date d'au moins {@code minInterval} (contrôle atomique) :
     * historique, dernière position du trajet et du véhicule, purge au-delà de la limite.
     * Renvoie false si le point a été ignoré.
     */
    Mono<Boolean> record(UUID tripId, UUID vehicleId, UUID driverId, PositionPoint point, Double accuracyM,
                         Duration minInterval, int historyLimit);

    /** Dernière position connue du trajet (vide si jamais partagée). */
    Mono<PositionPoint> lastPosition(UUID tripId);

    /** Les {@code limit} derniers points du trajet, du plus ancien au plus récent. */
    Flux<PositionPoint> track(UUID tripId, int limit);

    /**
     * Trajets en cours (partis ou arrivés) des flottes que l'utilisateur gère ou administre,
     * avec leur dernière position, sans tracé ({@code fleetId} : une seule flotte).
     */
    Flux<LiveTripPosition> liveTrips(UUID localUserId, UUID fleetId);

    /** Les {@code perTrip} derniers points de chaque trajet, du plus ancien au plus récent. */
    Mono<Map<UUID, List<PositionPoint>>> recentTracks(Collection<UUID> tripIds, int perTrip);
}
