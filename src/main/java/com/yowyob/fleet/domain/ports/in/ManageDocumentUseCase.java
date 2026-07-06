package com.yowyob.fleet.domain.ports.in;

import com.yowyob.fleet.domain.model.DriverDocument;
import com.yowyob.fleet.domain.model.VehicleDocument;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Port entrant — Cas d'utilisation pour la gestion des documents légaux.
 * Couvre les documents véhicules et conducteurs.
 */
public interface ManageDocumentUseCase {

    // ── Documents Véhicule ────────────────────────────────────────────────────

    Mono<VehicleDocument> addVehicleDocument(AddVehicleDocumentCommand cmd);
    Mono<VehicleDocument> getVehicleDocById(UUID id);
    Flux<VehicleDocument> getVehicleDocuments(UUID vehicleId);
    Mono<VehicleDocument> updateVehicleDocument(UpdateVehicleDocumentCommand cmd);
    Mono<Void> deleteVehicleDocument(UUID id);

    // ── Documents Conducteur ──────────────────────────────────────────────────

    Mono<DriverDocument> addDriverDocument(AddDriverDocumentCommand cmd);
    Mono<DriverDocument> getDriverDocById(UUID id);
    Flux<DriverDocument> getDriverDocuments(UUID driverId);
    Mono<DriverDocument> updateDriverDocument(UpdateDriverDocumentCommand cmd);
    Mono<Void> deleteDriverDocument(UUID id);

    // ── Vues transversales ────────────────────────────────────────────────────

    /** Retourne tous les documents (véhicules + conducteurs) expirant dans N jours. */
    Flux<ExpiringDocumentDto> getExpiringDocuments(UUID managerId, int withinDays);

    /** Retourne tous les documents expirés pour un manager. */
    Flux<ExpiringDocumentDto> getExpiredDocuments(UUID managerId);

    /** Calcule le taux de conformité documentaire d'une flotte. */
    Mono<ComplianceReportDto> getComplianceReport(UUID managerId);

    // ── Records Command ───────────────────────────────────────────────────────

    record AddVehicleDocumentCommand(
            UUID vehicleId,
            String docType,
            String docNumber,
            String issuer,
            LocalDate issueDate,
            LocalDate expiryDate,
            String fileUrl,
            String fileOriginalName,
            String fileMimeType,
            Long fileSizeBytes,
            String notes
    ) {}

    record UpdateVehicleDocumentCommand(
            UUID documentId,
            String docNumber,
            String issuer,
            LocalDate issueDate,
            LocalDate expiryDate,
            String fileUrl,
            String fileOriginalName,
            String fileMimeType,
            Long fileSizeBytes,
            String status,
            String notes
    ) {}

    record AddDriverDocumentCommand(
            UUID driverId,
            String docType,
            String docNumber,
            String licenseCategories,
            String issuer,
            LocalDate issueDate,
            LocalDate expiryDate,
            String fileUrl,
            String fileOriginalName,
            String fileMimeType,
            Long fileSizeBytes,
            String notes
    ) {}

    record UpdateDriverDocumentCommand(
            UUID documentId,
            String docNumber,
            String licenseCategories,
            String issuer,
            LocalDate issueDate,
            LocalDate expiryDate,
            String fileUrl,
            String fileOriginalName,
            String fileMimeType,
            Long fileSizeBytes,
            String status,
            String notes
    ) {}

    // ── Records de réponse transversaux ───────────────────────────────────────

    record ExpiringDocumentDto(
            UUID documentId,
            String entityType,   // "VEHICLE" ou "DRIVER"
            UUID entityId,
            String entityName,   // Immatriculation ou nom du conducteur
            String docType,
            String docNumber,
            LocalDate expiryDate,
            long daysUntilExpiry,
            String status
    ) {}

    record ComplianceReportDto(
            UUID managerId,
            int totalDocuments,
            int validDocuments,
            int expiringSoonDocuments,
            int expiredDocuments,
            double complianceRate  // Pourcentage de documents valides
    ) {}
}
