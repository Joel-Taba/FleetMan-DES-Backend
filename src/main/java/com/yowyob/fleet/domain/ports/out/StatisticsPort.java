package com.yowyob.fleet.domain.ports.out;

import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.SuperAdminDashboardStatsResponse.SignupTrendPoint;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.SuperAdminDashboardStatsResponse.UserTypeSlice;
import java.time.Instant;
import java.util.List;
import reactor.core.publisher.Mono;

public interface StatisticsPort {
    Mono<Long> countFleetManagers();
    Mono<Long> countFleets();
    Mono<Long> countVehicles();
    Mono<Long> countDrivers();
    /** Comptes locaux actifs avec rôle admin (approx plateform). */
    Mono<Long> countActiveAdmins();

    Mono<Long> countFleetManagersSince(Instant since);
    Mono<Long> countFleetsSince(Instant since);
    Mono<Long> countVehiclesSince(Instant since);
    Mono<Long> countDriversSince(Instant since);
    Mono<Long> countActiveAdminsSince(Instant since);
    Mono<List<SignupTrendPoint>> signupTrendSince(Instant since);
    Mono<List<UserTypeSlice>> userTypeDistributionSince(Instant since);
}