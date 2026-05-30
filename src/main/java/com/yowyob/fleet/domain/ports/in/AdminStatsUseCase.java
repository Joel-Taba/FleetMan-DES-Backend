package com.yowyob.fleet.domain.ports.in;

import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.GlobalStatsResponse;
import reactor.core.publisher.Mono;

public interface AdminStatsUseCase {
    Mono<GlobalStatsResponse> getGlobalStats();
}