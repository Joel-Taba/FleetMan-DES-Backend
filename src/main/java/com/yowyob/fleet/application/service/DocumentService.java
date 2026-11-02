package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.DocumentException;
import com.yowyob.fleet.domain.model.DocumentCatalog;
import com.yowyob.fleet.domain.model.DocumentCatalog.TargetType;
import com.yowyob.fleet.domain.model.DocumentRules;
import com.yowyob.fleet.domain.ports.out.DocumentHubPort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.DocumentEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.DocumentR2dbcRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Dossiers documentaires : dépôt (toujours avec justificatif), contrôle (approuvé / rejeté avec
 * motif), versions (l'ancienne pièce reste en vigueur jusqu'à l'approbation de la nouvelle),
 * archivage motivé (rien n'est effacé), checklist et rapport de conformité.
 */
@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final String FILE_URL_PREFIX = "/api/v1/files/";

    private final DocumentR2dbcRepository documents;
    private final DocumentAccessService access;
    private final DocumentComplianceService compliance;
    private final AccessScopeService accessScope;
    private final CompanyScopeService companyScope;
    private final DocumentHubPort hub;
    private final DatabaseClient db;

    public record SubmitCommand(String docType, String docNumber, String licenseCategories, String issuer,
                                LocalDate issueDate, LocalDate expiryDate, String fileUrl, String fileOriginalName,
                                String fileMimeType, Long fileSizeBytes, UUID kernelFileId, String notes) {
    }

    public record ApproveCommand(LocalDate expiryDate, String licenseCategories, String notes) {
    }

    public record ChecklistItem(String docType, boolean legal, boolean mandatory, String state, LocalDate expiryDate,
                                UUID documentId, boolean renewalPending) {
    }

    public record CatalogEntry(String targetType, String docType, boolean legal, boolean mandatory, boolean expiryRequired) {
    }

    public record TargetDocument(UUID documentId, String entityType, UUID entityId, String entityName, String docType,
                                 String docNumber, LocalDate expiryDate, long daysUntilExpiry, String status) {
    }

    public record MissingPiece(String entityType, UUID entityId, String entityName, String docType, String state) {
    }

    public record ComplianceReport(UUID managerId, int totalDocuments, int validDocuments, int expiringSoonDocuments,
                                   int expiredDocuments, int missingDocuments, int pendingDocuments,
                                   int rejectedDocuments, int compliantTargets, int totalTargets, double complianceRate) {
    }

    private record Target(TargetType type, UUID id, String name, UUID companyId) {
    }

    // ── Dépôt ────────────────────────────────────────────────────────────────

    @Transactional
    public Mono<DocumentEntity> submit(UUID actorId, TargetType type, UUID rawTargetId, SubmitCommand cmd) {
        return localTarget(type, rawTargetId).flatMap(targetId -> doSubmit(actorId, type, targetId, cmd));
    }

    /** Une personne peut être désignée par son identifiant Kernel : le dossier porte l'identifiant local. */
    private Mono<UUID> localTarget(TargetType type, UUID targetId) {
        return type == TargetType.VEHICLE ? Mono.just(targetId) : accessScope.resolveLocalUserId(targetId);
    }

    private Mono<DocumentEntity> doSubmit(UUID actorId, TargetType type, UUID targetId, SubmitCommand cmd) {
        return access.assertCanSubmit(actorId, type, targetId)
                .then(validate(type, targetId, cmd))
                .then(accessScope.resolveLocalUserId(actorId))
                .flatMap(localId -> closeRejected(type, targetId, cmd.docType())
                        .then(pendingConflict(type, targetId, cmd.docType()))
                        .then(documents.maxVersion(type.name(), targetId, cmd.docType()).defaultIfEmpty(0))
                        .flatMap(max -> {
                            LocalDateTime now = LocalDateTime.now();
                            DocumentEntity doc = new DocumentEntity();
                            doc.setId(UUID.randomUUID());
                            doc.setTargetType(type.name());
                            doc.setTargetId(targetId);
                            doc.setDocType(cmd.docType());
                            doc.setVersion(max + 1);
                            doc.setReviewStatus("PENDING");
                            doc.setSubmittedBy(localId);
                            doc.setCreatedAt(now);
                            doc.setUpdatedAt(now);
                            doc.setNew(true);
                            applyDetails(doc, cmd);
                            return documents.save(doc);
                        })
                        .flatMap(saved -> hub.attach(type.name(), targetId, saved.getKernelFileId(), saved.getDocType(), saved.getDocNumber())
                                .flatMap(linkId -> {
                                    saved.setKernelLinkId(linkId);
                                    saved.setNew(false);
                                    return documents.save(saved);
                                })
                                .defaultIfEmpty(saved))
                        .flatMap(saved -> notifyReviewers(saved, localId).thenReturn(saved)));
    }

    /** Correction d'une pièce encore en attente de contrôle (l'historique d'une pièce traitée reste intact). */
    @Transactional
    public Mono<DocumentEntity> update(UUID actorId, UUID documentId, SubmitCommand cmd) {
        return find(documentId).flatMap(doc -> {
            TargetType type = TargetType.valueOf(doc.getTargetType());
            if (!"PENDING".equals(doc.getReviewStatus()) || doc.getArchivedAt() != null) {
                return Mono.<DocumentEntity>error(DocumentException.notPending());
            }
            SubmitCommand merged = new SubmitCommand(doc.getDocType(),
                    cmd.docNumber() != null ? cmd.docNumber() : doc.getDocNumber(),
                    cmd.licenseCategories() != null ? cmd.licenseCategories() : doc.getLicenseCategories(),
                    cmd.issuer() != null ? cmd.issuer() : doc.getIssuer(),
                    cmd.issueDate() != null ? cmd.issueDate() : doc.getIssueDate(),
                    cmd.expiryDate() != null ? cmd.expiryDate() : doc.getExpiryDate(),
                    cmd.fileUrl() != null && !cmd.fileUrl().isBlank() ? cmd.fileUrl() : doc.getFileUrl(),
                    cmd.fileOriginalName() != null ? cmd.fileOriginalName() : doc.getFileOriginalName(),
                    cmd.fileMimeType() != null ? cmd.fileMimeType() : doc.getFileMimeType(),
                    cmd.fileSizeBytes() != null ? cmd.fileSizeBytes() : doc.getFileSizeBytes(),
                    cmd.kernelFileId() != null ? cmd.kernelFileId() : doc.getKernelFileId(),
                    cmd.notes() != null ? cmd.notes() : doc.getNotes());
            return access.assertCanSubmit(actorId, type, doc.getTargetId())
                    .then(validate(type, doc.getTargetId(), merged))
                    .then(Mono.defer(() -> {
                        applyDetails(doc, merged);
                        doc.setUpdatedAt(LocalDateTime.now());
                        doc.setNew(false);
                        return documents.save(doc);
                    }));
        });
    }

    // ── Contrôle ─────────────────────────────────────────────────────────────

    @Transactional
    public Mono<DocumentEntity> approve(UUID actorId, UUID documentId, ApproveCommand cmd) {
        return find(documentId).flatMap(doc -> {
            TargetType type = TargetType.valueOf(doc.getTargetType());
            if (!"PENDING".equals(doc.getReviewStatus()) || doc.getArchivedAt() != null) {
                return Mono.<DocumentEntity>error(DocumentException.notPending());
            }
            // Le contrôleur lit la pièce : il peut corriger l'échéance et les catégories.
            if (cmd != null && cmd.expiryDate() != null) doc.setExpiryDate(cmd.expiryDate());
            if (cmd != null && cmd.licenseCategories() != null) doc.setLicenseCategories(cmd.licenseCategories());
            if (cmd != null && cmd.notes() != null) doc.setNotes(cmd.notes());
            return access.assertCanReview(actorId, type, doc.getTargetId())
                    .then(checkDetails(type, doc.getDocType(), doc.getLicenseCategories(), doc.getIssueDate(), doc.getExpiryDate()))
                    .then(accessScope.resolveLocalUserId(actorId))
                    .flatMap(localId -> {
                        LocalDateTime now = LocalDateTime.now();
                        Mono<Void> retire = DocumentCatalog.OTHER.equals(doc.getDocType())
                                ? Mono.empty()
                                : documents.findCurrent(type.name(), doc.getTargetId(), doc.getDocType())
                                        .concatMap(old -> {
                                            old.setCurrent(false);
                                            old.setSupersededBy(doc.getId());
                                            old.setSupersededAt(now);
                                            old.setUpdatedAt(now);
                                            old.setNew(false);
                                            return documents.save(old);
                                        })
                                        .then();
                        return retire.then(Mono.defer(() -> {
                            doc.setReviewStatus("APPROVED");
                            doc.setCurrent(true);
                            doc.setRejectionReason(null);
                            doc.setReviewedBy(localId);
                            doc.setReviewedAt(now);
                            doc.setUpdatedAt(now);
                            doc.setNew(false);
                            return documents.save(doc);
                        }));
                    })
                    .flatMap(saved -> hub.review(saved.getKernelLinkId(), "APPROVED", saved.getExpiryDate(), saved.getNotes())
                            .then(notifyOutcome(saved, "Document approuvé", "a été approuvé.", "INFO"))
                            .thenReturn(saved));
        });
    }

    @Transactional
    public Mono<DocumentEntity> reject(UUID actorId, UUID documentId, String reason) {
        if (reason == null || reason.isBlank()) {
            return Mono.error(DocumentException.reasonRequired());
        }
        return find(documentId).flatMap(doc -> {
            TargetType type = TargetType.valueOf(doc.getTargetType());
            if (!"PENDING".equals(doc.getReviewStatus()) || doc.getArchivedAt() != null) {
                return Mono.<DocumentEntity>error(DocumentException.notPending());
            }
            return access.assertCanReview(actorId, type, doc.getTargetId())
                    .then(accessScope.resolveLocalUserId(actorId))
                    .flatMap(localId -> {
                        LocalDateTime now = LocalDateTime.now();
                        doc.setReviewStatus("REJECTED");
                        doc.setRejectionReason(reason.trim());
                        doc.setReviewedBy(localId);
                        doc.setReviewedAt(now);
                        doc.setUpdatedAt(now);
                        doc.setNew(false);
                        return documents.save(doc);
                    })
                    .flatMap(saved -> hub.review(saved.getKernelLinkId(), "REJECTED", null, reason)
                            .then(notifyOutcome(saved, "Document rejeté", "a été rejeté : " + reason.trim(), "WARNING"))
                            .thenReturn(saved));
        });
    }

    /**
     * Retrait du dossier, jamais d'effacement. Le déposant peut retirer son dépôt en attente ; une
     * pièce traitée ne s'archive que par un contrôleur, avec un motif.
     */
    @Transactional
    public Mono<Void> archive(UUID actorId, UUID documentId, String reason) {
        return find(documentId).flatMap(doc -> accessScope.resolveLocalUserId(actorId).flatMap(localId -> {
            TargetType type = TargetType.valueOf(doc.getTargetType());
            if (doc.getArchivedAt() != null) {
                return Mono.<Void>error(DocumentException.notFound(documentId));
            }
            boolean ownPending = "PENDING".equals(doc.getReviewStatus()) && localId.equals(doc.getSubmittedBy());
            if (!ownPending && (reason == null || reason.isBlank())) {
                return Mono.<Void>error(DocumentException.reasonRequired());
            }
            Mono<Void> allowed = ownPending ? Mono.empty() : access.assertCanReview(actorId, type, doc.getTargetId());
            return allowed.then(Mono.defer(() -> {
                LocalDateTime now = LocalDateTime.now();
                doc.setCurrent(false);
                doc.setArchivedAt(now);
                doc.setArchiveReason(reason != null && !reason.isBlank() ? reason.trim() : "Dépôt retiré");
                doc.setUpdatedAt(now);
                doc.setNew(false);
                return documents.save(doc).then();
            }));
        }));
    }

    // ── Lecture d'un dossier ─────────────────────────────────────────────────

    public Flux<DocumentEntity> listActive(UUID actorId, TargetType type, UUID rawTargetId) {
        return localTarget(type, rawTargetId).flatMapMany(targetId -> access.assertCanRead(actorId, type, targetId)
                .thenMany(documents.findActive(type.name(), targetId)));
    }

    public Flux<DocumentEntity> history(UUID actorId, TargetType type, UUID rawTargetId) {
        return localTarget(type, rawTargetId).flatMapMany(targetId -> access.assertCanRead(actorId, type, targetId)
                .thenMany(documents.findHistory(type.name(), targetId)));
    }

    public Mono<DocumentEntity> getById(UUID actorId, UUID documentId) {
        return find(documentId).flatMap(doc -> access
                .assertCanRead(actorId, TargetType.valueOf(doc.getTargetType()), doc.getTargetId())
                .thenReturn(doc));
    }

    /** Checklist du dossier (équivalent du checklist du Kernel) : pièces exigées d'abord. */
    public Flux<ChecklistItem> checklist(UUID actorId, TargetType type, UUID rawTargetId) {
        return localTarget(type, rawTargetId).flatMapMany(targetId -> access.assertCanRead(actorId, type, targetId)
                .then(Mono.zip(compliance.requiredTypes(type, targetId),
                        documents.findActive(type.name(), targetId).collectList()))
                .flatMapIterable(t -> buildChecklist(type, t.getT1(), t.getT2())));
    }

    private static List<ChecklistItem> buildChecklist(TargetType type, Set<String> required, List<DocumentEntity> active) {
        LocalDate today = LocalDate.now();
        List<ChecklistItem> items = new ArrayList<>();
        for (DocumentCatalog.Entry entry : DocumentCatalog.entries(type)) {
            if (DocumentCatalog.OTHER.equals(entry.docType())) continue;
            List<DocumentEntity> ofType = active.stream().filter(d -> d.getDocType().equals(entry.docType())).toList();
            DocumentEntity inForce = ofType.stream().filter(DocumentEntity::isCurrent).findFirst().orElse(null);
            DocumentEntity latest = inForce != null ? inForce : ofType.stream().findFirst().orElse(null);
            items.add(new ChecklistItem(entry.docType(), entry.legal(), required.contains(entry.docType()),
                    DocumentRules.checklistState(ofType.stream().map(DocumentEntity::toPiece).toList(), today),
                    inForce != null ? inForce.getExpiryDate() : null,
                    latest != null ? latest.getId() : null,
                    inForce != null && ofType.stream().anyMatch(d -> "PENDING".equals(d.getReviewStatus()))));
        }
        items.sort(Comparator.comparing(ChecklistItem::mandatory).reversed());
        return items;
    }

    // ── Vues transversales ───────────────────────────────────────────────────

    /** File de contrôle, la plus ancienne d'abord, sans les pièces que l'acteur a sur lui-même. */
    public Flux<Map.Entry<DocumentEntity, String>> pending(UUID actorId) {
        return activeInScope(actorId).flatMapIterable(s -> s.docs().stream()
                .filter(d -> "PENDING".equals(d.getReviewStatus()))
                .sorted(Comparator.comparing(DocumentEntity::getCreatedAt))
                .map(d -> Map.entry(d, s.nameOf(d.getTargetId())))
                .toList());
    }

    public Flux<TargetDocument> expiring(UUID actorId, int withinDays) {
        LocalDate today = LocalDate.now();
        return inForce(actorId)
                .filter(d -> !d.expiryDate().isBefore(today) && !d.expiryDate().isAfter(today.plusDays(withinDays)));
    }

    public Flux<TargetDocument> expired(UUID actorId) {
        LocalDate today = LocalDate.now();
        return inForce(actorId).filter(d -> d.expiryDate().isBefore(today));
    }

    private Flux<TargetDocument> inForce(UUID actorId) {
        LocalDate today = LocalDate.now();
        return activeInScope(actorId).flatMapIterable(s -> s.docs().stream()
                .filter(d -> d.isCurrent() && d.getExpiryDate() != null)
                .map(d -> new TargetDocument(d.getId(), d.getTargetType(), d.getTargetId(), s.nameOf(d.getTargetId()),
                        d.getDocType(), d.getDocNumber(), d.getExpiryDate(),
                        ChronoUnit.DAYS.between(today, d.getExpiryDate()), d.effectiveStatus(today)))
                .sorted(Comparator.comparingLong(TargetDocument::daysUntilExpiry))
                .toList());
    }

    public Flux<MissingPiece> missing(UUID actorId) {
        return scopeWithRequired(actorId).flatMapIterable(s -> {
            List<MissingPiece> out = new ArrayList<>();
            LocalDate today = LocalDate.now();
            for (Target t : s.targets()) {
                for (String type : s.required(t)) {
                    String state = DocumentRules.checklistState(s.piecesOf(t.id(), type), today);
                    // Les expirées et celles en attente ont leur propre vue.
                    if ("MISSING".equals(state) || "REJECTED".equals(state)) {
                        out.add(new MissingPiece(t.type().name(), t.id(), t.name(), type, state));
                    }
                }
            }
            return out;
        });
    }

    /**
     * Le taux porte sur les pièces EXIGÉES de chaque cible : une pièce manquante le fait baisser
     * (l'ancien calcul ne voyait que les documents enregistrés : aucun document = 100 %).
     */
    public Mono<ComplianceReport> report(UUID actorId) {
        return scopeWithRequired(actorId).next().zipWith(accessScope.resolveLocalUserId(actorId)).map(tuple -> {
            Scope s = tuple.getT1();
            LocalDate today = LocalDate.now();
            int total = 0, ok = 0, expiring = 0, expired = 0, missing = 0, pending = 0, rejected = 0, compliant = 0;
            for (Target t : s.targets()) {
                boolean clean = true;
                for (String type : s.required(t)) {
                    total++;
                    switch (DocumentRules.checklistState(s.piecesOf(t.id(), type), today)) {
                        case "OK" -> ok++;
                        case "EXPIRING" -> expiring++;
                        case "EXPIRED" -> { expired++; clean = false; }
                        case "PENDING" -> { pending++; clean = false; }
                        case "REJECTED" -> { rejected++; clean = false; }
                        default -> { missing++; clean = false; }
                    }
                }
                if (clean) compliant++;
            }
            double rate = total == 0 ? 100.0 : BigDecimal.valueOf((ok + expiring) * 100.0 / total)
                    .setScale(1, RoundingMode.HALF_UP).doubleValue();
            return new ComplianceReport(tuple.getT2(), total, ok, expiring, expired, missing, pending, rejected,
                    compliant, s.targets().size(), rate);
        });
    }

    // ── Politique de l'entreprise ────────────────────────────────────────────

    public Flux<CatalogEntry> catalog(UUID actorId) {
        return companyScope.resolveCompanyId(actorId)
                .flatMapMany(companyId -> Flux.fromArray(TargetType.values())
                        .concatMap(type -> compliance.requiredTypesOfCompany(type, companyId)
                                .flatMapIterable(required -> entriesOf(type, required))))
                .switchIfEmpty(Flux.fromArray(TargetType.values())
                        .flatMapIterable(type -> entriesOf(type, Set.copyOf(DocumentCatalog.legalTypes(type)))));
    }

    private static List<CatalogEntry> entriesOf(TargetType type, Set<String> required) {
        return DocumentCatalog.entries(type).stream()
                .map(e -> new CatalogEntry(type.name(), e.docType(), e.legal(), required.contains(e.docType()), e.expiryRequired()))
                .toList();
    }

    /** Réservé à l'administrateur de l'entreprise. Le socle légal ne se désactive pas. */
    @Transactional
    public Mono<Void> setPolicy(UUID actorId, TargetType type, String docType, boolean mandatory) {
        DocumentCatalog.Entry entry = DocumentCatalog.find(type, docType).orElse(null);
        if (entry == null || DocumentCatalog.OTHER.equals(docType)) {
            return Mono.error(DocumentException.invalidDocType(docType));
        }
        if (entry.legal() && !mandatory) {
            return Mono.error(DocumentException.legalPieceAlwaysMandatory(entry.label()));
        }
        return companyScope.requireOwnCompany(actorId)
                .flatMap(companyId -> db.sql("""
                                INSERT INTO fleet.document_policies (company_id, target_type, doc_type, mandatory, updated_by, updated_at)
                                VALUES (:c, :t, :d, :m, :c, :now)
                                ON CONFLICT (company_id, target_type, doc_type)
                                DO UPDATE SET mandatory = :m, updated_by = :c, updated_at = :now
                                """)
                        .bind("c", companyId)
                        .bind("t", type.name())
                        .bind("d", docType)
                        .bind("m", mandatory)
                        .bind("now", LocalDateTime.now())
                        .fetch().rowsUpdated())
                .then(hub.upsertPolicy(type.name(), docType, mandatory));
    }

    // ── Périmètre de l'acteur ────────────────────────────────────────────────

    private record Scope(List<Target> targets, Map<UUID, List<DocumentEntity>> byTarget, Map<UUID, Set<String>> vehicleRequired,
                         Map<UUID, Set<String>> driverRequired, Map<UUID, Set<String>> managerRequired) {
        List<DocumentEntity> docs() {
            return byTarget.values().stream().flatMap(List::stream).toList();
        }

        String nameOf(UUID id) {
            return targets.stream().filter(t -> t.id().equals(id)).map(Target::name).findFirst().orElse("—");
        }

        Set<String> required(Target t) {
            Map<UUID, Set<String>> map = switch (t.type()) {
                case VEHICLE -> vehicleRequired;
                case DRIVER -> driverRequired;
                case MANAGER -> managerRequired;
            };
            return map.getOrDefault(t.companyId(), Set.copyOf(DocumentCatalog.legalTypes(t.type())));
        }

        List<DocumentRules.Piece> piecesOf(UUID targetId, String docType) {
            return byTarget.getOrDefault(targetId, List.of()).stream()
                    .filter(d -> d.getDocType().equals(docType))
                    .map(DocumentEntity::toPiece)
                    .toList();
        }
    }

    private Flux<Scope> activeInScope(UUID actorId) {
        return scopeWithRequired(actorId);
    }

    private Flux<Scope> scopeWithRequired(UUID actorId) {
        return accessScope.resolveLocalUserId(actorId).flatMap(localId -> targetsOf(localId).collectList().flatMap(targets -> {
            List<UUID> ids = targets.stream().map(Target::id).toList();
            Mono<List<DocumentEntity>> docs = ids.isEmpty()
                    ? Mono.just(List.<DocumentEntity>of())
                    : documents.findActiveForTargets(ids).collectList();
            Set<UUID> companies = targets.stream().map(Target::companyId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
            return Mono.zip(docs, requiredByCompany(TargetType.VEHICLE, companies), requiredByCompany(TargetType.DRIVER, companies),
                    requiredByCompany(TargetType.MANAGER, companies)).map(t -> {
                // Un identifiant n'appartient qu'à une cible : un chauffeur et un gestionnaire sont des personnes distinctes.
                Map<UUID, List<DocumentEntity>> byTarget = new HashMap<>();
                Map<UUID, TargetType> typeOf = targets.stream().collect(Collectors.toMap(Target::id, Target::type, (a, b) -> a));
                for (DocumentEntity d : t.getT1()) {
                    if (typeOf.get(d.getTargetId()) != null && typeOf.get(d.getTargetId()).name().equals(d.getTargetType())) {
                        byTarget.computeIfAbsent(d.getTargetId(), k -> new ArrayList<>()).add(d);
                    }
                }
                return new Scope(targets, byTarget, t.getT2(), t.getT3(), t.getT4());
            });
        })).flux();
    }

    private Mono<Map<UUID, Set<String>>> requiredByCompany(TargetType type, Set<UUID> companies) {
        return Flux.fromIterable(companies)
                .flatMap(c -> compliance.requiredTypesOfCompany(type, c).map(req -> Map.entry(c, req)))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    private Flux<Target> targetsOf(UUID localId) {
        return db.sql("""
                WITH f AS (SELECT id, company_id FROM fleet.fleets
                           WHERE deleted_at IS NULL AND (manager_id = :a OR company_id = :a))
                SELECT 'VEHICLE' AS target_type, v.id AS target_id, v.license_plate AS name, f.company_id AS company_id
                FROM fleet.vehicles v JOIN f ON f.id = v.fleet_id
                WHERE v.deleted_at IS NULL AND v.status <> 'RETIRED'
                UNION ALL
                SELECT 'DRIVER', d.user_id, TRIM(COALESCE(u.first_name, '') || ' ' || COALESCE(u.last_name, '')), f.company_id
                FROM fleet.drivers d JOIN f ON f.id = d.fleet_id JOIN fleet.users u ON u.id = d.user_id
                WHERE u.deleted_at IS NULL AND d.deleted_at IS NULL AND COALESCE(d.status, 'ACTIVE') <> 'LEFT'
                UNION ALL
                SELECT 'MANAGER', fm.user_id, TRIM(COALESCE(u.first_name, '') || ' ' || COALESCE(u.last_name, '')), fm.created_by_admin_id
                FROM fleet.fleet_managers fm JOIN fleet.users u ON u.id = fm.user_id
                WHERE fm.created_by_admin_id = :a AND fm.account_status <> 'CLOSED'
                """)
                .bind("a", localId)
                .map(row -> new Target(TargetType.valueOf(row.get("target_type", String.class)),
                        row.get("target_id", UUID.class), row.get("name", String.class), row.get("company_id", UUID.class)))
                .all();
    }

    // ── Règles communes ──────────────────────────────────────────────────────

    private Mono<DocumentEntity> find(UUID documentId) {
        return documents.findById(documentId).switchIfEmpty(Mono.error(DocumentException.notFound(documentId)));
    }

    private Mono<Void> validate(TargetType type, UUID targetId, SubmitCommand cmd) {
        if (cmd.docType() == null || cmd.docType().isBlank()) {
            return Mono.error(DocumentException.docTypeRequired());
        }
        if (cmd.fileUrl() == null || !cmd.fileUrl().startsWith(FILE_URL_PREFIX)) {
            return Mono.error(DocumentException.fileRequired());
        }
        return checkDetails(type, cmd.docType(), cmd.licenseCategories(), cmd.issueDate(), cmd.expiryDate())
                .then(documents.fileUsedByAnotherTarget(cmd.fileUrl(), type.name(), targetId))
                .flatMap(used -> Boolean.TRUE.equals(used)
                        ? Mono.<Void>error(DocumentException.fileBelongsToAnotherTarget())
                        : Mono.<Void>empty());
    }

    private Mono<Void> checkDetails(TargetType type, String docType, String categories, LocalDate issue, LocalDate expiry) {
        DocumentCatalog.Entry entry = DocumentCatalog.find(type, docType).orElse(null);
        if (entry == null) {
            return Mono.error(DocumentException.invalidDocType(docType));
        }
        if (entry.expiryRequired() && expiry == null) {
            return Mono.error(DocumentException.expiryDateRequired());
        }
        LocalDate today = LocalDate.now();
        if (expiry != null && expiry.isBefore(today)) {
            return Mono.error(DocumentException.alreadyExpired());
        }
        if (issue != null && issue.isAfter(today)) {
            return Mono.error(DocumentException.invalidDates("La date de délivrance ne peut pas être dans le futur."));
        }
        if (issue != null && expiry != null && !expiry.isAfter(issue)) {
            return Mono.error(DocumentException.invalidDates("La date d'expiration doit suivre la date de délivrance."));
        }
        if (DocumentCatalog.DRIVING_LICENSE.equals(docType) && (categories == null || categories.isBlank())) {
            return Mono.error(DocumentException.licenseCategoriesRequired());
        }
        return Mono.empty();
    }

    private static void applyDetails(DocumentEntity doc, SubmitCommand cmd) {
        doc.setDocNumber(cmd.docNumber());
        doc.setLicenseCategories(cmd.licenseCategories());
        doc.setIssuer(cmd.issuer());
        doc.setIssueDate(cmd.issueDate());
        doc.setExpiryDate(cmd.expiryDate());
        doc.setFileUrl(cmd.fileUrl());
        doc.setFileOriginalName(cmd.fileOriginalName());
        doc.setFileMimeType(cmd.fileMimeType());
        doc.setFileSizeBytes(cmd.fileSizeBytes());
        doc.setKernelFileId(cmd.kernelFileId());
        doc.setNotes(cmd.notes());
    }

    /** Un nouveau dépôt clôt les rejets précédents de la même pièce (ils restent dans l'historique). */
    private Mono<Void> closeRejected(TargetType type, UUID targetId, String docType) {
        if (DocumentCatalog.OTHER.equals(docType)) return Mono.empty();
        return db.sql("""
                UPDATE fleet.documents SET archived_at = :now, archive_reason = 'Remplacé par un nouveau dépôt', updated_at = :now
                WHERE target_type = :t AND target_id = :id AND doc_type = :d AND review_status = 'REJECTED' AND archived_at IS NULL
                """)
                .bind("now", LocalDateTime.now())
                .bind("t", type.name())
                .bind("id", targetId)
                .bind("d", docType)
                .fetch().rowsUpdated().then();
    }

    private Mono<Void> pendingConflict(TargetType type, UUID targetId, String docType) {
        if (DocumentCatalog.OTHER.equals(docType)) return Mono.empty();
        return documents.findActive(type.name(), targetId)
                .filter(d -> d.getDocType().equals(docType) && "PENDING".equals(d.getReviewStatus()))
                .hasElements()
                .flatMap(has -> has ? Mono.<Void>error(DocumentException.pendingExists()) : Mono.<Void>empty());
    }

    // ── Notifications ────────────────────────────────────────────────────────

    /** Prévient les contrôleurs d'un dépôt à examiner, sauf si c'est l'un d'eux qui l'a fait. */
    private Mono<Void> notifyReviewers(DocumentEntity doc, UUID submitterId) {
        String sql = switch (TargetType.valueOf(doc.getTargetType())) {
            case VEHICLE -> "SELECT f.manager_id AS u FROM fleet.vehicles v JOIN fleet.fleets f ON f.id = v.fleet_id WHERE v.id = :id";
            case DRIVER -> "SELECT f.manager_id AS u FROM fleet.drivers d JOIN fleet.fleets f ON f.id = d.fleet_id WHERE d.user_id = :id";
            case MANAGER -> "SELECT created_by_admin_id AS u FROM fleet.fleet_managers WHERE user_id = :id";
        };
        return db.sql(sql).bind("id", doc.getTargetId()).map(row -> row.get("u", UUID.class)).one()
                .filter(reviewer -> !reviewer.equals(submitterId))
                .flatMap(reviewer -> insertNotification(reviewer, "Document à contrôler",
                        "Nouveau dépôt à contrôler : " + describe(doc) + ".", "INFO", doc))
                .then();
    }

    private Mono<Void> notifyOutcome(DocumentEntity doc, String title, String outcome, String type) {
        UUID person = TargetType.valueOf(doc.getTargetType()) == TargetType.VEHICLE ? null : doc.getTargetId();
        return Flux.fromIterable(java.util.stream.Stream.of(doc.getSubmittedBy(), person)
                        .filter(java.util.Objects::nonNull)
                        .filter(u -> !u.equals(doc.getReviewedBy()))
                        .distinct()
                        .toList())
                .concatMap(u -> insertNotification(u, title, "Votre document (" + describe(doc) + ") " + outcome, type, doc))
                .then();
    }

    private static String describe(DocumentEntity doc) {
        return DocumentCatalog.label(TargetType.valueOf(doc.getTargetType()), doc.getDocType());
    }

    private Mono<Long> insertNotification(UUID userId, String title, String message, String type, DocumentEntity doc) {
        return db.sql("""
                INSERT INTO fleet.notifications (id, user_id, title, message, type, is_read, created_at, data, tenant_id)
                SELECT gen_random_uuid(), u.id, :title, :message, :type, FALSE, :now, :data, u.tenant_id
                FROM fleet.users u WHERE u.id = :user
                """)
                .bind("title", title)
                .bind("message", message)
                .bind("type", type)
                .bind("now", LocalDateTime.now())
                .bind("data", "{\"type\":\"DOCUMENT\",\"documentId\":\"" + doc.getId() + "\"}")
                .bind("user", userId)
                .fetch().rowsUpdated();
    }
}
