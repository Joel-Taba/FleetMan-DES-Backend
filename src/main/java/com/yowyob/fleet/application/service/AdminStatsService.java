package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.ports.in.AdminStatsUseCase;
import com.yowyob.fleet.domain.ports.out.StatisticsPort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.GlobalStatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AdminStatsService implements AdminStatsUseCase {

    private final StatisticsPort statisticsPort;

    @Override
    public Mono<GlobalStatsResponse> getGlobalStats() {
        // Exécution parallèle des 4 compteurs
        return Mono.zip(
                statisticsPort.countFleetManagers(),
                statisticsPort.countFleets(),
                statisticsPort.countVehicles(),
                statisticsPort.countDrivers()
        ).map(tuple -> new GlobalStatsResponse(
                tuple.getT1(), // Managers
                tuple.getT2(), // Fleets
                tuple.getT3(), // Vehicles
                tuple.getT4(), // Drivers
                "OPERATIONAL"
        ));
    }
}