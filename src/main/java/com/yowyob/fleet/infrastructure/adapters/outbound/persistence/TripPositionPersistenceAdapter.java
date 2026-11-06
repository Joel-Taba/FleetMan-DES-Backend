package com.yowyob.fleet.infrastructure.adapters.outbound.persistence;

import com.yowyob.fleet.domain.model.TripTracking.LiveTripPosition;
import com.yowyob.fleet.domain.model.TripTracking.PositionPoint;
import com.yowyob.fleet.domain.ports.out.TripPositionPersistencePort;
import io.r2dbc.spi.Readable;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Positions GPS des trajets (migration 072) : table {@code fleet.trip_positions} et colonnes
 * {@code last_*} de {@code fleet.trips} / {@code fleet.vehicles}. Requêtes SQL directes : ces
 * colonnes ne sont pas mappées par les entités, qui ne peuvent donc pas les écraser.
 */
@Component
@RequiredArgsConstructor
public class TripPositionPersistenceAdapter implements TripPositionPersistencePort {

    private final DatabaseClient db;

    @Override
    public Mono<Instant> lastPositionAt(UUID tripId) {
        return db.sql("SELECT last_position_at FROM fleet.trips WHERE id = :tripId AND last_position_at IS NOT NULL")
            .bind("tripId", tripId)
            .map(row -> row.get("last_position_at", Instant.class))
            .one();
    }

    @Override
    public Mono<Boolean> record(UUID tripId, UUID vehicleId, UUID driverId, PositionPoint point, Double accuracyM,
                                Duration minInterval, int historyLimit) {
        // Mise à jour conditionnelle + insertion dans une seule instruction : deux envois
        // simultanés ne peuvent pas enregistrer deux points rapprochés.
        String sql = """
            WITH upd AS (
              UPDATE fleet.trips
                 SET last_lat = :lat, last_lng = :lng, last_speed = :speed, last_position_at = :at
               WHERE id = :tripId
                 AND (last_position_at IS NULL OR last_position_at <= :threshold)
              RETURNING id
            ), ins AS (
              INSERT INTO fleet.trip_positions (trip_id, vehicle_id, driver_id, latitude, longitude, speed_kmh, accuracy_m, recorded_at)
              SELECT id, :vehicleId, :driverId, :lat, :lng, :speed, :accuracy, :at FROM upd
              RETURNING id
            )
            SELECT COUNT(*) AS n FROM ins
            """;
        DatabaseClient.GenericExecuteSpec spec = db.sql(sql)
            .bind("tripId", tripId)
            .bind("lat", point.lat())
            .bind("lng", point.lng())
            .bind("at", point.at())
            .bind("threshold", point.at().minus(minInterval));
        spec = bindNullable(spec, "speed", point.speed(), Double.class);
        spec = bindNullable(spec, "accuracy", accuracyM, Double.class);
        spec = bindNullable(spec, "vehicleId", vehicleId, UUID.class);
        spec = bindNullable(spec, "driverId", driverId, UUID.class);
        return spec.map(row -> row.get("n", Long.class))
            .one()
            .defaultIfEmpty(0L)
            .flatMap(n -> n == null || n == 0
                ? Mono.just(false)
                : updateVehicle(tripId, vehicleId, point)
                    .then(prune(tripId, historyLimit))
                    .thenReturn(true));
    }

    private Mono<Void> updateVehicle(UUID tripId, UUID vehicleId, PositionPoint point) {
        if (vehicleId == null) return Mono.empty();
        DatabaseClient.GenericExecuteSpec spec = db.sql("""
                UPDATE fleet.vehicles
                   SET last_lat = :lat, last_lng = :lng, last_speed = :speed,
                       last_position_at = :at, last_position_trip_id = :tripId
                 WHERE id = :vehicleId
                   AND (last_position_at IS NULL OR last_position_at <= :at)
                """)
            .bind("vehicleId", vehicleId)
            .bind("tripId", tripId)
            .bind("lat", point.lat())
            .bind("lng", point.lng())
            .bind("at", point.at());
        return bindNullable(spec, "speed", point.speed(), Double.class).fetch().rowsUpdated().then();
    }

    private Mono<Void> prune(UUID tripId, int historyLimit) {
        return db.sql("""
                DELETE FROM fleet.trip_positions
                 WHERE trip_id = :tripId
                   AND id NOT IN (SELECT id FROM fleet.trip_positions WHERE trip_id = :tripId
                                  ORDER BY recorded_at DESC, id DESC LIMIT :keep)
                """)
            .bind("tripId", tripId)
            .bind("keep", historyLimit)
            .fetch().rowsUpdated().then();
    }

    @Override
    public Mono<PositionPoint> lastPosition(UUID tripId) {
        return db.sql("""
                SELECT last_lat, last_lng, last_speed, last_position_at FROM fleet.trips
                 WHERE id = :tripId AND last_lat IS NOT NULL AND last_lng IS NOT NULL AND last_position_at IS NOT NULL
                """)
            .bind("tripId", tripId)
            .map(row -> new PositionPoint(
                row.get("last_lat", Double.class),
                row.get("last_lng", Double.class),
                row.get("last_speed", Double.class),
                row.get("last_position_at", Instant.class)))
            .one();
    }

    @Override
    public Flux<PositionPoint> track(UUID tripId, int limit) {
        return db.sql("""
                SELECT latitude, longitude, speed_kmh, recorded_at FROM (
                  SELECT id, latitude, longitude, speed_kmh, recorded_at FROM fleet.trip_positions
                   WHERE trip_id = :tripId ORDER BY recorded_at DESC, id DESC LIMIT :limit
                ) recent ORDER BY recorded_at ASC, id ASC
                """)
            .bind("tripId", tripId)
            .bind("limit", limit)
            .map(TripPositionPersistenceAdapter::point)
            .all();
    }

    @Override
    public Flux<LiveTripPosition> liveTrips(UUID localUserId, UUID fleetId) {
        // Pré-filtre du périmètre (gestionnaire de la flotte, ou administrateur de l'organisation) ;
        // le service revérifie chaque flotte avec les règles d'accès communes.
        String sql = """
            SELECT t.id, t.trip_code, t.status, t.fleet_id, t.vehicle_id, t.driver_id, v.license_plate,
                   t.last_lat, t.last_lng, t.last_speed, t.last_position_at
              FROM fleet.trips t
              JOIN fleet.fleets f ON f.id = t.fleet_id AND f.deleted_at IS NULL
              LEFT JOIN fleet.vehicles v ON v.id = t.vehicle_id
             WHERE t.deleted_at IS NULL
               AND t.status IN ('DEPARTED', 'ARRIVED')
               AND (f.manager_id = :uid OR f.company_id = :uid OR f.created_by = :uid
                    OR EXISTS (SELECT 1 FROM fleet.fleet_managers m
                                WHERE m.user_id = f.manager_id AND m.created_by_admin_id = :uid))
            """ + (fleetId != null ? " AND t.fleet_id = :fleetId" : "") + """
             ORDER BY t.last_position_at DESC NULLS LAST, t.start_date DESC
            """;
        DatabaseClient.GenericExecuteSpec spec = db.sql(sql).bind("uid", localUserId);
        if (fleetId != null) spec = spec.bind("fleetId", fleetId);
        return spec.map(row -> {
                Double lat = row.get("last_lat", Double.class);
                Double lng = row.get("last_lng", Double.class);
                Instant at = row.get("last_position_at", Instant.class);
                PositionPoint last = lat != null && lng != null && at != null
                    ? new PositionPoint(lat, lng, row.get("last_speed", Double.class), at)
                    : null;
                return new LiveTripPosition(
                    row.get("id", UUID.class),
                    row.get("trip_code", String.class),
                    row.get("status", String.class),
                    row.get("fleet_id", UUID.class),
                    row.get("vehicle_id", UUID.class),
                    row.get("license_plate", String.class),
                    row.get("driver_id", UUID.class),
                    last,
                    List.of());
            })
            .all();
    }

    @Override
    public Mono<Map<UUID, List<PositionPoint>>> recentTracks(Collection<UUID> tripIds, int perTrip) {
        if (tripIds == null || tripIds.isEmpty()) return Mono.just(Map.of());
        return db.sql("""
                SELECT trip_id, latitude, longitude, speed_kmh, recorded_at FROM (
                  SELECT p.trip_id, p.latitude, p.longitude, p.speed_kmh, p.recorded_at, p.id,
                         ROW_NUMBER() OVER (PARTITION BY p.trip_id ORDER BY p.recorded_at DESC, p.id DESC) AS rn
                    FROM fleet.trip_positions p
                   WHERE p.trip_id IN (:ids)
                ) recent
                 WHERE rn <= :perTrip
                 ORDER BY trip_id, recorded_at ASC, id ASC
                """)
            .bind("ids", List.copyOf(tripIds))
            .bind("perTrip", perTrip)
            .map(row -> Map.entry(row.get("trip_id", UUID.class), point(row)))
            .all()
            .collect(LinkedHashMap<UUID, List<PositionPoint>>::new,
                (map, e) -> map.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue()))
            .map(m -> (Map<UUID, List<PositionPoint>>) m);
    }

    private static PositionPoint point(Readable row) {
        return new PositionPoint(
            row.get("latitude", Double.class),
            row.get("longitude", Double.class),
            row.get("speed_kmh", Double.class),
            row.get("recorded_at", Instant.class));
    }

    private static <T> DatabaseClient.GenericExecuteSpec bindNullable(
        DatabaseClient.GenericExecuteSpec spec, String name, T value, Class<T> type) {
        return value != null ? spec.bind(name, value) : spec.bindNull(name, type);
    }
}
