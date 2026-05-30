package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.VehicleTypeEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface VehicleTypeR2dbcRepository extends ReactiveCrudRepository<VehicleTypeEntity, UUID> {
    // On pourra ajouter findByCode(String code) si besoin plus tard
}