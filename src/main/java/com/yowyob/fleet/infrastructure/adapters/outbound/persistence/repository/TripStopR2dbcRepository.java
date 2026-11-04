package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.TripStopEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface TripStopR2dbcRepository extends ReactiveCrudRepository<TripStopEntity, UUID> {

    Flux<TripStopEntity> findAllByTripIdOrderBySequence(UUID tripId);

    Mono<TripStopEntity> findByIdAndTripId(UUID id, UUID tripId);
}
