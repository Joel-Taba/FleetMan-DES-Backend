package com.yowyob.fleet.infrastructure.adapters.outbound.persistence;

import com.yowyob.fleet.domain.ports.out.StatisticsPort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.SuperAdminDashboardStatsResponse.SignupTrendPoint;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.SuperAdminDashboardStatsResponse.UserTypeSlice;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.DriverR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.FleetManagerR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.FleetR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.UserLocalR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.VehicleLocalR2dbcRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class StatisticsPersistenceAdapter implements StatisticsPort {

    private static final ZoneId ZONE = ZoneId.of("Africa/Douala");
    private static final DateTimeFormatter DAY_LABEL =
            DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH);

    private final FleetManagerR2dbcRepository managerRepo;
    private final FleetR2dbcRepository fleetRepo;
    private final VehicleLocalR2dbcRepository vehicleRepo;
    private final DriverR2dbcRepository driverRepo;
    private final UserLocalR2dbcRepository userRepo;
    private final DatabaseClient db;

    @Override
    public Mono<Long> countFleetManagers() {
        return managerRepo.count();
    }

    @Override
    public Mono<Long> countFleets() {
        return fleetRepo.count();
    }

    @Override
    public Mono<Long> countVehicles() {
        return vehicleRepo.count();
    }

    @Override
    public Mono<Long> countDrivers() {
        return driverRepo.count();
    }

    @Override
    public Mono<Long> countActiveAdmins() {
        return countActiveAdminsSince(Instant.EPOCH);
    }

    @Override
    public Mono<Long> countFleetManagersSince(Instant since) {
        return countSince("""
                SELECT COUNT(DISTINCT fm.user_id) AS cnt
                FROM fleet.fleet_managers fm
                JOIN fleet.users u ON u.id = fm.user_id
                WHERE u.deleted_at IS NULL
                  AND u.is_active = true
                  AND COALESCE(u.last_login_at, u.approved_at, NOW()) >= :since
                """, since);
    }

    @Override
    public Mono<Long> countFleetsSince(Instant since) {
        return countSince("""
                SELECT COUNT(*) AS cnt
                FROM fleet.fleets f
                WHERE f.deleted_at IS NULL
                  AND f.created_at >= :since
                """, since);
    }

    @Override
    public Mono<Long> countVehiclesSince(Instant since) {
        return countSince("""
                SELECT COUNT(*) AS cnt
                FROM fleet.vehicles v
                WHERE v.deleted_at IS NULL
                  AND v.created_at >= :since
                """, since);
    }

    @Override
    public Mono<Long> countDriversSince(Instant since) {
        return countSince("""
                SELECT COUNT(*) AS cnt
                FROM fleet.drivers d
                WHERE d.deleted_at IS NULL
                  AND d.created_at >= :since
                """, since);
    }

    @Override
    public Mono<Long> countActiveAdminsSince(Instant since) {
        return countSince("""
                SELECT COUNT(*) AS cnt
                FROM fleet.users u
                WHERE u.is_active = true
                  AND u.deleted_at IS NULL
                  AND (
                    u.email LIKE '%admin%@fleetman.cm'
                    OR u.email LIKE 'admin%@%'
                  )
                  AND COALESCE(u.last_login_at, u.approved_at, NOW()) >= :since
                """, since);
    }

    @Override
    public Mono<List<SignupTrendPoint>> signupTrendSince(Instant since) {
        return db.sql("""
                SELECT DATE(COALESCE(u.last_login_at, u.approved_at)) AS day, COUNT(*) AS cnt
                FROM fleet.users u
                WHERE u.deleted_at IS NULL
                  AND COALESCE(u.last_login_at, u.approved_at) IS NOT NULL
                  AND COALESCE(u.last_login_at, u.approved_at) >= :since
                GROUP BY DATE(COALESCE(u.last_login_at, u.approved_at))
                ORDER BY day ASC
                """)
                .bind("since", since)
                .map((row, meta) -> {
                    Object day = row.get("day");
                    Object cnt = row.get("cnt");
                    String label = day != null
                            ? day.toString()
                            : "?";
                    if (day instanceof java.time.LocalDate ld) {
                        label = ld.atStartOfDay(ZONE).format(DAY_LABEL);
                    }
                    long count = cnt instanceof Number n ? n.longValue() : 0L;
                    return new SignupTrendPoint(label, count);
                })
                .all()
                .collectList()
                .defaultIfEmpty(List.of());
    }

    @Override
    public Mono<List<UserTypeSlice>> userTypeDistributionSince(Instant since) {
        return Mono.zip(
                        countActiveAdminsSince(since),
                        countFleetManagersSince(since),
                        countDriversSince(since)
                )
                .map(t -> List.of(
                        new UserTypeSlice("Admin", t.getT1(), "#2696e4"),
                        new UserTypeSlice("Manager", t.getT2(), "#10B981"),
                        new UserTypeSlice("Driver", t.getT3(), "#F59E0B")
                ));
    }

    private Mono<Long> countSince(String sql, Instant since) {
        return db.sql(sql)
                .bind("since", since)
                .map((row, meta) -> {
                    Object v = row.get("cnt");
                    if (v instanceof Number n) return n.longValue();
                    return 0L;
                })
                .one()
                .defaultIfEmpty(0L);
    }
}
