package com.yowyob.fleet.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Véhicules qu'un chauffeur conduit aujourd'hui : son véhicule habituel, celui de son
 * trajet en cours et ceux de ses postes du jour. Mêmes règles que
 * {@link ResourceAccessService#assertDriverOperatesVehicle} : le chauffeur peut lire
 * (et exporter) les documents de chacun de ces véhicules.
 */
@Service
@RequiredArgsConstructor
public class DriverVehicleService {

    public static final String REGULAR = "REGULAR";
    public static final String TRIP = "TRIP";
    public static final String SHIFT = "SHIFT";

    private final DatabaseClient db;
    private final AccessScopeService accessScope;

    public record DriverVehicle(UUID vehicleId, String licensePlate, String label, List<String> reasons) {}

    /** Ligne brute : un véhicule et une raison (un véhicule peut revenir plusieurs fois). */
    record Row(UUID vehicleId, String licensePlate, String brand, String model, String typeLabel, String reason) {}

    public Flux<DriverVehicle> myVehicles(UUID actorId) {
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        return accessScope.resolveLocalUserId(actorId)
                .flatMapMany(driverId -> db.sql("""
                                WITH src AS (
                                    SELECT d.assigned_vehicle_id AS vehicle_id, 'REGULAR' AS reason, 1 AS ord
                                    FROM fleet.drivers d
                                    WHERE d.user_id = :d AND d.assigned_vehicle_id IS NOT NULL
                                    UNION ALL
                                    SELECT t.vehicle_id, 'TRIP', 2 FROM (
                                        SELECT vehicle_id FROM fleet.trips
                                        WHERE driver_id = :d AND deleted_at IS NULL
                                          AND status IN ('DEPARTED', 'ARRIVED')
                                        ORDER BY departure_registered_at DESC NULLS LAST LIMIT 1
                                    ) t WHERE t.vehicle_id IS NOT NULL
                                    UNION ALL
                                    SELECT a.vehicle_id, 'SHIFT', 3
                                    FROM fleet.assignments a
                                    WHERE a.driver_id = :d
                                      AND a.status IN ('PENDING', 'IN_PROGRESS')
                                      AND (a.status = 'IN_PROGRESS'
                                           OR (a.start_datetime < :dayEnd AND a.end_datetime > :dayStart))
                                )
                                SELECT v.id, v.license_plate, v.brand, v.model, vt.label AS type_label, s.reason
                                FROM src s
                                JOIN fleet.vehicles v ON v.id = s.vehicle_id AND v.deleted_at IS NULL
                                LEFT JOIN fleet.vehicle_types vt ON vt.id = v.vehicle_type_id
                                ORDER BY s.ord, v.license_plate
                                """)
                        .bind("d", driverId)
                        .bind("dayStart", dayStart)
                        .bind("dayEnd", dayStart.plusDays(1))
                        .map((row, meta) -> new Row(
                                row.get("id", UUID.class),
                                row.get("license_plate", String.class),
                                row.get("brand", String.class),
                                row.get("model", String.class),
                                row.get("type_label", String.class),
                                row.get("reason", String.class)))
                        .all()
                        .collectList()
                        .flatMapIterable(DriverVehicleService::group));
    }

    /** Une entrée par véhicule, dans l'ordre de première apparition (habituel d'abord), raisons sans doublon. */
    static List<DriverVehicle> group(List<Row> rows) {
        Map<UUID, Row> first = new LinkedHashMap<>();
        Map<UUID, List<String>> reasons = new LinkedHashMap<>();
        for (Row r : rows) {
            first.putIfAbsent(r.vehicleId(), r);
            List<String> list = reasons.computeIfAbsent(r.vehicleId(), k -> new ArrayList<>());
            if (!list.contains(r.reason())) list.add(r.reason());
        }
        List<DriverVehicle> out = new ArrayList<>();
        first.forEach((id, r) -> out.add(new DriverVehicle(id, r.licensePlate(), label(r), List.copyOf(reasons.get(id)))));
        return out;
    }

    static String label(Row r) {
        String bm = (blankToEmpty(r.brand()) + " " + blankToEmpty(r.model())).trim();
        if (!bm.isEmpty()) return bm;
        return blankToEmpty(r.typeLabel()).trim();
    }

    private static String blankToEmpty(String s) {
        return s == null ? "" : s.trim();
    }
}
