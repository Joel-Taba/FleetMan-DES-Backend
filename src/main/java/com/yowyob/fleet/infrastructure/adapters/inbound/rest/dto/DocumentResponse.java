package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.DocumentEntity;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Pièce d'un dossier. {@code status} : PENDING, REJECTED, VALID, EXPIRING_SOON, EXPIRED, REPLACED, ARCHIVED. */
public record DocumentResponse(
        UUID id,
        String targetType,
        UUID targetId,
        UUID vehicleId,
        UUID driverId,
        String docType,
        String docNumber,
        String licenseCategories,
        String issuer,
        LocalDate issueDate,
        LocalDate expiryDate,
        Long daysUntilExpiry,
        String fileUrl,
        String fileOriginalName,
        String fileMimeType,
        Long fileSizeBytes,
        String status,
        String reviewStatus,
        String rejectionReason,
        LocalDateTime reviewedAt,
        int version,
        boolean current,
        String archiveReason,
        String notes,
        UUID submittedBy,
        LocalDateTime createdAt,
        String targetName) {

    public static DocumentResponse from(DocumentEntity d) {
        LocalDate today = LocalDate.now();
        return new DocumentResponse(d.getId(), d.getTargetType(), d.getTargetId(),
                "VEHICLE".equals(d.getTargetType()) ? d.getTargetId() : null,
                "DRIVER".equals(d.getTargetType()) ? d.getTargetId() : null,
                d.getDocType(), d.getDocNumber(), d.getLicenseCategories(), d.getIssuer(), d.getIssueDate(), d.getExpiryDate(),
                d.getExpiryDate() != null ? java.time.temporal.ChronoUnit.DAYS.between(today, d.getExpiryDate()) : null,
                d.getFileUrl(), d.getFileOriginalName(), d.getFileMimeType(), d.getFileSizeBytes(),
                d.effectiveStatus(today), d.getReviewStatus(), d.getRejectionReason(), d.getReviewedAt(),
                d.getVersion(), d.isCurrent(), d.getArchiveReason(), d.getNotes(), d.getSubmittedBy(), d.getCreatedAt(), null);
    }

    public DocumentResponse withTargetName(String name) {
        return new DocumentResponse(id, targetType, targetId, vehicleId, driverId, docType, docNumber, licenseCategories,
                issuer, issueDate, expiryDate, daysUntilExpiry, fileUrl, fileOriginalName, fileMimeType, fileSizeBytes,
                status, reviewStatus, rejectionReason, reviewedAt, version, current, archiveReason, notes, submittedBy,
                createdAt, name);
    }
}
