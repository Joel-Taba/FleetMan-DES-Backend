package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.FinancialParameterEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Repository
public interface FinancialParameterR2dbcRepository extends ReactiveCrudRepository<FinancialParameterEntity, UUID> {
    
    /**
     * Fetches financial details linked to a specific vehicle ID.
     */
    Mono<FinancialParameterEntity> findByVehicleId(UUID vehicleId);
}