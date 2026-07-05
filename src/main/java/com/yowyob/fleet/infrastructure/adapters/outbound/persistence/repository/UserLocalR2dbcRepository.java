package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.UserLocalEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono; // <--- L'IMPORT MANQUANT

import java.util.UUID;

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
}