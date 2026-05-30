package com.yowyob.fleet.application.service;

import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.VehicleTypeRequest;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.VehicleTypeEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.VehicleTypeR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VehicleTypeService {

    private final VehicleTypeR2dbcRepository repository;

    // --- Lecture (Public / Auth) ---
    public Flux<VehicleTypeEntity> getAllTypes() {
        return repository.findAll();
    }

    public Mono<VehicleTypeEntity> getTypeById(UUID id) {
        return repository.findById(id);
    }

    // --- Écriture (Admin) ---
    public Mono<VehicleTypeEntity> createType(VehicleTypeRequest request) {
        VehicleTypeEntity entity = new VehicleTypeEntity(
                UUID.randomUUID(),
                request.code().toUpperCase(),
                request.label(),
                request.description(),
                true // isNew force l'INSERT
        );
        return repository.save(entity);
    }

    public Mono<VehicleTypeEntity> updateType(UUID id, VehicleTypeRequest request) {
        return repository.findById(id)
                .flatMap(existing -> {
                    existing.setCode(request.code().toUpperCase());
                    existing.setLabel(request.label());
                    existing.setDescription(request.description());
                    existing.setNew(false); // Update
                    return repository.save(existing);
                });
    }

    public Mono<Void> deleteType(UUID id) {
        // Note : PostgreSQL renverra une erreur si ce type est utilisé par des véhicules (FK Constraint).
        // C'est le comportement voulu pour la sécurité des données.
        return repository.deleteById(id);
    }
}