package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.MaintenanceParameterEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Repository
public interface MaintenanceParameterR2dbcRepository extends ReactiveCrudRepository<MaintenanceParameterEntity, UUID> {
    
    /**
     * Fetches maintenance status linked to a specific vehicle ID.
     */
    Mono<MaintenanceParameterEntity> findByVehicleId(UUID vehicleId);
}