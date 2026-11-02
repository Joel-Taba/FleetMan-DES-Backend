package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.TripEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface TripR2dbcRepository
    extends ReactiveCrudRepository<TripEntity, UUID>
{
    Mono<TripEntity> findByDriverIdAndStatus(UUID driverId, String status);
    Mono<TripEntity> findByVehicleIdAndStatus(UUID vehicleId, String status);
    Mono<TripEntity> findByTripCode(String tripCode);

    Flux<TripEntity> findAllByCreatedBy(UUID createdBy);
    Flux<TripEntity> findAllByCreatedByAndFleetId(UUID createdBy, UUID fleetId);

    @Query("""
            SELECT t.* FROM fleet.trips t
            WHERE t.deleted_at IS NULL AND t.fleet_id = :fleetId
            ORDER BY t.start_date DESC NULLS LAST
            """)
    Flux<TripEntity> findAllByFleetId(UUID fleetId);

    @Query("""
            SELECT t.* FROM fleet.trips t
            WHERE t.deleted_at IS NULL
              AND t.fleet_id IN (
                SELECT f.id FROM fleet.fleets f
                WHERE f.manager_id = :managerId AND f.deleted_at IS NULL
              )
            ORDER BY t.start_date DESC NULLS LAST
            """)
    Flux<TripEntity> findAllByFleetManagerId(UUID managerId);

    @Query("""
            SELECT COUNT(*) FROM fleet.trips t
            WHERE t.deleted_at IS NULL
              AND t.fleet_id IN (
                SELECT f.id FROM fleet.fleets f
                WHERE f.manager_id = :managerId AND f.deleted_at IS NULL
              )
            """)
    Mono<Long> countByFleetManagerId(UUID managerId);
    Flux<TripEntity> findAllByDriverId(UUID driverId);

    @Query(
        "SELECT COUNT(*) > 0 FROM fleet.trips WHERE driver_id = :driverId " +
            "AND status IN ('SCHEDULED', 'DEPARTED', 'ARRIVED')"
    )
    Mono<Boolean> existsActiveTripForDriver(UUID driverId);

    /**
     * IDs des chauffeurs actuellement en course (départ enregistré, retour non enregistré).
     * Un trajet seulement planifié (SCHEDULED) ne met pas le chauffeur « en course ».
     */
    @Query(
        "SELECT DISTINCT driver_id FROM fleet.trips WHERE status IN ('DEPARTED', 'ARRIVED')"
    )
    Flux<UUID> findActiveTripDriverIds();

    // ── Créneaux horaires (détection des conflits) ───────────────────────────
    // Créneau d'un trajet non terminé : [départ prévu ou réel → retour prévu].
    // Sans retour prévu, le créneau reste ouvert. Un trajet en cours occupe ses
    // ressources au moins jusqu'à :now (retour en retard).

    String SLOT_START = "(t.start_date + COALESCE(t.start_time, TIME '00:00'))";
    String SLOT_END = "(CASE WHEN t.status IN ('DEPARTED', 'ARRIVED') "
            + "THEN GREATEST(COALESCE(t.scheduled_return_datetime, TIMESTAMP '9999-12-31 23:59:59'), :now) "
            + "ELSE COALESCE(t.scheduled_return_datetime, TIMESTAMP '9999-12-31 23:59:59') END)";
    String OPEN_TRIP = "t.deleted_at IS NULL AND t.status IN ('SCHEDULED', 'DEPARTED', 'ARRIVED') "
            + "AND (:excludeTripId IS NULL OR t.id <> :excludeTripId) ";

    @Query("SELECT COUNT(*) > 0 FROM fleet.trips t WHERE t.vehicle_id = :vehicleId AND " + OPEN_TRIP
            + "AND " + SLOT_START + " < :slotEnd AND " + SLOT_END + " > :slotStart")
    Mono<Boolean> existsOverlappingTripForVehicle(UUID vehicleId, LocalDateTime slotStart,
                                                  LocalDateTime slotEnd, LocalDateTime now,
                                                  UUID excludeTripId);

    @Query("SELECT COUNT(*) > 0 FROM fleet.trips t WHERE t.driver_id = :driverId AND " + OPEN_TRIP
            + "AND " + SLOT_START + " < :slotEnd AND " + SLOT_END + " > :slotStart")
    Mono<Boolean> existsOverlappingTripForDriver(UUID driverId, LocalDateTime slotStart,
                                                 LocalDateTime slotEnd, LocalDateTime now,
                                                 UUID excludeTripId);

    /** Trajets non terminés qui chevauchent le créneau et mobilisent ce véhicule OU ce chauffeur. */
    @Query("SELECT t.* FROM fleet.trips t WHERE (t.vehicle_id = :vehicleId OR t.driver_id = :driverId) AND "
            + OPEN_TRIP + "AND " + SLOT_START + " < :slotEnd AND " + SLOT_END + " > :slotStart")
    Flux<TripEntity> findOverlappingTripsForResources(UUID vehicleId, UUID driverId,
                                                      LocalDateTime slotStart, LocalDateTime slotEnd,
                                                      LocalDateTime now, UUID excludeTripId);

    /** Un trajet est-il physiquement en cours (départ enregistré, retour non enregistré) pour ce véhicule ? */
    @Query("SELECT COUNT(*) > 0 FROM fleet.trips t WHERE t.vehicle_id = :vehicleId AND t.deleted_at IS NULL "
            + "AND t.status IN ('DEPARTED', 'ARRIVED') AND (:excludeTripId IS NULL OR t.id <> :excludeTripId)")
    Mono<Boolean> existsInProgressTripForVehicle(UUID vehicleId, UUID excludeTripId);

    /** Trajets physiquement en cours (départ enregistré, retour non enregistré) d'un véhicule. */
    @Query("SELECT t.* FROM fleet.trips t WHERE t.vehicle_id = :vehicleId AND t.deleted_at IS NULL "
            + "AND t.status IN ('DEPARTED', 'ARRIVED')")
    Flux<TripEntity> findInProgressTripsForVehicle(UUID vehicleId);

    /** Trajets planifiés (pas encore partis) d'un véhicule dont le départ prévu n'est pas passé. */
    @Query("SELECT COUNT(*) FROM fleet.trips t WHERE t.vehicle_id = :vehicleId AND t.deleted_at IS NULL "
            + "AND t.status = 'SCHEDULED' AND (t.start_date + COALESCE(t.start_time, TIME '00:00')) >= :now")
    Mono<Long> countUpcomingScheduledTripsForVehicle(UUID vehicleId, LocalDateTime now);

    @Query("SELECT COUNT(*) > 0 FROM fleet.trips t WHERE t.driver_id = :driverId AND t.deleted_at IS NULL "
            + "AND t.status IN ('DEPARTED', 'ARRIVED') AND (:excludeTripId IS NULL OR t.id <> :excludeTripId)")
    Mono<Boolean> existsInProgressTripForDriver(UUID driverId, UUID excludeTripId);

    /** Trajet en cours du chauffeur (aller ou retour). */
    @Query("SELECT t.* FROM fleet.trips t WHERE t.driver_id = :driverId AND t.deleted_at IS NULL "
            + "AND t.status IN ('DEPARTED', 'ARRIVED') ORDER BY t.departure_registered_at DESC NULLS LAST LIMIT 1")
    Mono<TripEntity> findInProgressTripForDriver(UUID driverId);

    @Query(
        "SELECT COUNT(*) > 0 FROM fleet.trips WHERE driver_id = :driverId " +
            "AND status IN ('SCHEDULED', 'DEPARTED', 'ARRIVED') AND id != :excludeTripId"
    )
    Mono<Boolean> existsActiveTripForDriverExcluding(
        UUID driverId,
        UUID excludeTripId
    );

    @Query(
        "SELECT COUNT(*) > 0 FROM fleet.trips WHERE vehicle_id = :vehicleId " +
            "AND status IN ('SCHEDULED', 'DEPARTED', 'ARRIVED')"
    )
    Mono<Boolean> existsActiveTripForVehicle(UUID vehicleId);

    @Query(
        "SELECT COUNT(*) > 0 FROM fleet.trips WHERE vehicle_id = :vehicleId " +
            "AND status IN ('SCHEDULED', 'DEPARTED', 'ARRIVED') AND id != :excludeTripId"
    )
    Mono<Boolean> existsActiveTripForVehicleExcluding(
        UUID vehicleId,
        UUID excludeTripId
    );

    @Query(
        "SELECT * FROM fleet.trips WHERE created_by = :createdBy " +
            "AND status IN ('DEPARTED', 'ARRIVED')"
    )
    Flux<TripEntity> findOpenTripsByCreatedBy(UUID createdBy);

    // Même règle que les KPI : trajets terminés de la flotte du trajet (et non de la
    // flotte actuelle du véhicule, qui a pu changer depuis).
    @Query(
        "SELECT CAST(COALESCE(SUM(COALESCE(t.computed_distance_km, t.distance_km, 0)), 0) AS double precision) " +
            "FROM fleet.trips t " +
            "WHERE t.fleet_id = :fleetId AND t.status = 'COMPLETED' AND t.deleted_at IS NULL"
    )
    Mono<Double> getTotalDistanceByFleetId(UUID fleetId);

    @Query(
        "SELECT COALESCE(SUM(COALESCE(t.computed_distance_km, t.distance_km, 0)), 0) " +
            "FROM fleet.trips t " +
            "WHERE t.fleet_id = :fleetId " +
            "AND t.status = 'COMPLETED' " +
            "AND t.start_date >= :start AND t.start_date <= :end"
    )
    Mono<BigDecimal> sumDistanceByFleetAndPeriod(UUID fleetId, LocalDate start, LocalDate end);

    @Query(
        "SELECT COUNT(*) FROM fleet.trips t " +
            "WHERE t.fleet_id = :fleetId " +
            "AND t.status = 'COMPLETED' " +
            "AND t.start_date >= :start AND t.start_date <= :end"
    )
    Mono<Long> countTripsByFleetAndPeriod(UUID fleetId, LocalDate start, LocalDate end);

    @Query(
        "SELECT COALESCE(SUM(COALESCE(t.computed_distance_km, t.distance_km, 0)), 0) " +
            "FROM fleet.trips t " +
            "WHERE t.vehicle_id = :vehicleId " +
            "AND t.status = 'COMPLETED' " +
            "AND t.start_date >= :start AND t.start_date <= :end"
    )
    Mono<BigDecimal> sumDistanceByVehicleAndPeriod(UUID vehicleId, LocalDate start, LocalDate end);

    @Query(
        "SELECT COUNT(*) FROM fleet.trips t " +
            "WHERE t.vehicle_id = :vehicleId " +
            "AND t.status = 'COMPLETED' " +
            "AND t.start_date >= :start AND t.start_date <= :end"
    )
    Mono<Long> countTripsByVehicleAndPeriod(UUID vehicleId, LocalDate start, LocalDate end);

    @Query(
        "SELECT COALESCE(SUM(COALESCE(t.computed_distance_km, t.distance_km, 0)), 0) " +
            "FROM fleet.trips t " +
            "WHERE t.driver_id = :driverId " +
            "AND t.status = 'COMPLETED' " +
            "AND t.start_date >= :start AND t.start_date <= :end"
    )
    Mono<BigDecimal> sumDistanceByDriverAndPeriod(UUID driverId, LocalDate start, LocalDate end);

    @Query(
        "SELECT COUNT(*) FROM fleet.trips t " +
            "WHERE t.driver_id = :driverId " +
            "AND t.status = 'COMPLETED' " +
            "AND t.start_date >= :start AND t.start_date <= :end"
    )
    Mono<Long> countTripsByDriverAndPeriod(UUID driverId, LocalDate start, LocalDate end);

    @Query("""
            SELECT t.* FROM fleet.trips t
            WHERE t.deleted_at IS NULL
              AND (
                t.created_by IN (
                    SELECT fm.user_id FROM fleet.fleet_managers fm WHERE fm.created_by_admin_id = :adminId
                )
                OR t.fleet_id IN (
                    SELECT f.id FROM fleet.fleets f
                    JOIN fleet.fleet_managers fm ON f.manager_id = fm.user_id
                    WHERE fm.created_by_admin_id = :adminId
                )
              )
            ORDER BY t.start_date DESC NULLS LAST
            """)
    Flux<TripEntity> findAllByOrganizationAdminId(UUID adminId);

    @Query("""
            SELECT t.* FROM fleet.trips t
            WHERE t.deleted_at IS NULL
              AND t.fleet_id IN (
                SELECT f.id FROM fleet.fleets f
                WHERE f.manager_id = :managerId AND f.deleted_at IS NULL
              )
              AND EXISTS (
                SELECT 1 FROM fleet.fleet_managers fm
                WHERE fm.user_id = :managerId AND fm.created_by_admin_id = :adminId
              )
            ORDER BY t.start_date DESC NULLS LAST
            """)
    Flux<TripEntity> findAllByOrganizationAdminIdAndManagerId(UUID adminId, UUID managerId);

    @Query("""
            SELECT COUNT(*) FROM fleet.trips t
            WHERE t.created_by IN (
                SELECT fm.user_id FROM fleet.fleet_managers fm WHERE fm.created_by_admin_id = :adminId
            )
               OR t.fleet_id IN (
                SELECT f.id FROM fleet.fleets f
                JOIN fleet.fleet_managers fm ON f.manager_id = fm.user_id
                WHERE fm.created_by_admin_id = :adminId
            )
            """)
    Mono<Long> countByOrganizationAdminId(UUID adminId);

    @Query("""
            SELECT t.* FROM fleet.trips t
            WHERE t.deleted_at IS NULL
              AND t.status IN ('COMPLETED', 'CANCELLED')
              AND (
                t.created_by IN (
                    SELECT fm.user_id FROM fleet.fleet_managers fm WHERE fm.created_by_admin_id = :adminId
                )
                OR t.fleet_id IN (
                    SELECT f.id FROM fleet.fleets f
                    JOIN fleet.fleet_managers fm ON f.manager_id = fm.user_id
                    WHERE fm.created_by_admin_id = :adminId
                )
              )
            ORDER BY t.start_date DESC NULLS LAST
            """)
    Flux<TripEntity> findHistoryByOrganizationAdminId(UUID adminId);

    @Query("""
            SELECT t.* FROM fleet.trips t
            WHERE t.deleted_at IS NULL
              AND t.status IN ('COMPLETED', 'CANCELLED')
              AND t.fleet_id IN (
                SELECT f.id FROM fleet.fleets f
                WHERE f.manager_id = :managerId AND f.deleted_at IS NULL
              )
              AND EXISTS (
                SELECT 1 FROM fleet.fleet_managers fm
                WHERE fm.user_id = :managerId AND fm.created_by_admin_id = :adminId
              )
            ORDER BY t.start_date DESC NULLS LAST
            """)
    Flux<TripEntity> findHistoryByOrganizationAdminIdAndManagerId(UUID adminId, UUID managerId);

    @Query("""
            SELECT COALESCE(SUM(COALESCE(t.computed_distance_km, t.distance_km::numeric, 0)), 0)
            FROM fleet.trips t
            WHERE t.created_by IN (
                SELECT fm.user_id FROM fleet.fleet_managers fm WHERE fm.created_by_admin_id = :adminId
            )
               OR t.fleet_id IN (
                SELECT f.id FROM fleet.fleets f
                JOIN fleet.fleet_managers fm ON f.manager_id = fm.user_id
                WHERE fm.created_by_admin_id = :adminId
            )
            """)
    Mono<java.math.BigDecimal> sumDistanceByOrganizationAdminId(UUID adminId);
}
