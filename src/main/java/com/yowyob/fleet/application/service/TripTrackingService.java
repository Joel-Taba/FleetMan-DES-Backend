package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.TripException;
import com.yowyob.fleet.domain.model.TripTracking;
import com.yowyob.fleet.domain.model.TripTracking.LiveTripPosition;
import com.yowyob.fleet.domain.model.TripTracking.PositionPoint;
import com.yowyob.fleet.domain.model.TripTracking.TelemetryAck;
import com.yowyob.fleet.domain.model.TripTracking.TripPositions;
import com.yowyob.fleet.domain.ports.in.TrackTripUseCase;
import com.yowyob.fleet.domain.ports.out.TripPositionPersistencePort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.RedisTelemetryAdapter;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.TripEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.VehicleLocalEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.OperationalParameterR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.TripR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.VehicleLocalR2dbcRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Suivi GPS en direct des trajets.
 *
 * <p>Le téléphone du chauffeur envoie un point toutes les ~30 s pendant un trajet en cours. Le point
 * est validé, limité (un point par 15 s et par trajet, les autres sont ignorés sans erreur) et
 * enregistré durablement en base (historique borné + dernière position du trajet et du véhicule).
 * Redis et la vitesse instantanée du véhicule sont mis à jour en plus, sans jamais faire échouer
 * l'envoi : l'application fonctionne sans Redis.
 */
@Service
public class TripTrackingService implements TrackTripUseCase {

    private static final Logger log = LoggerFactory.getLogger(TripTrackingService.class);
    private static final Duration SIDE_EFFECT_TIMEOUT = Duration.ofSeconds(2);

    private final TripR2dbcRepository tripRepository;
    private final VehicleLocalR2dbcRepository vehicleRepository;
    private final OperationalParameterR2dbcRepository operationalRepo;
    private final TripPositionPersistencePort positions;
    private final RedisTelemetryAdapter redisTelemetry;
    private final AccessScopeService accessScope;
    private final Clock clock;

    @Autowired
    public TripTrackingService(
        TripR2dbcRepository tripRepository,
        VehicleLocalR2dbcRepository vehicleRepository,
        OperationalParameterR2dbcRepository operationalRepo,
        TripPositionPersistencePort positions,
        RedisTelemetryAdapter redisTelemetry,
        AccessScopeService accessScope
    ) {
        this(tripRepository, vehicleRepository, operationalRepo, positions, redisTelemetry, accessScope, Clock.systemUTC());
    }

    TripTrackingService(
        TripR2dbcRepository tripRepository,
        VehicleLocalR2dbcRepository vehicleRepository,
        OperationalParameterR2dbcRepository operationalRepo,
        TripPositionPersistencePort positions,
        RedisTelemetryAdapter redisTelemetry,
        AccessScopeService accessScope,
        Clock clock
    ) {
        this.tripRepository = tripRepository;
        this.vehicleRepository = vehicleRepository;
        this.operationalRepo = operationalRepo;
        this.positions = positions;
        this.redisTelemetry = redisTelemetry;
        this.accessScope = accessScope;
        this.clock = clock;
    }

    // ── Envoi d'un point (chauffeur) ──────────────────────────────────────────

    @Override
    public Mono<TelemetryAck> sendTelemetry(UUID tripId, UUID actorId, Double lat, Double lng, Double speedKmh, Double accuracyM) {
        if (!TripTracking.validCoordinates(lat, lng)) {
            return Mono.error(TripException.invalidPosition());
        }
        return tripRepository.findById(tripId)
            .switchIfEmpty(Mono.error(TripException.notFound(tripId)))
            .flatMap(trip -> assertTripDriver(actorId, trip).thenReturn(trip))
            .flatMap(trip -> {
                if (!isInProgress(trip.getStatus())) {
                    return Mono.error(TripException.trackingNotActive());
                }
                Instant now = clock.instant();
                PositionPoint point = new PositionPoint(lat, lng, TripTracking.sanitizeSpeed(speedKmh), now);
                return positions.lastPositionAt(tripId)
                    .map(last -> TripTracking.tooSoon(last, now)
                        ? Mono.just(new TelemetryAck(false, TripTracking.TOO_SOON, last, TripTracking.retryInSeconds(last, now)))
                        : store(trip, point, accuracyM))
                    .defaultIfEmpty(Mono.defer(() -> store(trip, point, accuracyM)))
                    .flatMap(m -> m);
            });
    }

    private Mono<TelemetryAck> store(TripEntity trip, PositionPoint point, Double accuracyM) {
        return positions.record(trip.getId(), trip.getVehicleId(), trip.getDriverId(), point,
                TripTracking.sanitizeAccuracy(accuracyM), TripTracking.MIN_INTERVAL, TripTracking.HISTORY_LIMIT)
            .defaultIfEmpty(false)
            .flatMap(recorded -> {
                if (!recorded) {
                    // Un autre envoi simultané vient d'être enregistré : celui-ci est ignoré.
                    return Mono.just(new TelemetryAck(false, TripTracking.TOO_SOON, point.at(),
                        TripTracking.MIN_INTERVAL.toSeconds()));
                }
                return sideEffects(trip, point)
                    .thenReturn(new TelemetryAck(true, TripTracking.RECORDED, point.at(), TripTracking.MIN_INTERVAL.toSeconds()));
            });
    }

    /** Redis (tracé temps réel historique) et vitesse instantanée du véhicule : au mieux, jamais bloquant. */
    private Mono<Void> sideEffects(TripEntity trip, PositionPoint point) {
        try {
            redisTelemetry.addPoint(trip.getId(), point.lat(), point.lng())
                .timeout(SIDE_EFFECT_TIMEOUT)
                .subscribe(
                    v -> {},
                    e -> log.debug("Redis indisponible, point GPS gardé en base seulement : {}", e.getMessage()));
        } catch (RuntimeException e) {
            log.debug("Redis indisponible : {}", e.getMessage());
        }
        if (trip.getVehicleId() == null) return Mono.empty();
        return operationalRepo.findByVehicleId(trip.getVehicleId())
            .flatMap(op -> {
                op.setCurrentSpeed(BigDecimal.valueOf(point.speed() != null ? point.speed() : 0.0));
                op.setTimestamp(point.at());
                return operationalRepo.save(op);
            })
            .timeout(SIDE_EFFECT_TIMEOUT)
            .then()
            .onErrorResume(e -> {
                log.debug("Vitesse du véhicule non mise à jour : {}", e.getMessage());
                return Mono.empty();
            });
    }

    // ── Lecture (gestionnaire) ────────────────────────────────────────────────

    @Override
    public Mono<TripPositions> getPositions(UUID tripId, UUID actorId, Integer limit) {
        int size = limit == null || limit <= 0 ? TripTracking.DEFAULT_TRACK : Math.min(limit, TripTracking.HISTORY_LIMIT);
        return tripRepository.findById(tripId)
            .switchIfEmpty(Mono.error(TripException.notFound(tripId)))
            .flatMap(trip -> assertDriverOrFleet(actorId, trip).thenReturn(trip))
            .flatMap(trip -> Mono.zip(
                    plate(trip.getVehicleId()),
                    positions.lastPosition(tripId).map(List::of).defaultIfEmpty(List.of()),
                    positions.track(tripId, size).collectList())
                .map(t -> new TripPositions(
                    trip.getId(),
                    trip.getTripCode(),
                    trip.getStatus(),
                    trip.getVehicleId(),
                    t.getT1().isEmpty() ? null : t.getT1(),
                    t.getT2().isEmpty() ? null : t.getT2().get(0),
                    t.getT3())));
    }

    @Override
    public Flux<LiveTripPosition> getLivePositions(UUID actorId, UUID fleetId) {
        Mono<Void> fleetCheck = fleetId != null ? accessScope.assertTripFleetAccess(actorId, fleetId) : Mono.empty();
        return fleetCheck
            .then(accessScope.resolveLocalUserId(actorId).defaultIfEmpty(actorId))
            .flatMapMany(localId -> positions.liveTrips(localId, fleetId).collectList()
                .flatMapMany(rows -> {
                    Set<UUID> fleets = rows.stream().map(LiveTripPosition::fleetId).collect(Collectors.toSet());
                    // Revérifie chaque flotte avec les règles d'accès communes (gestionnaire, admin de l'organisation).
                    return Flux.fromIterable(fleets)
                        .flatMap(f -> accessScope.assertTripFleetAccess(actorId, f).thenReturn(f)
                            .onErrorResume(e -> Mono.empty()))
                        .collect(Collectors.toSet())
                        .flatMapMany(allowed -> {
                            List<LiveTripPosition> visible = rows.stream()
                                .filter(r -> allowed.contains(r.fleetId()))
                                .toList();
                            List<UUID> ids = visible.stream().map(LiveTripPosition::tripId).toList();
                            return positions.recentTracks(ids, TripTracking.LIVE_TRACK)
                                .flatMapMany(tracks -> Flux.fromIterable(visible)
                                    .map(r -> r.withTrack(tracks.getOrDefault(r.tripId(), List.of()))));
                        });
                }));
    }

    // ── Accès ─────────────────────────────────────────────────────────────────

    /**
     * Seul le chauffeur du trajet partage sa position. L'identifiant du jeton peut être l'id Kernel :
     * il est ramené à l'id local, comme pour les arrêts de la tournée.
     */
    private Mono<Void> assertTripDriver(UUID actorId, TripEntity trip) {
        if (actorId == null || trip.getDriverId() == null) return Mono.error(TripException.forbidden());
        if (actorId.equals(trip.getDriverId())) return Mono.empty();
        return accessScope.resolveLocalUserId(actorId)
            .defaultIfEmpty(actorId)
            .flatMap(localId -> localId.equals(trip.getDriverId())
                ? Mono.<Void>empty()
                : Mono.<Void>error(TripException.forbidden()));
    }

    /** Le chauffeur du trajet, ou un gestionnaire ayant accès à sa flotte. */
    private Mono<Void> assertDriverOrFleet(UUID actorId, TripEntity trip) {
        return assertTripDriver(actorId, trip)
            .onErrorResume(TripException.class, e -> accessScope.assertTripFleetAccess(actorId, trip.getFleetId()));
    }

    private Mono<String> plate(UUID vehicleId) {
        if (vehicleId == null) return Mono.just("");
        return vehicleRepository.findById(vehicleId)
            .map(VehicleLocalEntity::getLicensePlate)
            .defaultIfEmpty("")
            .onErrorReturn("");
    }

    private static boolean isInProgress(String status) {
        return "DEPARTED".equals(status) || "ARRIVED".equals(status);
    }
}
