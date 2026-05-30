package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.resources;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.resources.BrandEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface BrandR2dbcRepository extends ReactiveCrudRepository<BrandEntity, UUID> { }