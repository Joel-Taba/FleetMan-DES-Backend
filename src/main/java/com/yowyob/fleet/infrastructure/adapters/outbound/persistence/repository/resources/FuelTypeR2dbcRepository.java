package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.resources;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.resources.FuelTypeEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface FuelTypeR2dbcRepository extends ReactiveCrudRepository<FuelTypeEntity, UUID> { }