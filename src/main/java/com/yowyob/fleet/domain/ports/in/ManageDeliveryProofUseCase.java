package com.yowyob.fleet.domain.ports.in;

import com.yowyob.fleet.domain.model.DeliveryProof;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Port d'entrée : cas d'utilisation pour la gestion et la vérification des preuves de livraison.
 * Garantit la conformité, l'absence de fraude et la détection d'altération (HMAC).
 */
public interface ManageDeliveryProofUseCase {

    // ── Commandes ─────────────────────────────────────────────────────────────

    record SubmitProofCommand(
            UUID missionId,
            UUID driverId,
            DeliveryProof.ProofType type,
            String fileId,
            String fileUrl,
            String integrityHash,
            String recipientName,
            String recipientSignature,
            Double latitude,
            Double longitude,
            String notes,
            UUID tenantId
    ) {}

    // ── Use Cases ─────────────────────────────────────────────────────────────

    /**
     * Dépôt d'une preuve de livraison par le conducteur ou le système mobile.
     */
    Mono<DeliveryProof> submitProof(SubmitProofCommand command);

    /**
     * Récupère la preuve de livraison associée à une mission.
     */
    Mono<DeliveryProof> getProofByMissionId(UUID missionId);

    /**
     * Récupère une preuve par son identifiant unique.
     */
    Mono<DeliveryProof> getProofById(UUID id);

    /**
     * Validation managériale de la preuve déposée.
     */
    Mono<DeliveryProof> acceptProof(UUID proofId, UUID reviewerId);

    /**
     * Rejet d'une preuve non conforme avec motif explicite.
     */
    Mono<DeliveryProof> rejectProof(UUID proofId, UUID reviewerId, String reason);

    /**
     * Vérification cryptographique de l'intégrité de la preuve par rapport au hash calculé.
     * En cas de disparité, la preuve est marquée comme TAMPERED.
     */
    Mono<Boolean> verifyProofIntegrity(UUID proofId, String calculatedHash);
}

