package com.yowyob.fleet.domain.ports.in;

import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.FleetManagerResponse;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.ManagerKpiResponse;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface ManageFleetManagerUseCase {
    // Le token est requis pour les appels distants
    Flux<FleetManagerResponse> getAllManagers(String token);
    Mono<FleetManagerResponse> getManagerDetails(UUID userId, String token);
    Mono<Void> updateManagerCompany(UUID userId, String companyName);
    Mono<Void> deleteManager(UUID userId, String token);
    Mono<ManagerKpiResponse> getManagerKpis(UUID managerId);
}