package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.OperationalParameterEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Repository
public interface OperationalParameterR2dbcRepository extends ReactiveCrudRepository<OperationalParameterEntity, UUID> {
    Mono<OperationalParameterEntity> findByVehicleId(UUID vehicleId);
}