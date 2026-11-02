package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.ports.in.AuthUseCase;
import com.yowyob.fleet.domain.ports.in.ManageSubscriptionPlanUseCase;
import com.yowyob.fleet.domain.ports.out.ExternalFilePort;
import com.yowyob.fleet.domain.ports.out.ExternalKycPort;
import com.yowyob.fleet.domain.ports.out.MailPort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.KycDocumentVerificationResponse;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.SubscriptionDocumentResponse;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.SubscriptionDocumentEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.SubscriptionDocumentR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.UserLocalR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionRegistrationService.class);
    private static final int MAX_DOCUMENTS = 10;

    private final AuthUseCase authUseCase;
    private final UserLocalR2dbcRepository userRepo;
    private final SubscriptionDocumentR2dbcRepository documentRepo;
    private final DatabaseClient db;
    private final MailPort mailPort;
    private final ExternalKycPort kycPort;
    private final ExternalFilePort filePort;
    private final LocalFileStorageService localFileStorageService;
    private final CompanyService companyService;

    public record DocumentInput(
            String docType,
            String docNumber,
            String fileUrl,
            String fileMimeType,
            String fileOriginalName,
            String issuer,
            String issueDate,
            String expiryDate,
            String notes
    ) {}

    public record RegisterManagerCommand(
            String username,
            String password,
            String email,
            String phone,
            String firstName,
            String lastName,
            String companyName,
            UUID requestedPlanId,
            com.yowyob.fleet.domain.model.BillingCycle requestedBillingCycle,
            List<DocumentInput> documents
    ) {}

    public Mono<java.util.Map<String, Object>> registerManager(RegisterManagerCommand cmd) {
        if (cmd.documents() == null || cmd.documents().isEmpty()) {
            return Mono.error(new IllegalArgumentException("Au moins un document est requis."));
        }
        if (cmd.documents().size() > MAX_DOCUMENTS) {
            return Mono.error(new IllegalArgumentException("Maximum " + MAX_DOCUMENTS + " documents autorisés."));
        }
        boolean hasCni = cmd.documents().stream().anyMatch(d -> "ID_CARD".equals(d.docType()));
        boolean hasCasier = cmd.documents().stream().anyMatch(d -> "CRIMINAL_RECORD".equals(d.docType()));
        if (!hasCni || !hasCasier) {
            return Mono.error(new IllegalArgumentException(
                    "La CNI et l'extrait de casier judiciaire sont obligatoires."));
        }

        var registerCmd = new AuthUseCase.RegisterCommand(
                cmd.username(),
                cmd.password(),
                cmd.email(),
                cmd.phone(),
                cmd.firstName(),
                cmd.lastName(),
                // L'inscription crée l'administrateur de l'entreprise ; le rôle FLEET_ADMIN lui est
                // attribué dans le Kernel à l'approbation (SubscriptionPlanService.approveSubscription).
                List.of("FLEET_ADMIN"),
                null
        );

        return authUseCase.register(registerCmd)
                .flatMap(res -> userRepo.findByKernelId(res.user().id())
                        .switchIfEmpty(userRepo.findById(res.user().id()))
                        .flatMap(user -> {
                            user.setApprovalStatus("PENDING");
                            user.setActive(false);
                            user.setNewRecord(false);
                            return userRepo.save(user).map(saved -> saved.getId());
                        }))
                .flatMap(localUserId -> companyService.createPending(localUserId, cmd.companyName(),
                                cmd.requestedPlanId(), cmd.requestedBillingCycle())
                        .then(saveDocuments(localUserId, cmd.documents()))
                        .thenReturn(java.util.Map.<String, Object>of(
                                "id", localUserId.toString(),
                                "status", "PENDING",
                                "message", "Demande enregistrée. Vous serez notifié après validation."
                        )));
    }

    private Mono<Void> saveDocuments(UUID userId, List<DocumentInput> documents) {
        return Flux.fromIterable(documents)
                .flatMap(d -> {
                    SubscriptionDocumentEntity e = new SubscriptionDocumentEntity();
                    e.setId(UUID.randomUUID());
                    e.setUserId(userId);
                    e.setDocType(d.docType());
                    e.setDocNumber(d.docNumber());
                    e.setFileUrl(d.fileUrl());
                    e.setFileMimeType(d.fileMimeType());
                    e.setFileOriginalName(d.fileOriginalName());
                    e.setIssuer(d.issuer());
                    e.setNotes(d.notes());
                    e.setCreatedAt(Instant.now());
                    e.setNew(true);
                    return documentRepo.save(e);
                })
                .then();
    }

    public Flux<SubscriptionDocumentResponse> listDocuments(UUID userId) {
        return documentRepo.findByUserId(userId).map(SubscriptionDocumentResponse::from);
    }

    public Mono<KycDocumentVerificationResponse> verifyDocument(
            UUID userId,
            UUID documentId,
            String bearerToken) {
        return documentRepo.findById(documentId)
                .filter(doc -> doc.getUserId().equals(userId))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Document introuvable pour cette demande.")))
                .flatMap(doc -> loadDocumentBytes(doc, null)
                        .flatMap(file -> kycPort.verify(
                                file.content(),
                                file.fileName(),
                                file.contentType(),
                                null))
                        .map(analysis -> KycDocumentVerificationResponse.from(doc, analysis))
                        .switchIfEmpty(Mono.error(new IllegalStateException(
                                "Le service KYC n'a renvoyé aucun résultat. Réessayez plus tard."))));
    }

    private record LoadedFile(byte[] content, String contentType, String fileName) {}

    private Mono<LoadedFile> loadDocumentBytes(SubscriptionDocumentEntity doc, String bearerToken) {
        String segment = extractFileSegment(doc.getFileUrl());
        if (segment == null || segment.isBlank()) {
            return Mono.error(new IllegalArgumentException("URL de fichier invalide."));
        }
        if (isUuid(segment)) {
            return filePort.download(UUID.fromString(segment), bearerToken)
                    .map(result -> new LoadedFile(
                            result.content(),
                            result.contentType() != null ? result.contentType() : doc.getFileMimeType(),
                            result.fileName() != null ? result.fileName() : doc.getFileOriginalName()));
        }
        return Mono.fromCallable(() -> {
            var path = localFileStorageService.resolve(segment);
            if (!Files.exists(path)) {
                throw new IllegalArgumentException("Fichier local introuvable : " + segment);
            }
            byte[] content = Files.readAllBytes(path);
            String mime = doc.getFileMimeType();
            if (mime == null || mime.isBlank()) {
                mime = Files.probeContentType(path);
            }
            if (mime == null || mime.isBlank()) {
                mime = "application/octet-stream";
            }
            String name = doc.getFileOriginalName() != null ? doc.getFileOriginalName() : segment;
            return new LoadedFile(content, mime, name);
        });
    }

    private static String extractFileSegment(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return null;
        }
        String normalized = fileUrl.trim();
        int filesIdx = normalized.indexOf("/api/v1/files/");
        if (filesIdx >= 0) {
            return normalized.substring(filesIdx + "/api/v1/files/".length());
        }
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }

    private static boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Envoie l'email de rejet de souscription au demandeur.
     * Dégradation gracieuse : un échec d'envoi n'interrompt pas le flux de rejet
     * (le statut REJECTED est déjà persisté par l'appelant).
     */
    public Mono<Void> sendRejectionEmail(String email, String subject, String message) {
        if (email == null || email.isBlank()) {
            log.warn("📧 [REJECTION EMAIL] Aucune adresse email pour ce demandeur, envoi ignoré.");
            return Mono.empty();
        }
        log.info("📧 [REJECTION EMAIL] Envoi à : {} | Sujet : {}", email, subject);
        return mailPort.sendEmail(email, subject, message)
                .doOnNext(sent -> {
                    if (Boolean.TRUE.equals(sent)) {
                        log.info("✅ [REJECTION EMAIL] Email de rejet remis pour {}", email);
                    } else {
                        log.warn("⚠️ [REJECTION EMAIL] Email de rejet NON envoyé pour {} (voir logs SMTP)", email);
                    }
                })
                .onErrorResume(e -> {
                    log.error("⚠️ [REJECTION EMAIL] Erreur inattendue pour {} : {}", email, e.getMessage());
                    return Mono.just(false);
                })
                .then();
    }
}
