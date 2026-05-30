package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.GeofenceEventEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import java.util.UUID;

public interface GeofenceEventR2dbcRepository extends ReactiveCrudRepository<GeofenceEventEntity, UUID> {
}