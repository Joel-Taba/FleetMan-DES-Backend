package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.UserLocalEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.Collection;

import java.util.UUID;
import java.util.Collection;

@Repository
public interface UserLocalR2dbcRepository extends ReactiveCrudRepository<UserLocalEntity, UUID> {
    
    /**
     * Recherche un utilisateur par son login technique.
     * Utilisé pour la vérification du statut local (is_active) lors du login/register.
     */
    Mono<UserLocalEntity> findByUsername(String username);

    /**
     * Recherche un utilisateur par email.
     * Utilisé pour le merge Kernel → local (IDs peuvent différer).
     */
    Mono<UserLocalEntity> findByEmail(String email);

    /**
     * Recherche un utilisateur par son ID Kernel (UUID Kernel ≠ UUID local potentiellement).
     */
    Mono<UserLocalEntity> findByKernelId(UUID kernelId);

    Flux<UserLocalEntity> findByEmailIn(Collection<String> emails);

    /** Chauffeurs actifs portant ce numéro (connexion par téléphone). */
    @org.springframework.data.r2dbc.repository.Query("SELECT u.* FROM fleet.users u JOIN fleet.drivers d ON d.user_id = u.id "
            + "WHERE REGEXP_REPLACE(u.phone, '[^0-9+]', '', 'g') = :phone AND u.deleted_at IS NULL AND d.deleted_at IS NULL")
    reactor.core.publisher.Flux<com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.UserLocalEntity> findDriversByPhone(String phone);
}