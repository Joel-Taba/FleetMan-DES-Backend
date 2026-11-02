package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.model.DocumentCatalog.TargetType;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.DocumentR2dbcRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Lecture d'un fichier stocké : une pièce d'un dossier, un dossier d'inscription ou un justificatif
 * de dépense ou une pièce d'incident ne se lit qu'avec les droits sur l'objet auquel il est rattaché. Il suffisait d'être
 * connecté pour télécharger la CNI de n'importe qui. Les autres fichiers (photos, logos) restent
 * lisibles par tout utilisateur connecté.
 */
@Service
@RequiredArgsConstructor
public class FileAccessService {

    private final DocumentR2dbcRepository documents;
    private final DocumentAccessService documentAccess;
    private final ResourceAccessService resourceAccess;
    private final AccessScopeService accessScope;
    private final DatabaseClient db;

    public Mono<Void> assertCanRead(UUID actorId, boolean superAdmin, String fileName) {
        String url = "/api/v1/files/" + fileName;
        return documents.findAllByFileUrl(url).next()
                .flatMap(doc -> documentAccess.assertCanRead(actorId, TargetType.valueOf(doc.getTargetType()), doc.getTargetId())
                        .thenReturn(true))
                .switchIfEmpty(Mono.defer(() -> subscriptionOwner(url)
                        .flatMap(owner -> superAdmin ? Mono.just(true) : assertSelf(actorId, owner).thenReturn(true))))
                .switchIfEmpty(Mono.defer(() -> expenseOf(url)
                        .flatMap(expenseId -> resourceAccess.assertExpenseAccess(actorId, expenseId).thenReturn(true))))
                .switchIfEmpty(Mono.defer(() -> incidentOf(url)
                        .flatMap(incidentId -> resourceAccess.assertIncidentAccess(actorId, incidentId).thenReturn(true))))
                .then();
    }

    private Mono<Void> assertSelf(UUID actorId, UUID owner) {
        return accessScope.resolveLocalUserId(actorId).flatMap(localId -> localId.equals(owner)
                ? Mono.<Void>empty()
                : Mono.<Void>error(new AccessDeniedException("Fichier hors périmètre.")));
    }

    private Mono<UUID> subscriptionOwner(String url) {
        return db.sql("SELECT user_id FROM fleet.subscription_documents WHERE file_url = :u LIMIT 1")
                .bind("u", url)
                .map(row -> row.get("user_id", UUID.class))
                .one();
    }

    private Mono<UUID> incidentOf(String url) {
        return db.sql("SELECT incident_id FROM fleet.incident_attachments WHERE file_url = :u LIMIT 1")
                .bind("u", url)
                .map(row -> row.get("incident_id", UUID.class))
                .one();
    }

    private Mono<UUID> expenseOf(String url) {
        return db.sql("SELECT id FROM fleet.expenses WHERE receipt_url = :u LIMIT 1")
                .bind("u", url)
                .map(row -> row.get("id", UUID.class))
                .one();
    }
}
