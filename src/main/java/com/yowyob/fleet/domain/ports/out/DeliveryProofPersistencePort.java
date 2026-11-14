package com.yowyob.fleet.domain.ports.out;

import com.yowyob.fleet.domain.model.DeliveryProof;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Port sortant : contrat de persistance pour les preuves de livraison.
 * Implémenté par DeliveryProofPersistenceAdapter dans l'infrastructure R2DBC.
 */
public interface DeliveryProofPersistencePort {

    Mono<DeliveryProof> save(DeliveryProof proof);

    Mono<DeliveryProof> findById(UUID id);

    Mono<DeliveryProof> findByMissionId(UUID missionId);

    Mono<Boolean> existsByMissionId(UUID missionId);

    Mono<Void> deleteById(UUID id);
}

