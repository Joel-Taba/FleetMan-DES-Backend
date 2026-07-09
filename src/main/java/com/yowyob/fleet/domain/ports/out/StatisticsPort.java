package com.yowyob.fleet.domain.ports.out;

import reactor.core.publisher.Mono;

public interface StatisticsPort {
    Mono<Long> countFleetManagers();
    Mono<Long> countFleets();
    Mono<Long> countVehicles();
    Mono<Long> countDrivers();
    /** Comptes locaux actifs avec rôle admin (approx plateform). */
    Mono<Long> countActiveAdmins();
}