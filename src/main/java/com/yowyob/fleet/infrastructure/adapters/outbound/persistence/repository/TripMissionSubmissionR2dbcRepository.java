package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.TripMissionSubmissionEntity;
import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface TripMissionSubmissionR2dbcRepository
    extends ReactiveCrudRepository<TripMissionSubmissionEntity, UUID>
{
    Flux<TripMissionSubmissionEntity> findAllByTripIdAndStatus(
        UUID tripId,
        String status
    );

    Mono<TripMissionSubmissionEntity> findByIdAndStatus(UUID id, String status);
}
