package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.domain.ports.in.ManageDocumentUseCase;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class DocumentController {

    private final ManageDocumentUseCase documentUseCase;

    private UUID getUserId(Authentication auth) {
        return ((AuthPort.UserDetail) auth.getPrincipal()).id();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TAG 17 — DOCUMENTS VÉHICULES
    // ═══════════════════════════════════════════════════════════════════════

    @Tag(name = "17. Documents | Véhicules")
    @PostMapping("/vehicles/{vehicleId}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Ajouter un document à un véhicule",
               description = "Enregistre un document légal (assurance, carte grise, visite technique...). "
                       + "Le statut est calculé automatiquement selon la date d'expiration.")
    public Mono<VehicleDocumentResponse> addVehicleDoc(
            @PathVariable UUID vehicleId,
            @Valid @RequestBody VehicleDocumentRequest request) {

        ManageDocumentUseCase.AddVehicleDocumentCommand cmd =
                new ManageDocumentUseCase.AddVehicleDocumentCommand(
                        vehicleId, request.docType(), request.docNumber(),
                        request.issuer(), request.issueDate(), request.expiryDate(),
                        request.fileUrl(), request.notes()
                );
        return documentUseCase.addVehicleDocument(cmd).map(VehicleDocumentResponse::from);
    }

    @Tag(name = "17. Documents | Véhicules")
    @GetMapping("/vehicles/{vehicleId}/documents")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Documents d'un véhicule",
               description = "Retourne tous les documents légaux d'un véhicule.")
    public Mono<PageResponse<VehicleDocumentResponse>> getVehicleDocs(
            @PathVariable UUID vehicleId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return PageResponse.of(
                documentUseCase.getVehicleDocuments(vehicleId)
                        .map(VehicleDocumentResponse::from),
                page, size
        );
    }

    @Tag(name = "17. Documents | Véhicules")
    @GetMapping("/vehicles/{vehicleId}/documents/{docId}")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Détail d'un document véhicule")
    public Mono<VehicleDocumentResponse> getVehicleDocById(
            @PathVariable UUID vehicleId,
            @PathVariable UUID docId) {
        return documentUseCase.getVehicleDocById(docId).map(VehicleDocumentResponse::from);
    }

    @Tag(name = "17. Documents | Véhicules")
    @PutMapping("/vehicles/{vehicleId}/documents/{docId}")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Mettre à jour un document véhicule",
               description = "Met à jour le fichier, le statut ou les notes d'un document.")
    public Mono<VehicleDocumentResponse> updateVehicleDoc(
            @PathVariable UUID vehicleId,
            @PathVariable UUID docId,
            @RequestBody VehicleDocumentRequest request) {

        ManageDocumentUseCase.UpdateVehicleDocumentCommand cmd =
                new ManageDocumentUseCase.UpdateVehicleDocumentCommand(
                        docId, request.docNumber(), request.issuer(),
                        request.issueDate(), request.expiryDate(),
                        request.fileUrl(), null, request.notes()
                );
        return documentUseCase.updateVehicleDocument(cmd).map(VehicleDocumentResponse::from);
    }

    @Tag(name = "17. Documents | Véhicules")
    @DeleteMapping("/vehicles/{vehicleId}/documents/{docId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Supprimer un document véhicule")
    public Mono<Void> deleteVehicleDoc(
            @PathVariable UUID vehicleId,
            @PathVariable UUID docId) {
        return documentUseCase.deleteVehicleDocument(docId);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TAG 18 — DOCUMENTS CONDUCTEURS
    // ═══════════════════════════════════════════════════════════════════════

    @Tag(name = "18. Documents | Conducteurs")
    @PostMapping("/drivers/{driverId}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Ajouter un document à un conducteur",
               description = "Enregistre un document légal (permis, visite médicale, carte pro...).")
    public Mono<DriverDocumentResponse> addDriverDoc(
            @PathVariable UUID driverId,
            @Valid @RequestBody DriverDocumentRequest request) {

        ManageDocumentUseCase.AddDriverDocumentCommand cmd =
                new ManageDocumentUseCase.AddDriverDocumentCommand(
                        driverId, request.docType(), request.docNumber(),
                        request.licenseCategories(), request.issuer(),
                        request.issueDate(), request.expiryDate(),
                        request.fileUrl(), request.notes()
                );
        return documentUseCase.addDriverDocument(cmd).map(DriverDocumentResponse::from);
    }

    @Tag(name = "18. Documents | Conducteurs")
    @GetMapping("/drivers/{driverId}/documents")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Documents d'un conducteur")
    public Mono<PageResponse<DriverDocumentResponse>> getDriverDocs(
            @PathVariable UUID driverId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return PageResponse.of(
                documentUseCase.getDriverDocuments(driverId)
                        .map(DriverDocumentResponse::from),
                page, size
        );
    }

    @Tag(name = "18. Documents | Conducteurs")
    @GetMapping("/drivers/{driverId}/documents/{docId}")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Détail d'un document conducteur")
    public Mono<DriverDocumentResponse> getDriverDocById(
            @PathVariable UUID driverId,
            @PathVariable UUID docId) {
        return documentUseCase.getDriverDocById(docId).map(DriverDocumentResponse::from);
    }

    @Tag(name = "18. Documents | Conducteurs")
    @PutMapping("/drivers/{driverId}/documents/{docId}")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Mettre à jour un document conducteur")
    public Mono<DriverDocumentResponse> updateDriverDoc(
            @PathVariable UUID driverId,
            @PathVariable UUID docId,
            @RequestBody DriverDocumentRequest request) {

        ManageDocumentUseCase.UpdateDriverDocumentCommand cmd =
                new ManageDocumentUseCase.UpdateDriverDocumentCommand(
                        docId, request.docNumber(), request.licenseCategories(),
                        request.issuer(), request.issueDate(), request.expiryDate(),
                        request.fileUrl(), null, request.notes()
                );
        return documentUseCase.updateDriverDocument(cmd).map(DriverDocumentResponse::from);
    }

    @Tag(name = "18. Documents | Conducteurs")
    @DeleteMapping("/drivers/{driverId}/documents/{docId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Supprimer un document conducteur")
    public Mono<Void> deleteDriverDoc(
            @PathVariable UUID driverId,
            @PathVariable UUID docId) {
        return documentUseCase.deleteDriverDocument(docId);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TAG 19 — CONFORMITÉ DOCUMENTAIRE (vues transversales)
    // ═══════════════════════════════════════════════════════════════════════

    @Tag(name = "19. Documents | Conformité")
    @GetMapping("/documents/expiring")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Documents expirant bientôt",
               description = "Retourne tous les documents (véhicules + conducteurs) "
                       + "expirant dans les N prochains jours. Défaut : 30 jours.")
    public Mono<PageResponse<ManageDocumentUseCase.ExpiringDocumentDto>> getExpiring(
            Authentication auth,
            @Parameter(description = "Nombre de jours", example = "30")
            @RequestParam(defaultValue = "30") int withinDays,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        return PageResponse.of(
                documentUseCase.getExpiringDocuments(getUserId(auth), withinDays),
                page, size
        );
    }

    @Tag(name = "19. Documents | Conformité")
    @GetMapping("/documents/expired")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Documents expirés",
               description = "Retourne tous les documents expirés. Action immédiate requise.")
    public Mono<PageResponse<ManageDocumentUseCase.ExpiringDocumentDto>> getExpired(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        return PageResponse.of(
                documentUseCase.getExpiredDocuments(getUserId(auth)),
                page, size
        );
    }

    @Tag(name = "19. Documents | Conformité")
    @GetMapping("/documents/compliance-report")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Rapport de conformité documentaire",
               description = "Calcule le taux de conformité documentaire de la flotte : "
                       + "nombre de documents valides / total. "
                       + "Un taux < 80% indique un risque légal élevé.")
    public Mono<ManageDocumentUseCase.ComplianceReportDto> getComplianceReport(
            Authentication auth) {
        return documentUseCase.getComplianceReport(getUserId(auth));
    }
}
