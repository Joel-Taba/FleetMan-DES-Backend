package com.yowyob.fleet.domain.ports.out;

import com.yowyob.fleet.domain.model.FleetManager; // Sera créé implicitement ou on utilise DTO interne
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface FleetManagerPersistencePort {
    /**
     * Crée (ou met à jour) le profil manager. {@code createdByAdminId} est obligatoire
     * et doit correspondre à un {@code platform_admins.user_id} existant.
     */
    Mono<Void> createProfile(UUID userId, String companyName, UUID createdByAdminId);

    Mono<Void> updateCompany(UUID userId, String companyName);
    Mono<String> getCompanyName(UUID userId);
}