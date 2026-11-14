package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.MissionException;
import com.yowyob.fleet.domain.model.DeliveryProof;
import com.yowyob.fleet.domain.ports.in.ManageDeliveryProofUseCase;
import com.yowyob.fleet.domain.ports.out.DeliveryProofPersistencePort;
import com.yowyob.fleet.domain.ports.out.MissionPersistencePort;
import com.yowyob.fleet.domain.ports.out.TiiBnTickMissionPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Service applicatif pour la gestion et la vérification des Preuves de Livraison.
 * Responsabilités G4 & R9 (Confiance et intégrité) :
 * - Dépôt de preuves (photo, signature, QR scan) par les conducteurs.
 * - Vérification cryptographique d'intégrité (HMAC-SHA256) pour détecter toute falsification.
 * - Validation managériale préalable au déblocage des paiements côté TiiBnTick/Agrinet.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryProofService implements ManageDeliveryProofUseCase {

    private final DeliveryProofPersistencePort proofPersistence;
    private final MissionPersistencePort missionPersistence;
    private final TiiBnTickMissionPort tiiBnTickPort;

    @Override
    @Transactional
    public Mono<DeliveryProof> submitProof(SubmitProofCommand cmd) {
        log.info("Dépôt de preuve pour la mission {} par le conducteur {}", cmd.missionId(), cmd.driverId());

        return missionPersistence.findById(cmd.missionId())
                .switchIfEmpty(Mono.error(MissionException.missionNotFound(cmd.missionId())))
                .flatMap(mission -> {
                    // Instanciation de la preuve du domaine
                    DeliveryProof proof = new DeliveryProof(
                            cmd.missionId(),
                            cmd.driverId(),
                            cmd.type() != null ? cmd.type() : DeliveryProof.ProofType.PHOTO,
                            cmd.tenantId() != null ? cmd.tenantId() : mission.getTenantId()
                    );

                    if (cmd.fileId() != null || cmd.fileUrl() != null) {
                        proof.setFileInfo(cmd.fileId(), cmd.fileUrl());
                    }
                    if (cmd.integrityHash() != null) {
                        proof.setIntegrityHash(cmd.integrityHash());
                    }
                    if (cmd.recipientName() != null || cmd.recipientSignature() != null) {
                        proof.setRecipientInfo(cmd.recipientName(), cmd.recipientSignature());
                    }
                    if (cmd.latitude() != null && cmd.longitude() != null) {
                        proof.setLocation(cmd.latitude(), cmd.longitude());
                    }

                    return proofPersistence.save(proof);
                })
                .doOnSuccess(saved -> log.info("Preuve enregistrée avec succès (ID: {})", saved.getId()));
    }

    @Override
    public Mono<DeliveryProof> getProofByMissionId(UUID missionId) {
        return proofPersistence.findByMissionId(missionId)
                .switchIfEmpty(Mono.error(MissionException.proofNotFound(missionId)));
    }

    @Override
    public Mono<DeliveryProof> getProofById(UUID id) {
        return proofPersistence.findById(id)
                .switchIfEmpty(Mono.error(MissionException.proofNotFound(id)));
    }

    @Override
    @Transactional
    public Mono<DeliveryProof> acceptProof(UUID proofId, UUID reviewerId) {
        log.info("Acceptation de la preuve {} par le manager {}", proofId, reviewerId);

        return proofPersistence.findById(proofId)
                .switchIfEmpty(Mono.error(MissionException.proofNotFound(proofId)))
                .flatMap(proof -> {
                    try {
                        proof.accept(reviewerId);
                    } catch (IllegalStateException e) {
                        return Mono.error(MissionException.proofAlreadyProcessed(proofId));
                    }

                    return proofPersistence.save(proof)
                            .flatMap(acceptedProof -> {
                                // Notification à TiiBnTick pour libération du règlement
                                return missionPersistence.findById(acceptedProof.getMissionId())
                                        .flatMap(m -> {
                                            if (m.getTiiBnTickDeliveryId() != null) {
                                                return tiiBnTickPort.notifyMissionCompleted(
                                                        m.getTiiBnTickDeliveryId(),
                                                        m.getId(),
                                                        acceptedProof.getId()
                                                );
                                            }
                                            return Mono.empty();
                                        })
                                        .thenReturn(acceptedProof);
                            });
                });
    }

    @Override
    @Transactional
    public Mono<DeliveryProof> rejectProof(UUID proofId, UUID reviewerId, String reason) {
        log.warn("Rejet de la preuve {} par le manager {}. Motif : {}", proofId, reviewerId, reason);

        return proofPersistence.findById(proofId)
                .switchIfEmpty(Mono.error(MissionException.proofNotFound(proofId)))
                .flatMap(proof -> {
                    try {
                        proof.reject(reviewerId, reason);
                    } catch (IllegalStateException e) {
                        return Mono.error(MissionException.proofAlreadyProcessed(proofId));
                    }

                    return proofPersistence.save(proof)
                            .flatMap(rejectedProof -> {
                                // Notification d'incident sur la livraison TiiBnTick
                                return missionPersistence.findById(rejectedProof.getMissionId())
                                        .flatMap(m -> {
                                            if (m.getTiiBnTickDeliveryId() != null) {
                                                return tiiBnTickPort.notifyMissionIncident(
                                                        m.getTiiBnTickDeliveryId(),
                                                        m.getId(),
                                                        "PROOF_REJECTED",
                                                        reason
                                                );
                                            }
                                            return Mono.empty();
                                        })
                                        .thenReturn(rejectedProof);
                            });
                });
    }

    @Override
    @Transactional
    public Mono<Boolean> verifyProofIntegrity(UUID proofId, String calculatedHash) {
        return proofPersistence.findById(proofId)
                .switchIfEmpty(Mono.error(MissionException.proofNotFound(proofId)))
                .flatMap(proof -> {
                    if (proof.getIntegrityHash() == null) {
                        return Mono.error(MissionException.integrityHashMissing());
                    }

                    boolean isValid = proof.verifyIntegrity(calculatedHash);
                    if (!isValid) {
                        log.error("ALTÉRATION DÉTECTÉE sur la preuve {} ! Hash stocké: {}, Hash calculé: {}",
                                proofId, proof.getIntegrityHash(), calculatedHash);
                        proof.markAsTampered();
                        return proofPersistence.save(proof)
                                .then(Mono.error(MissionException.proofTamperedDetected()));
                    }

                    return Mono.just(true);
                });
    }
}

