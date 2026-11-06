package com.yowyob.fleet.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lectures transverses des opérations terrain (plein, maintenance, dépense) : qui conduit le
 * véhicule, qui gère la flotte, quel est l'état de la dépense générée… Utilisées par les
 * notifications entre rôles et par les listes du chauffeur et du gestionnaire.
 */
@Service
@RequiredArgsConstructor
public class OperationLookup {

    private final DatabaseClient db;
    private final AccessScopeService accessScope;

    /** Véhicule : flotte, immatriculation, entreprise (= administrateur), statut courant. */
    public record VehicleRef(UUID id, UUID fleetId, String plate, UUID companyId, String status) {}

    /** État de la dépense générée par une opération (plein, maintenance). */
    public record ExpenseBrief(UUID id, String status, String rejectionReason, String approvalLevel,
                               LocalDateTime validatedAt, BigDecimal amount) {}

    public Mono<UUID> localUser(UUID id) {
        return accessScope.resolveLocalUserId(id);
    }

    public Mono<VehicleRef> vehicle(UUID vehicleId) {
        if (vehicleId == null) return Mono.empty();
        return db.sql("""
                        SELECT v.id, v.fleet_id, v.license_plate, v.status, f.company_id
                        FROM fleet.vehicles v LEFT JOIN fleet.fleets f ON f.id = v.fleet_id
                        WHERE v.id = :v
                        """)
                .bind("v", vehicleId)
                .map((row, meta) -> new VehicleRef(
                        row.get("id", UUID.class),
                        row.get("fleet_id", UUID.class),
                        row.get("license_plate", String.class),
                        row.get("company_id", UUID.class),
                        row.get("status", String.class)))
                .one();
    }

    /** « Prénom Nom » d'un utilisateur local, vide s'il est inconnu. */
    public Mono<String> fullName(UUID userId) {
        if (userId == null) return Mono.just("");
        return db.sql("""
                        SELECT TRIM(COALESCE(first_name, '') || ' ' || COALESCE(last_name, '')) AS n, username
                        FROM fleet.users WHERE id = :u
                        """)
                .bind("u", userId)
                .map((row, meta) -> {
                    String n = row.get("n", String.class);
                    return n != null && !n.isBlank() ? n : String.valueOf(row.get("username", String.class));
                })
                .one()
                .defaultIfEmpty("");
    }

    public Mono<Boolean> isDriver(UUID userId) {
        if (userId == null) return Mono.just(false);
        return db.sql("SELECT EXISTS (SELECT 1 FROM fleet.drivers WHERE user_id = :u AND deleted_at IS NULL) AS d")
                .bind("u", userId)
                .map((row, meta) -> Boolean.TRUE.equals(row.get("d", Boolean.class)))
                .one()
                .defaultIfEmpty(false);
    }

    /** Chauffeurs qui conduisent ce véhicule : chauffeur attitré et chauffeur courant. */
    public Flux<UUID> vehicleDrivers(UUID vehicleId) {
        if (vehicleId == null) return Flux.empty();
        return db.sql("""
                        SELECT user_id AS id FROM fleet.drivers WHERE assigned_vehicle_id = :v AND deleted_at IS NULL
                        UNION
                        SELECT current_driver_id AS id FROM fleet.vehicles WHERE id = :v AND current_driver_id IS NOT NULL
                        """)
                .bind("v", vehicleId)
                .map((row, meta) -> row.get("id", UUID.class))
                .all();
    }

    /** Véhicule attitré du chauffeur (null s'il n'en a pas). */
    public Mono<UUID> assignedVehicle(UUID driverId) {
        if (driverId == null) return Mono.empty();
        return db.sql("SELECT assigned_vehicle_id FROM fleet.drivers WHERE user_id = :u AND assigned_vehicle_id IS NOT NULL")
                .bind("u", driverId)
                .map((row, meta) -> row.get("assigned_vehicle_id", UUID.class))
                .one();
    }

    public Mono<ExpenseBrief> autoExpense(UUID sourceId) {
        if (sourceId == null) return Mono.empty();
        return autoExpenses(List.of(sourceId)).mapNotNull(map -> map.get(sourceId));
    }

    /** Dépenses AUTO rattachées à ces opérations, par identifiant d'opération. */
    public Mono<Map<UUID, ExpenseBrief>> autoExpenses(Collection<UUID> sourceIds) {
        if (sourceIds == null || sourceIds.isEmpty()) return Mono.just(Map.of());
        return db.sql("""
                        SELECT source_id, id, status, rejection_reason, approval_level, validated_at, amount
                        FROM fleet.expenses
                        WHERE source_type = 'AUTO' AND source_id = ANY(:ids)
                        """)
                .bind("ids", sourceIds.toArray(new UUID[0]))
                .map((row, meta) -> Map.entry(row.get("source_id", UUID.class), new ExpenseBrief(
                        row.get("id", UUID.class),
                        row.get("status", String.class),
                        row.get("rejection_reason", String.class),
                        row.get("approval_level", String.class),
                        row.get("validated_at", LocalDateTime.class),
                        row.get("amount", BigDecimal.class))))
                .all()
                .collectMap(Map.Entry::getKey, Map.Entry::getValue, HashMap::new)
                .map(m -> (Map<UUID, ExpenseBrief>) m);
    }

    // ── Effets d'une maintenance sur la disponibilité du véhicule ───────────

    /** Le véhicule disponible entre à l'atelier : statut MAINTENANCE (un véhicule immobilisé le reste). */
    public Mono<Void> vehicleToWorkshop(UUID vehicleId, String reason) {
        return db.sql("""
                        UPDATE fleet.vehicles SET status = 'MAINTENANCE', status_reason = :reason, status_changed_at = :now
                        WHERE id = :v AND status = 'AVAILABLE'
                        """)
                .bind("v", vehicleId)
                .bind("reason", reason)
                .bind("now", LocalDateTime.now())
                .fetch().rowsUpdated().then();
    }

    /**
     * Fin d'atelier : le véhicule redevient disponible, sauf si une autre intervention est en
     * cours ou si un incident grave le retient immobilisé.
     */
    public Mono<Boolean> releaseVehicleFromWorkshop(UUID vehicleId, UUID maintenanceId) {
        return db.sql("""
                        UPDATE fleet.vehicles SET status = 'AVAILABLE', status_reason = NULL, status_changed_at = :now
                        WHERE id = :v AND status = 'MAINTENANCE'
                          AND NOT EXISTS (SELECT 1 FROM fleet.maintenances m
                                          WHERE m.vehicle_id = :v AND m.status = 'IN_PROGRESS' AND m.id <> :m)
                          AND NOT EXISTS (SELECT 1 FROM fleet.incidents i
                                          WHERE i.vehicle_id = :v AND i.vehicle_immobilized = TRUE)
                        """)
                .bind("v", vehicleId)
                .bind("m", maintenanceId)
                .bind("now", LocalDateTime.now())
                .fetch().rowsUpdated()
                .map(n -> n > 0);
    }
}
