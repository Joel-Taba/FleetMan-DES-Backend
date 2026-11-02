package com.yowyob.fleet.infrastructure.adapters.outbound.persistence;

import com.yowyob.fleet.domain.exception.AdminException;
import com.yowyob.fleet.domain.ports.out.FleetManagerPersistencePort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.FleetManagerEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.FleetManagerR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.PlatformAdminR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class FleetManagerPersistenceAdapter implements FleetManagerPersistencePort {

    private final FleetManagerR2dbcRepository repository;
    private final PlatformAdminR2dbcRepository platformAdminRepo;
    private final com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.CompanyR2dbcRepository companyRepo;

    @Override
    public Mono<Void> createProfile(UUID userId, String companyName, UUID createdByAdminId) {
        if (createdByAdminId == null) {
            return Mono.error(AdminException.createdByMustBeAdmin());
        }
        // Idempotent : le Kernel peut renvoyer l'ID d'un compte existant lors d'une
        // inscription (dédoublonnage par téléphone), auquel cas le profil local existe
        // déjà — on met simplement à jour plutôt que de violer la contrainte PK.
        return requireExistingAdmin(createdByAdminId)
                .then(repository.findById(userId)
                        .flatMap(existing -> {
                            existing.setCompanyName(companyName);
                            if (existing.getCreatedByAdminId() == null) {
                                existing.setCreatedByAdminId(createdByAdminId);
                            }
                            return repository.save(existing);
                        })
                        .switchIfEmpty(Mono.defer(() -> {
                            FleetManagerEntity entity = new FleetManagerEntity(userId, companyName, createdByAdminId);
                            return repository.save(entity);
                        })))
                .then();
    }

    private Mono<UUID> requireExistingAdmin(UUID adminId) {
        return platformAdminRepo.existsById(adminId)
                .flatMap(exists -> exists
                        ? Mono.just(adminId)
                        : Mono.error(AdminException.createdByMustBeAdmin()));
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
    /** Nom de l'entreprise de l'utilisateur (admin ou gestionnaire). */
    public Mono<String> getCompanyName(UUID userId) {
        return companyRepo.findForUser(userId)
                .map(c -> c.getName() != null ? c.getName() : "")
                .switchIfEmpty(repository.findById(userId)
                        .map(entity -> entity.getCompanyName() != null ? entity.getCompanyName() : ""));
    }
}
