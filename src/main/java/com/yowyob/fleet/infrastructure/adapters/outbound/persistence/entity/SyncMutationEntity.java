package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Enregistrement d'idempotence write-once (jamais relu-puis-resauvegardé —
 * voir IdempotencyService.save(), toujours un `new SyncMutationEntity()`).
 * L'id étant fourni par le client (clientMutationId), Spring Data R2DBC ne
 * peut pas déduire "nouvel enregistrement" depuis un id non-null : sans
 * Persistable, il tente un UPDATE dès le tout premier save() et échoue
 * ("Row with Id [...] does not exist") — cause racine du 500 systématique
 * sur le replay des mutations en file (POST /api/v1/sync/mutations) et de
 * l'échec silencieux (avalé) de l'idempotence sur les appels directs.
 */
@Table(name = "sync_mutations", schema = "fleet")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SyncMutationEntity implements Persistable<UUID> {

    @Id
    @Column("client_mutation_id")
    private UUID clientMutationId;

    @Column("user_id")
    private UUID userId;

    @Column("http_method")
    private String httpMethod;

    private String endpoint;

    @Column("request_hash")
    private String requestHash;

    @Column("response_status")
    private Integer responseStatus;

    @Column("response_body")
    private String responseBody;

    @Column("entity_id")
    private UUID entityId;

    @Column("processed_at")
    private Instant processedAt;

    @Transient
    private boolean isNewRecord = true;

    @Override
    public UUID getId() {
        return clientMutationId;
    }

    @Override
    public boolean isNew() {
        return isNewRecord;
    }
}
