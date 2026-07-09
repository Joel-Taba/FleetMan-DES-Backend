package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.ports.in.AuthUseCase;
import com.yowyob.fleet.domain.ports.in.ManageSubscriptionPlanUseCase;
import com.yowyob.fleet.domain.ports.out.MailPort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.SubscriptionDocumentResponse;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.SubscriptionDocumentEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.FleetManagerR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.SubscriptionDocumentR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.UserLocalR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
    private final FleetManagerR2dbcRepository managerRepo;
    private final SubscriptionDocumentR2dbcRepository documentRepo;
    private final DatabaseClient db;
    private final MailPort mailPort;

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
                List.of("FLEET_MANAGER"),
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
                .flatMap(localUserId -> updateManagerProfile(localUserId, cmd.companyName(), cmd.requestedPlanId())
                        .then(saveDocuments(localUserId, cmd.documents()))
                        .thenReturn(java.util.Map.<String, Object>of(
                                "id", localUserId.toString(),
                                "status", "PENDING",
                                "message", "Demande enregistrée. Vous serez notifié après validation."
                        )));
    }

    private Mono<Void> updateManagerProfile(UUID userId, String companyName, UUID requestedPlanId) {
        return managerRepo.findById(userId)
                .flatMap(mgr -> {
                    if (companyName != null && !companyName.isBlank()) {
                        mgr.setCompanyName(companyName);
                    }
                    mgr.setNew(false);
                    return managerRepo.save(mgr);
                })
                .then(requestedPlanId != null
                        ? db.sql("""
                                UPDATE fleet.fleet_managers
                                SET requested_plan_id = :planId
                                WHERE user_id = :userId
                                """)
                                .bind("planId", requestedPlanId)
                                .bind("userId", userId)
                                .fetch()
                                .rowsUpdated()
                                .then()
                        : Mono.empty());
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
