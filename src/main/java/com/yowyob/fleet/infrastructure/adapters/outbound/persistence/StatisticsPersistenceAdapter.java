package com.yowyob.fleet.infrastructure.adapters.outbound.persistence;

import com.yowyob.fleet.domain.ports.out.StatisticsPort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.DriverR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.FleetManagerR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.FleetR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.VehicleLocalR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class StatisticsPersistenceAdapter implements StatisticsPort {

    private final FleetManagerR2dbcRepository managerRepo;
    private final FleetR2dbcRepository fleetRepo;
    private final VehicleLocalR2dbcRepository vehicleRepo;
    private final DriverR2dbcRepository driverRepo;

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
}