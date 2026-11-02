package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.application.service.DocumentService;
import com.yowyob.fleet.domain.model.DocumentCatalog.TargetType;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.DocumentResponse;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Dossiers documentaires des véhicules, chauffeurs et gestionnaires. Les droits fins (qui lit,
 * dépose, contrôle) sont dans DocumentAccessService.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "17. Documents")
public class DocumentController {

    private static final String KIND = "{kind:vehicles|drivers|managers}";
    private static final String ANY_ROLE = "hasAnyRole('FLEET_DRIVER', 'FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')";
    private static final String STAFF = "hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')";

    private final DocumentService documents;

    public record DocumentRequest(
            String docType, String docNumber, String licenseCategories, String issuer, LocalDate issueDate,
            LocalDate expiryDate, String fileUrl, String fileOriginalName, String fileMimeType, Long fileSizeBytes,
            UUID kernelFileId, String notes) {

        DocumentService.SubmitCommand toCommand() {
            return new DocumentService.SubmitCommand(docType, docNumber, licenseCategories, issuer, issueDate, expiryDate,
                    fileUrl, fileOriginalName, fileMimeType, fileSizeBytes, kernelFileId, notes);
        }
    }

    public record ApproveRequest(LocalDate expiryDate, String licenseCategories, String notes) {
    }

    public record RejectRequest(@NotBlank(message = "Indiquez le motif du rejet.") String reason) {
    }

    public record PolicyRequest(@NotNull Boolean mandatory) {
    }

    private static UUID userId(Authentication auth) {
        return ((AuthPort.UserDetail) auth.getPrincipal()).id();
    }

    private static TargetType targetType(String kind) {
        return switch (kind) {
            case "vehicles" -> TargetType.VEHICLE;
            case "drivers" -> TargetType.DRIVER;
            default -> TargetType.MANAGER;
        };
    }

    // ── Dossier d'une cible ──────────────────────────────────────────────────

    @PostMapping("/" + KIND + "/{targetId}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(ANY_ROLE)
    @Operation(summary = "Déposer une pièce (nouvelle ou renouvellement)",
               description = "Le fichier est obligatoire. La pièce reste en attente jusqu'au contrôle ; "
                       + "la version précédente reste en vigueur jusqu'à l'approbation.")
    public Mono<DocumentResponse> submit(@PathVariable String kind, @PathVariable UUID targetId,
                                         @RequestBody DocumentRequest request, Authentication auth) {
        return documents.submit(userId(auth), targetType(kind), targetId, request.toCommand()).map(DocumentResponse::from);
    }

    @GetMapping("/" + KIND + "/{targetId}/documents")
    @PreAuthorize(ANY_ROLE)
    @Operation(summary = "Dossier en cours (ni archivé, ni remplacé)")
    public Mono<PageResponse<DocumentResponse>> list(@PathVariable String kind, @PathVariable UUID targetId, Authentication auth,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "50") int size) {
        return PageResponse.of(documents.listActive(userId(auth), targetType(kind), targetId).map(DocumentResponse::from), page, size);
    }

    @GetMapping("/" + KIND + "/{targetId}/documents/history")
    @PreAuthorize(ANY_ROLE)
    @Operation(summary = "Toutes les versions, archives comprises")
    public Flux<DocumentResponse> history(@PathVariable String kind, @PathVariable UUID targetId, Authentication auth) {
        return documents.history(userId(auth), targetType(kind), targetId).map(DocumentResponse::from);
    }

    @GetMapping("/" + KIND + "/{targetId}/documents/checklist")
    @PreAuthorize(ANY_ROLE)
    @Operation(summary = "Checklist : état de chaque pièce, obligatoire ou non")
    public Flux<DocumentService.ChecklistItem> checklist(@PathVariable String kind, @PathVariable UUID targetId, Authentication auth) {
        return documents.checklist(userId(auth), targetType(kind), targetId);
    }

    // ── Une pièce ────────────────────────────────────────────────────────────

    @GetMapping("/documents/{id:[0-9a-fA-F-]{36}}")
    @PreAuthorize(ANY_ROLE)
    public Mono<DocumentResponse> get(@PathVariable UUID id, Authentication auth) {
        return documents.getById(userId(auth), id).map(DocumentResponse::from);
    }

    @PutMapping("/documents/{id:[0-9a-fA-F-]{36}}")
    @PreAuthorize(ANY_ROLE)
    @Operation(summary = "Corriger une pièce encore en attente de contrôle")
    public Mono<DocumentResponse> update(@PathVariable UUID id, @RequestBody DocumentRequest request, Authentication auth) {
        return documents.update(userId(auth), id, request.toCommand()).map(DocumentResponse::from);
    }

    @PostMapping("/documents/{id:[0-9a-fA-F-]{36}}/approve")
    @PreAuthorize(STAFF)
    @Operation(summary = "Approuver une pièce", description = "Elle entre en vigueur et remplace la version précédente. "
            + "Le contrôleur peut corriger l'échéance et les catégories lues sur la pièce.")
    public Mono<DocumentResponse> approve(@PathVariable UUID id, @RequestBody(required = false) ApproveRequest request, Authentication auth) {
        DocumentService.ApproveCommand cmd = request != null
                ? new DocumentService.ApproveCommand(request.expiryDate(), request.licenseCategories(), request.notes())
                : null;
        return documents.approve(userId(auth), id, cmd).map(DocumentResponse::from);
    }

    @PostMapping("/documents/{id:[0-9a-fA-F-]{36}}/reject")
    @PreAuthorize(STAFF)
    @Operation(summary = "Rejeter une pièce avec un motif")
    public Mono<DocumentResponse> reject(@PathVariable UUID id, @Valid @RequestBody RejectRequest request, Authentication auth) {
        return documents.reject(userId(auth), id, request.reason()).map(DocumentResponse::from);
    }

    @DeleteMapping("/documents/{id:[0-9a-fA-F-]{36}}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(ANY_ROLE)
    @Operation(summary = "Retirer une pièce du dossier (archivage, jamais d'effacement)",
               description = "Le motif est obligatoire sauf pour retirer son propre dépôt en attente.")
    public Mono<Void> archive(@PathVariable UUID id, @RequestParam(required = false) String reason, Authentication auth) {
        return documents.archive(userId(auth), id, reason);
    }

    // ── Vues transversales ───────────────────────────────────────────────────

    @GetMapping("/documents/pending")
    @PreAuthorize(STAFF)
    @Operation(summary = "File des pièces à contrôler")
    public Flux<DocumentResponse> pending(Authentication auth) {
        return documents.pending(userId(auth)).map(e -> DocumentResponse.from(e.getKey()).withTargetName(e.getValue()));
    }

    @GetMapping("/documents/expiring")
    @PreAuthorize(STAFF)
    public Mono<PageResponse<DocumentService.TargetDocument>> expiring(Authentication auth,
                                                                       @RequestParam(defaultValue = "30") int withinDays,
                                                                       @RequestParam(defaultValue = "0") int page,
                                                                       @RequestParam(defaultValue = "50") int size) {
        return PageResponse.of(documents.expiring(userId(auth), withinDays), page, size);
    }

    @GetMapping("/documents/expired")
    @PreAuthorize(STAFF)
    public Mono<PageResponse<DocumentService.TargetDocument>> expired(Authentication auth,
                                                                      @RequestParam(defaultValue = "0") int page,
                                                                      @RequestParam(defaultValue = "50") int size) {
        return PageResponse.of(documents.expired(userId(auth)), page, size);
    }

    @GetMapping("/documents/missing")
    @PreAuthorize(STAFF)
    @Operation(summary = "Pièces exigées absentes, en attente ou rejetées, par véhicule ou personne")
    public Flux<DocumentService.MissingPiece> missing(Authentication auth) {
        return documents.missing(userId(auth));
    }

    @GetMapping("/documents/compliance-report")
    @PreAuthorize(STAFF)
    @Operation(summary = "Taux de conformité calculé sur les pièces exigées")
    public Mono<DocumentService.ComplianceReport> report(Authentication auth) {
        return documents.report(userId(auth));
    }

    // ── Catalogue et politique de l'entreprise ───────────────────────────────

    @GetMapping("/documents/catalog")
    @PreAuthorize(ANY_ROLE)
    @Operation(summary = "Pièces connues et celles qu'exige l'entreprise")
    public Mono<List<DocumentService.CatalogEntry>> catalog(Authentication auth) {
        return documents.catalog(userId(auth)).collectList();
    }

    @PutMapping("/documents/policies/{targetType}/{docType}")
    @PreAuthorize("hasRole('FLEET_ADMIN')")
    @Operation(summary = "Rendre une pièce obligatoire ou non pour l'entreprise (administrateur)")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> setPolicy(@PathVariable TargetType targetType, @PathVariable String docType,
                                @Valid @RequestBody PolicyRequest request, Authentication auth) {
        return documents.setPolicy(userId(auth), targetType, docType, request.mandatory());
    }
}
