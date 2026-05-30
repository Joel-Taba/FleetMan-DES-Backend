package com.yowyob.fleet.domain.ports.out;

import com.yowyob.fleet.domain.model.FleetManager; // Sera créé implicitement ou on utilise DTO interne
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface FleetManagerPersistencePort {
    Mono<Void> createProfile(UUID userId, String companyName);
    Mono<Void> updateCompany(UUID userId, String companyName);
    Mono<String> getCompanyName(UUID userId);
}