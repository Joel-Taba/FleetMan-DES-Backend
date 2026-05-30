package com.yowyob.fleet.infrastructure.adapters.outbound.persistence;

import com.yowyob.fleet.domain.model.Fleet;
import com.yowyob.fleet.domain.ports.out.FleetRepositoryPort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.FleetR2dbcRepository;
import com.yowyob.fleet.infrastructure.mappers.FleetMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class FleetPersistenceAdapter implements FleetRepositoryPort {

    private final FleetR2dbcRepository repository;
    private final FleetMapper mapper;

    @Override
    public Mono<Fleet> save(Fleet fleet) {
        return repository.save(mapper.toEntity(fleet))
                .map(mapper::toDomain);
    }

    @Override
    public Mono<Fleet> findById(UUID id) {
        return repository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public Flux<Fleet> findAll() {
        return repository.findAll()
                .map(mapper::toDomain);
    }

    @Override
    public Mono<Void> deleteById(UUID id) {
        return repository.deleteById(id);
    }

    @Override
    public Mono<Boolean> existsByIdAndManagerId(UUID id, UUID managerId) {
        return repository.existsByIdAndManagerId(id, managerId);
    }
}