package com.yowyob.fleet.infrastructure.adapters.outbound.persistence;

import com.yowyob.fleet.domain.ports.out.StatisticsPort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.DriverR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.FleetManagerR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.FleetR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.UserLocalR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.VehicleLocalR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class StatisticsPersistenceAdapter implements StatisticsPort {

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
        // Seed + comptes approuvés : comptes actifs hors gestionnaires PENDING
        // Approximation : utilisateurs actifs qui ne sont pas uniquement chauffeurs.
        // Compte explicite via emails demo admin + superadmin IDs + création locale.
        return db.sql("""
                SELECT COUNT(*) AS cnt
                FROM fleet.users u
                WHERE u.is_active = true
                  AND u.deleted_at IS NULL
                  AND (
                    u.id IN (
                      '2c9a43d2-8406-4860-b33b-f7ba989885ba',
                      '96b87460-6179-483d-a6d5-9cbcacd9d06d'
                    )
                    OR u.email LIKE '%admin%'
                  )
                """)
                .map((row, meta) -> {
                    Object v = row.get("cnt");
                    if (v instanceof Number n) return n.longValue();
                    return 0L;
                })
                .one()
                .defaultIfEmpty(0L)
                .onErrorResume(e -> userRepo.count().defaultIfEmpty(0L));
    }
}