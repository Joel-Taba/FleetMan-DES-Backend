package com.yowyob.fleet.domain.ports.out;

import com.yowyob.fleet.domain.model.Fleet;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface FleetRepositoryPort {
    Mono<Fleet> save(Fleet fleet);
    Mono<Fleet> findById(UUID id);
    Flux<Fleet> findAll();
    Mono<Void> deleteById(UUID id);
    Mono<Boolean> existsByIdAndManagerId(UUID fleetId, UUID managerId);
}