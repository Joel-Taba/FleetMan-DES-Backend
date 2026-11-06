package com.yowyob.fleet.domain.ports.in;

import com.yowyob.fleet.domain.model.TripTracking.LiveTripPosition;
import com.yowyob.fleet.domain.model.TripTracking.TelemetryAck;
import com.yowyob.fleet.domain.model.TripTracking.TripPositions;
import java.util.UUID;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Suivi GPS en direct : le chauffeur partage sa position, le gestionnaire la suit sur la carte. */
public interface TrackTripUseCase {

    /** Point GPS du chauffeur pour SON trajet en cours (vitesse en km/h, précision en mètres). */
    Mono<TelemetryAck> sendTelemetry(UUID tripId, UUID actorId, Double lat, Double lng, Double speedKmh, Double accuracyM);

    /** Dernière position et tracé récent d'un trajet (gestionnaire de la flotte, ou chauffeur du trajet). */
    Mono<TripPositions> getPositions(UUID tripId, UUID actorId, Integer limit);

    /** Positions de tous les trajets en cours des flottes du gestionnaire. */
    Flux<LiveTripPosition> getLivePositions(UUID actorId, UUID fleetId);
}
