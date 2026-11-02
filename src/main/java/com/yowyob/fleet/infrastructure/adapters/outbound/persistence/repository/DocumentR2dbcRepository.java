package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.DocumentEntity;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface DocumentR2dbcRepository extends ReactiveCrudRepository<DocumentEntity, UUID> {

    /** Dossier en cours d'une cible : ni archivé, ni remplacé par une version plus récente. */
    @Query("SELECT * FROM fleet.documents WHERE target_type = :targetType AND target_id = :targetId "
            + "AND archived_at IS NULL AND superseded_by IS NULL ORDER BY doc_type, created_at DESC")
    Flux<DocumentEntity> findActive(String targetType, UUID targetId);

    @Query("SELECT * FROM fleet.documents WHERE target_id IN (:targetIds) "
            + "AND archived_at IS NULL AND superseded_by IS NULL")
    Flux<DocumentEntity> findActiveForTargets(Collection<UUID> targetIds);

    /** Toutes les versions (historique complet, archives comprises). */
    @Query("SELECT * FROM fleet.documents WHERE target_type = :targetType AND target_id = :targetId "
            + "ORDER BY doc_type, version DESC, created_at DESC")
    Flux<DocumentEntity> findHistory(String targetType, UUID targetId);

    @Query("SELECT COALESCE(MAX(version), 0) FROM fleet.documents WHERE target_type = :targetType "
            + "AND target_id = :targetId AND doc_type = :docType")
    Mono<Integer> maxVersion(String targetType, UUID targetId, String docType);

    @Query("SELECT * FROM fleet.documents WHERE target_type = :targetType AND target_id = :targetId "
            + "AND doc_type = :docType AND is_current AND archived_at IS NULL")
    Flux<DocumentEntity> findCurrent(String targetType, UUID targetId, String docType);

    /** Le fichier est-il déjà rattaché à une autre cible ? Sinon, le rattacher ouvrirait sa lecture. */
    @Query("SELECT COUNT(*) > 0 FROM fleet.documents WHERE file_url = :fileUrl "
            + "AND NOT (target_type = :targetType AND target_id = :targetId)")
    Mono<Boolean> fileUsedByAnotherTarget(String fileUrl, String targetType, UUID targetId);

    Flux<DocumentEntity> findAllByFileUrl(String fileUrl);
}
