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
                    // Ici isNew est false par défaut (chargé depuis la DB), donc R2DBC fera un
                    // UPDATE
                    return repository.save(entity);
                })
                .then();
    }

    @Override
    public Mono<String> getCompanyName(UUID userId) {
        return repository.findById(userId)
                .map(entity -> entity.getCompanyName() != null ? entity.getCompanyName() : "");
    }

    @Override
    public Mono<Void> updateCompanyDetails(UUID userId, String companyName, String phone, String address, String city,
            String logoUrl) {
        return repository.findById(userId)
                .flatMap(entity -> {
                    if (companyName != null)
                        entity.setCompanyName(companyName);
                    if (phone != null)
                        entity.setPhone(phone);
                    if (address != null)
                        entity.setAddress(address);
                    if (city != null)
                        entity.setCity(city);
                    if (logoUrl != null)
                        entity.setLogoUrl(logoUrl);
                    return repository.save(entity);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // Create if not exists
                    FleetManagerEntity entity = new FleetManagerEntity(userId, companyName);
                    entity.setPhone(phone);
                    entity.setAddress(address);
                    entity.setCity(city);
                    entity.setLogoUrl(logoUrl);
                    return repository.save(entity);
                }))
                .then();
    }

    @Override
    public Mono<CompanyDetails> getCompanyDetails(UUID userId) {
        return repository.findById(userId)
                .map(entity -> new CompanyDetails(
                        entity.getCompanyName() != null ? entity.getCompanyName() : "",
                        entity.getPhone() != null ? entity.getPhone() : "",
                        entity.getAddress() != null ? entity.getAddress() : "",
                        entity.getCity() != null ? entity.getCity() : "",
                        entity.getLogoUrl() != null ? entity.getLogoUrl() : ""));
    }
}
