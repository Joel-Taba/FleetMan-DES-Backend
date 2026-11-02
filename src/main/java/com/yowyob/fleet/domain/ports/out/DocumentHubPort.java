package com.yowyob.fleet.domain.ports.out;

import java.time.LocalDate;
import java.util.UUID;
import reactor.core.publisher.Mono;

/**
 * Hub documentaire externe (file-core du Kernel : document-hub et document-governance).
 * FleetMan reste maître de son dossier ; ce port recopie rattachements, revues et politiques
 * quand le hub est activé (application.document.hub=kernel). Un échec ne bloque jamais FleetMan.
 */
public interface DocumentHubPort {

    /** Rattache un fichier du Kernel à une cible ; renvoie l'identifiant du lien, vide sinon. */
    Mono<UUID> attach(String targetType, UUID targetId, UUID kernelFileId, String category, String label);

    /** reviewStatus : APPROVED ou REJECTED. */
    Mono<Void> review(UUID linkId, String reviewStatus, LocalDate expiresAt, String notes);

    Mono<Void> upsertPolicy(String targetType, String category, boolean mandatory);
}
