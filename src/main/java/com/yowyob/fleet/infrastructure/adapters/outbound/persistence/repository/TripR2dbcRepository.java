package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.TripEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
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
            "AND status IN ('SCHEDULED', 'DEPARTED', 'RETURNING')"
    )
    Mono<Boolean> existsActiveTripForDriver(UUID driverId);

    /** IDs des chauffeurs actuellement mobilisés par un trajet non terminé (pas de retour enregistré). */
    @Query(
        "SELECT DISTINCT driver_id FROM fleet.trips WHERE status IN ('SCHEDULED', 'DEPARTED', 'RETURNING')"
    )
    Flux<UUID> findActiveTripDriverIds();

    @Query(
        "SELECT COUNT(*) > 0 FROM fleet.trips WHERE driver_id = :driverId " +
            "AND status IN ('SCHEDULED', 'DEPARTED', 'RETURNING') AND id != :excludeTripId"
    )
    Mono<Boolean> existsActiveTripForDriverExcluding(
        UUID driverId,
        UUID excludeTripId
    );

    @Query(
        "SELECT COUNT(*) > 0 FROM fleet.trips WHERE vehicle_id = :vehicleId " +
            "AND status IN ('SCHEDULED', 'DEPARTED', 'RETURNING')"
    )
    Mono<Boolean> existsActiveTripForVehicle(UUID vehicleId);

    @Query(
        "SELECT COUNT(*) > 0 FROM fleet.trips WHERE vehicle_id = :vehicleId " +
            "AND status IN ('SCHEDULED', 'DEPARTED', 'RETURNING') AND id != :excludeTripId"
    )
    Mono<Boolean> existsActiveTripForVehicleExcluding(
        UUID vehicleId,
        UUID excludeTripId
    );

    @Query(
        "SELECT * FROM fleet.trips WHERE created_by = :createdBy " +
            "AND status IN ('DEPARTED', 'RETURNING')"
    )
    Flux<TripEntity> findOpenTripsByCreatedBy(UUID createdBy);

    @Query(
        "SELECT COALESCE(SUM(t.distance_km), 0) FROM fleet.trips t " +
            "JOIN fleet.vehicles v ON t.vehicle_id = v.id " +
            "WHERE v.fleet_id = :fleetId"
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
