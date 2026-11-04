package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.model.VehicleLifecycle;
import com.yowyob.fleet.domain.ports.out.AssignmentPersistencePort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.TripR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Véhicules et chauffeurs réellement engageables sur un créneau de trajet, pour ne proposer au
 * planning que des ressources libres : mêmes règles qu'à l'enregistrement (état, conformité,
 * absences, permis, trajets et postes qui se chevauchent). Un poste réserve le binôme : un
 * véhicule en poste n'est proposé qu'avec son chauffeur, et inversement.
 */
@Service
@RequiredArgsConstructor
public class TripAvailabilityService {

    public record VehicleOption(UUID id, String licensePlate, String brand, String model, UUID fleetId, String fleetName,
                                String status, String vehicleType, String requiredLicenseCategory, UUID reservedForDriverId) {}

    public record DriverOption(UUID id, String firstName, String lastName, String phone, UUID fleetId, String fleetName,
                               UUID reservedForVehicleId) {}

    public record Availability(List<VehicleOption> vehicles, List<DriverOption> drivers,
                               int unavailableVehicles, int unavailableDrivers) {}

    public record Query(LocalDateTime start, LocalDateTime end, UUID fleetId, UUID vehicleId, UUID driverId, UUID excludeTripId) {}

    private record Slot(boolean free, UUID reservedFor) {}

    private final DatabaseClient db;
    private final AccessScopeService accessScope;
    private final ResourceAvailabilityService availability;
    private final TripR2dbcRepository trips;
    private final AssignmentPersistencePort assignments;

    public Mono<Availability> find(UUID actorId, Query q) {
        return accessScope.resolveLocalUserId(actorId).flatMap(actor -> {
            Map<UUID, Boolean> operableFleets = new ConcurrentHashMap<>();
            Mono<List<VehicleOption>> allVehicles = vehicleCandidates(actor, q.fleetId()).collectList();
            Mono<List<DriverOption>> allDrivers = driverCandidates(actor, q.fleetId()).collectList();
            return Mono.zip(allVehicles, allDrivers).flatMap(t -> {
                Mono<List<VehicleOption>> vehicles = Flux.fromIterable(t.getT1())
                        .concatMap(v -> vehicleAvailable(v, q, operableFleets))
                        .collectList();
                Mono<List<DriverOption>> drivers = Flux.fromIterable(t.getT2())
                        .concatMap(d -> driverAvailable(d, q, operableFleets))
                        .collectList();
                return Mono.zip(vehicles, drivers).map(r -> new Availability(r.getT1(), r.getT2(),
                        t.getT1().size() - r.getT1().size(), t.getT2().size() - r.getT2().size()));
            });
        });
    }

    private Mono<VehicleOption> vehicleAvailable(VehicleOption v, Query q, Map<UUID, Boolean> operable) {
        if (!VehicleLifecycle.isDispatchable(v.status())) {
            return Mono.empty();
        }
        Mono<Boolean> checks = fleetOperable(v.fleetId(), operable)
                .flatMap(ok -> ok ? passes(availability.assertVehicleCompliant(v.id(), q.start(), q.end())) : Mono.just(false))
                .flatMap(ok -> ok ? trips.existsOverlappingTripForVehicle(v.id(), q.start(), q.end(), LocalDateTime.now(), q.excludeTripId())
                        .map(busy -> !Boolean.TRUE.equals(busy)) : Mono.just(false))
                .flatMap(ok -> ok && q.driverId() != null
                        ? passes(availability.assertDriverLicensed(q.driverId(), v.id(), q.start(), q.end()))
                        : Mono.just(ok));
        return checks.flatMap(ok -> ok ? vehicleSlot(v.id(), q) : Mono.just(new Slot(false, null)))
                .flatMap(slot -> {
                    if (!slot.free()) return Mono.<VehicleOption>empty();
                    if (q.driverId() != null && slot.reservedFor() != null && !slot.reservedFor().equals(q.driverId())) {
                        return Mono.<VehicleOption>empty();
                    }
                    return Mono.just(new VehicleOption(v.id(), v.licensePlate(), v.brand(), v.model(), v.fleetId(), v.fleetName(),
                            v.status(), v.vehicleType(), v.requiredLicenseCategory(), slot.reservedFor()));
                });
    }

    private Mono<DriverOption> driverAvailable(DriverOption d, Query q, Map<UUID, Boolean> operable) {
        LocalDate validOn = q.end().toLocalDate();
        Mono<Boolean> checks = fleetOperable(d.fleetId(), operable)
                .flatMap(ok -> ok ? passes(availability.assertDriverPresent(d.id(), q.start(), q.end())) : Mono.just(false))
                .flatMap(ok -> ok ? passes(availability.assertDriverFileCompliant(d.id(), q.start(), q.end())) : Mono.just(false))
                .flatMap(ok -> {
                    if (!ok) return Mono.just(false);
                    return q.vehicleId() != null
                            ? passes(availability.assertDriverLicensed(d.id(), q.vehicleId(), q.start(), q.end()))
                            : hasValidLicense(d.id(), validOn);
                })
                .flatMap(ok -> ok ? trips.existsOverlappingTripForDriver(d.id(), q.start(), q.end(), LocalDateTime.now(), q.excludeTripId())
                        .map(busy -> !Boolean.TRUE.equals(busy)) : Mono.just(false));
        return checks.flatMap(ok -> ok ? driverSlot(d.id(), q) : Mono.just(new Slot(false, null)))
                .flatMap(slot -> {
                    if (!slot.free()) return Mono.<DriverOption>empty();
                    if (q.vehicleId() != null && slot.reservedFor() != null && !slot.reservedFor().equals(q.vehicleId())) {
                        return Mono.<DriverOption>empty();
                    }
                    return Mono.just(new DriverOption(d.id(), d.firstName(), d.lastName(), d.phone(), d.fleetId(), d.fleetName(),
                            slot.reservedFor()));
                });
    }

    /** Postes qui chevauchent le créneau : libre, réservé à un seul partenaire, ou pris. */
    private Mono<Slot> vehicleSlot(UUID vehicleId, Query q) {
        return assignments.findConflictingByVehicle(vehicleId, q.start(), q.end(), null)
                .map(a -> a.getDriverId()).distinct().collectList()
                .map(partners -> partners.size() > 1 ? new Slot(false, null) : new Slot(true, partners.isEmpty() ? null : partners.get(0)));
    }

    private Mono<Slot> driverSlot(UUID driverId, Query q) {
        return assignments.findConflictingByDriver(driverId, q.start(), q.end(), null)
                .map(a -> a.getVehicleId()).distinct().collectList()
                .map(partners -> partners.size() > 1 ? new Slot(false, null) : new Slot(true, partners.isEmpty() ? null : partners.get(0)));
    }

    private Mono<Boolean> fleetOperable(UUID fleetId, Map<UUID, Boolean> cache) {
        if (fleetId == null) return Mono.just(false);
        Boolean known = cache.get(fleetId);
        if (known != null) return Mono.just(known);
        return passes(availability.assertFleetOperable(fleetId)).doOnNext(ok -> cache.put(fleetId, ok));
    }

    private Mono<Boolean> hasValidLicense(UUID driverId, LocalDate validOn) {
        return db.sql("""
                SELECT EXISTS (SELECT 1 FROM fleet.documents d WHERE d.target_type = 'DRIVER' AND d.target_id = :driver
                    AND d.doc_type = 'DRIVING_LICENSE' AND d.is_current AND d.review_status = 'APPROVED'
                    AND d.archived_at IS NULL AND (d.expiry_date IS NULL OR d.expiry_date >= :day)) AS ok
                """)
                .bind("driver", driverId).bind("day", validOn)
                .map((row, meta) -> Boolean.TRUE.equals(row.get("ok", Boolean.class)))
                .one()
                .defaultIfEmpty(false);
    }

    private static Mono<Boolean> passes(Mono<Void> check) {
        return check.thenReturn(true).onErrorReturn(false);
    }

    private static String scope(UUID fleetId) {
        return "(f.manager_id = :actor OR f.company_id = :actor) AND f.deleted_at IS NULL"
                + (fleetId != null ? " AND f.id = :fleet" : "");
    }

    private Flux<VehicleOption> vehicleCandidates(UUID actor, UUID fleetId) {
        DatabaseClient.GenericExecuteSpec spec = db.sql("""
                SELECT v.id, v.license_plate, v.brand, v.model, v.fleet_id, f.name AS fleet_name, v.status,
                       vt.label AS vehicle_type, vt.required_license_category
                FROM fleet.vehicles v
                JOIN fleet.fleets f ON f.id = v.fleet_id
                LEFT JOIN fleet.vehicle_types vt ON vt.id = v.vehicle_type_id
                WHERE v.deleted_at IS NULL AND %s
                ORDER BY v.license_plate
                """.formatted(scope(fleetId))).bind("actor", actor);
        if (fleetId != null) spec = spec.bind("fleet", fleetId);
        return spec.map((row, meta) -> new VehicleOption(row.get("id", UUID.class), row.get("license_plate", String.class),
                        row.get("brand", String.class), row.get("model", String.class), row.get("fleet_id", UUID.class),
                        row.get("fleet_name", String.class), row.get("status", String.class), row.get("vehicle_type", String.class),
                        row.get("required_license_category", String.class), null))
                .all();
    }

    private Flux<DriverOption> driverCandidates(UUID actor, UUID fleetId) {
        DatabaseClient.GenericExecuteSpec spec = db.sql("""
                SELECT d.user_id, u.first_name, u.last_name, u.phone, d.fleet_id, f.name AS fleet_name
                FROM fleet.drivers d
                JOIN fleet.fleets f ON f.id = d.fleet_id
                JOIN fleet.users u ON u.id = d.user_id AND u.deleted_at IS NULL
                WHERE d.deleted_at IS NULL AND d.status = 'ACTIVE' AND %s
                ORDER BY LOWER(u.last_name), LOWER(u.first_name)
                """.formatted(scope(fleetId))).bind("actor", actor);
        if (fleetId != null) spec = spec.bind("fleet", fleetId);
        return spec.map((row, meta) -> new DriverOption(row.get("user_id", UUID.class), row.get("first_name", String.class),
                        row.get("last_name", String.class), row.get("phone", String.class), row.get("fleet_id", UUID.class),
                        row.get("fleet_name", String.class), null))
                .all();
    }
}
