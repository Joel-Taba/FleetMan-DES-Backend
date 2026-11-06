package com.yowyob.fleet.application.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Journal des relevés terrain saisis par le chauffeur (compteur, jauge) : le gestionnaire voit qui a
 * saisi quoi et quand, et il est alerté des anomalies : jauge très basse, saut de compteur
 * invraisemblable, relevé inférieur au dernier connu (refusé, mais signalé).
 */
@Service
@RequiredArgsConstructor
public class VehicleOperationalJournal {

    /** Jauge (en %) en dessous de laquelle le gestionnaire est prévenu. */
    public static final int LOW_FUEL_PCT = 15;
    /** Écart de compteur (km) au-delà duquel un relevé est signalé comme suspect. */
    public static final int ODOMETER_JUMP_KM = 1500;

    public static final String FUEL_LOW = "FUEL_LOW";
    public static final String ODOMETER_JUMP = "ODOMETER_JUMP";
    public static final String ODOMETER_DECREASE = "ODOMETER_DECREASE";

    private final DatabaseClient db;
    private final AppNotifier notifier;
    private final AccessScopeService accessScope;

    /** État avant la saisie (compteur en km, jauge « 62% »). */
    public record Snapshot(BigDecimal odometer, String fuel) {
        public static final Snapshot EMPTY = new Snapshot(null, null);
    }

    public record Entry(UUID id, UUID vehicleId, UUID authorId, String authorName, BigDecimal odometerBefore,
                        BigDecimal odometerAfter, String fuelBefore, String fuelAfter, boolean accepted,
                        List<String> anomalies, LocalDateTime createdAt) {}

    public Mono<Snapshot> snapshot(UUID vehicleId) {
        return db.sql("SELECT odometer_reading, fuel_level FROM fleet.operational_parameters WHERE vehicle_id = :v LIMIT 1")
                .bind("v", vehicleId)
                .map((row, meta) -> new Snapshot(row.get("odometer_reading", BigDecimal.class), row.get("fuel_level", String.class)))
                .one()
                .defaultIfEmpty(Snapshot.EMPTY);
    }

    /** Pourcentage d'une jauge (« 62% », « 62 ») ; null si illisible. */
    public static Integer fuelPct(String fuel) {
        if (fuel == null) return null;
        try {
            return new BigDecimal(fuel.replace("%", "").replace(',', '.').trim()).intValue();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Anomalies d'un relevé accepté : jauge basse (nouvelle baisse sous le seuil), saut de compteur. */
    public static List<String> anomalies(Snapshot before, Snapshot after) {
        List<String> out = new ArrayList<>();
        Integer pct = fuelPct(after.fuel());
        Integer previous = fuelPct(before.fuel());
        if (pct != null && pct <= LOW_FUEL_PCT && (previous == null || previous > LOW_FUEL_PCT || pct < previous)) {
            out.add(FUEL_LOW);
        }
        if (before.odometer() != null && after.odometer() != null
                && after.odometer().subtract(before.odometer()).compareTo(BigDecimal.valueOf(ODOMETER_JUMP_KM)) > 0) {
            out.add(ODOMETER_JUMP);
        }
        return out;
    }

    /** Relevé accepté : tracé, et le gestionnaire est alerté en cas d'anomalie. */
    public Mono<Void> recordAccepted(UUID vehicleId, UUID actorId, Snapshot before) {
        return snapshot(vehicleId).flatMap(after -> {
            boolean odometerChanged = after.odometer() != null && !after.odometer().equals(before.odometer());
            List<String> anomalies = anomalies(before, after);
            return insert(vehicleId, actorId, before, new Snapshot(odometerChanged ? after.odometer() : null, after.fuel()), true, anomalies)
                    .then(alert(vehicleId, actorId, anomalies, before, after));
        });
    }

    /** Compteur inférieur au dernier relevé : la saisie est refusée, mais tracée et signalée au gestionnaire. */
    public Mono<Void> recordRejected(UUID vehicleId, UUID actorId, Snapshot before, Map<String, Object> updates) {
        BigDecimal attempted;
        try {
            Object raw = updates.get("odometerReading");
            attempted = raw == null ? null : new BigDecimal(raw.toString().replace(',', '.').trim());
        } catch (NumberFormatException e) {
            return Mono.empty();
        }
        if (attempted == null || before.odometer() == null || attempted.compareTo(before.odometer()) >= 0) {
            return Mono.empty();
        }
        Snapshot after = new Snapshot(attempted, null);
        return insert(vehicleId, actorId, before, after, false, List.of(ODOMETER_DECREASE))
                .then(alert(vehicleId, actorId, List.of(ODOMETER_DECREASE), before, after))
                .onErrorResume(e -> Mono.empty());
    }

    public Flux<Entry> recent(UUID vehicleId, int limit) {
        return db.sql("""
                        SELECT j.*, TRIM(COALESCE(u.first_name, '') || ' ' || COALESCE(u.last_name, '')) AS author_name
                        FROM fleet.vehicle_operational_updates j LEFT JOIN fleet.users u ON u.id = j.author_id
                        WHERE j.vehicle_id = :v ORDER BY j.created_at DESC LIMIT :n
                        """)
                .bind("v", vehicleId)
                .bind("n", Math.max(1, Math.min(limit, 50)))
                .map((row, meta) -> {
                    String anomalies = row.get("anomalies", String.class);
                    String name = row.get("author_name", String.class);
                    return new Entry(row.get("id", UUID.class), row.get("vehicle_id", UUID.class), row.get("author_id", UUID.class),
                            name == null || name.isBlank() ? null : name,
                            row.get("odometer_before", BigDecimal.class), row.get("odometer_after", BigDecimal.class),
                            row.get("fuel_before", String.class), row.get("fuel_after", String.class),
                            Boolean.TRUE.equals(row.get("accepted", Boolean.class)),
                            anomalies == null || anomalies.isBlank() ? List.of() : List.of(anomalies.split(",")),
                            row.get("created_at", LocalDateTime.class));
                })
                .all();
    }

    private Mono<Void> insert(UUID vehicleId, UUID actorId, Snapshot before, Snapshot after, boolean accepted, List<String> anomalies) {
        return accessScope.resolveLocalUserId(actorId).defaultIfEmpty(new UUID(0, 0)).flatMap(author -> {
            var spec = db.sql("""
                            INSERT INTO fleet.vehicle_operational_updates
                                (id, vehicle_id, author_id, odometer_before, odometer_after, fuel_before, fuel_after, accepted, anomalies, created_at)
                            VALUES (:id, :v, :a, :ob, :oa, :fb, :fa, :ok, :an, :now)
                            """)
                    .bind("id", UUID.randomUUID()).bind("v", vehicleId).bind("a", author)
                    .bind("ok", accepted).bind("now", LocalDateTime.now());
            spec = bindOrNull(spec, "ob", before.odometer(), BigDecimal.class);
            spec = bindOrNull(spec, "oa", after.odometer(), BigDecimal.class);
            spec = bindOrNull(spec, "fb", before.fuel(), String.class);
            spec = bindOrNull(spec, "fa", after.fuel(), String.class);
            spec = bindOrNull(spec, "an", anomalies.isEmpty() ? null : String.join(",", anomalies), String.class);
            return spec.fetch().rowsUpdated().then();
        });
    }

    private static <T> DatabaseClient.GenericExecuteSpec bindOrNull(DatabaseClient.GenericExecuteSpec spec, String name, T value, Class<T> type) {
        return value != null ? spec.bind(name, value) : spec.bindNull(name, type);
    }

    private Mono<Void> alert(UUID vehicleId, UUID actorId, List<String> anomalies, Snapshot before, Snapshot after) {
        if (anomalies.isEmpty()) return Mono.empty();
        return Mono.zip(accessScope.resolveLocalUserId(actorId).defaultIfEmpty(new UUID(0, 0)), vehicleInfo(vehicleId))
                .flatMap(t -> {
                    UUID author = t.getT1();
                    String plate = t.getT2()[0];
                    UUID fleetId = t.getT2()[1] == null ? null : UUID.fromString(t.getT2()[1]);
                    return authorName(author).flatMap(who -> Flux.fromIterable(anomalies).concatMap(code -> switch (code) {
                        case FUEL_LOW -> notifier.toFleetManager(fleetId, author, "Carburant bas",
                                plate + " : jauge à " + after.fuel() + " d'après le relevé de " + who + ".",
                                AppNotifier.Level.WARNING, "VEHICLE_FUEL_LOW", AppNotifier.refs("vehicleId", vehicleId));
                        case ODOMETER_JUMP -> notifier.toFleetManager(fleetId, author, "Relevé kilométrique à vérifier",
                                plate + " : le compteur passe de " + km(before.odometer()) + " à " + km(after.odometer())
                                        + " km d'un coup (relevé de " + who + ").",
                                AppNotifier.Level.WARNING, "VEHICLE_ODOMETER_ANOMALY", AppNotifier.refs("vehicleId", vehicleId));
                        default -> notifier.toFleetManager(fleetId, author, "Relevé kilométrique refusé",
                                plate + " : " + who + " a saisi " + km(after.odometer()) + " km, moins que le dernier relevé ("
                                        + km(before.odometer()) + " km). Le compteur n'a pas été modifié.",
                                AppNotifier.Level.WARNING, "VEHICLE_ODOMETER_ANOMALY", AppNotifier.refs("vehicleId", vehicleId));
                    }).then());
                });
    }

    private static String km(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }

    private Mono<String[]> vehicleInfo(UUID vehicleId) {
        return db.sql("SELECT license_plate, fleet_id FROM fleet.vehicles WHERE id = :id")
                .bind("id", vehicleId)
                .map((row, meta) -> {
                    UUID fleet = row.get("fleet_id", UUID.class);
                    String plate = row.get("license_plate", String.class);
                    return new String[]{plate != null ? plate : "-", fleet != null ? fleet.toString() : null};
                })
                .one()
                .defaultIfEmpty(new String[]{"-", null});
    }

    private Mono<String> authorName(UUID userId) {
        return db.sql("SELECT TRIM(COALESCE(first_name, '') || ' ' || COALESCE(last_name, '')) AS n FROM fleet.users WHERE id = :id")
                .bind("id", userId)
                .map((row, meta) -> row.get("n", String.class))
                .one()
                .filter(n -> !n.isBlank())
                .defaultIfEmpty("le chauffeur");
    }
}
