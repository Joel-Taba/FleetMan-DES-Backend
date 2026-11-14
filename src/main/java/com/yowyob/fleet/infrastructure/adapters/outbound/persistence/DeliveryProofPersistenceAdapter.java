package com.yowyob.fleet.infrastructure.adapters.outbound.persistence;

import com.yowyob.fleet.domain.model.DeliveryProof;
import com.yowyob.fleet.domain.ports.out.DeliveryProofPersistencePort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.DeliveryProofEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.DeliveryProofR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DeliveryProofPersistenceAdapter implements DeliveryProofPersistencePort {

    private final DeliveryProofR2dbcRepository repository;

    @Override
    public Mono<DeliveryProof> save(DeliveryProof proof) {
        DeliveryProofEntity entity = toEntity(proof);
        return repository.existsById(entity.getId())
                .flatMap(exists -> {
                    entity.setNew(!exists);
                    return repository.save(entity);
                })
                .map(this::toDomain);
    }

    @Override
    public Mono<DeliveryProof> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Mono<DeliveryProof> findByMissionId(UUID missionId) {
        return repository.findByMissionId(missionId).map(this::toDomain);
    }

    @Override
    public Mono<Boolean> existsByMissionId(UUID missionId) {
        return repository.existsByMissionId(missionId);
    }

    @Override
    public Mono<Void> deleteById(UUID id) {
        return repository.deleteById(id);
    }

    // ── Mappings ──────────────────────────────────────────────────────────────

    private DeliveryProofEntity toEntity(DeliveryProof domain) {
        DeliveryProofEntity entity = new DeliveryProofEntity();
        entity.setId(domain.getId());
        entity.setMissionId(domain.getMissionId());
        entity.setDriverId(domain.getDriverId());
        entity.setType(domain.getType() != null ? domain.getType().name() : DeliveryProof.ProofType.PHOTO.name());
        entity.setStatus(domain.getStatus() != null ? domain.getStatus().name() : DeliveryProof.ProofStatus.PENDING_REVIEW.name());
        entity.setFileId(domain.getFileId());
        entity.setFileUrl(domain.getFileUrl());
        entity.setIntegrityHash(domain.getIntegrityHash());
        entity.setRecipientName(domain.getRecipientName());
        entity.setRecipientSignature(domain.getRecipientSignature());
        entity.setLatitude(domain.getLatitude());
        entity.setLongitude(domain.getLongitude());
        entity.setNotes(domain.getNotes());
        entity.setSubmittedAt(domain.getSubmittedAt() != null ? domain.getSubmittedAt() : LocalDateTime.now());
        entity.setReviewedAt(domain.getReviewedAt());
        entity.setReviewedBy(domain.getReviewedBy());
        entity.setCreatedAt(domain.getCreatedAt() != null ? domain.getCreatedAt() : LocalDateTime.now());
        entity.setTenantId(domain.getTenantId());
        return entity;
    }

    private DeliveryProof toDomain(DeliveryProofEntity entity) {
        DeliveryProof.ProofType type = entity.getType() != null
                ? DeliveryProof.ProofType.valueOf(entity.getType())
                : DeliveryProof.ProofType.PHOTO;
        DeliveryProof.ProofStatus status = entity.getStatus() != null
                ? DeliveryProof.ProofStatus.valueOf(entity.getStatus())
                : DeliveryProof.ProofStatus.PENDING_REVIEW;

        return new DeliveryProof(
                entity.getId(),
                entity.getMissionId(),
                entity.getDriverId(),
                type,
                status,
                entity.getFileId(),
                entity.getFileUrl(),
                entity.getIntegrityHash(),
                entity.getRecipientName(),
                entity.getRecipientSignature(),
                entity.getLatitude(),
                entity.getLongitude(),
                entity.getNotes(),
                entity.getSubmittedAt(),
                entity.getReviewedAt(),
                entity.getReviewedBy(),
                entity.getTenantId(),
                entity.getCreatedAt()
        );
    }
}

