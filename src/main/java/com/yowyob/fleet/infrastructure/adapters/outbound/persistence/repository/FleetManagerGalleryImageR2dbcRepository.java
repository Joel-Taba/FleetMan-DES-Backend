package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.FleetManagerGalleryImageEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.util.UUID;

@Repository
public interface FleetManagerGalleryImageR2dbcRepository
        extends ReactiveCrudRepository<FleetManagerGalleryImageEntity, UUID> {

    Flux<FleetManagerGalleryImageEntity> findByManagerId(UUID managerId);
}
