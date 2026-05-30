package com.yowyob.fleet.infrastructure.adapters.outbound.persistence;

import com.yowyob.fleet.domain.ports.out.FleetManagerPersistencePort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.FleetManagerEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.FleetManagerR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class FleetManagerPersistenceAdapter implements FleetManagerPersistencePort {

    private final FleetManagerR2dbcRepository repository;

    @Override
    public Mono<Void> createProfile(UUID userId, String companyName) {
        // Utilisation du constructeur qui met isNew = true
        FleetManagerEntity entity = new FleetManagerEntity(userId, companyName);
        return repository.save(entity).then();
    }

    @Override
    public Mono<Void> updateCompany(UUID userId, String companyName) {
        return repository.findById(userId)
                .flatMap(entity -> {
                    entity.setCompanyName(companyName);
                    // Ici isNew est false par défaut (chargé depuis la DB), donc R2DBC fera un UPDATE
                    return repository.save(entity);
                })
                .then();
    }

    @Override
    public Mono<String> getCompanyName(UUID userId) {
        return repository.findById(userId)
                .map(entity -> entity.getCompanyName() != null ? entity.getCompanyName() : "");
    }
}