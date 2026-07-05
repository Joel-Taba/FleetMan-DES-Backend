package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.TripDetailEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface TripDetailR2dbcRepository extends ReactiveCrudRepository<TripDetailEntity, UUID> {
    Flux<TripDetailEntity> findAllByTripIdOrderBySortOrder(UUID tripId);
    Mono<Void> deleteAllByTripId(UUID tripId);
}
