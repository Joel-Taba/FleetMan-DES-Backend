package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import com.yowyob.fleet.domain.model.DriverDocument;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Réponse document conducteur")
public record DriverDocumentResponse(
        UUID id,
        UUID driverId,
        String docType,
        String docNumber,
        String licenseCategories,
        String issuer,
        LocalDate issueDate,
        LocalDate expiryDate,
        String fileUrl,
        String status,
        long daysUntilExpiry,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static DriverDocumentResponse from(DriverDocument d) {
        return new DriverDocumentResponse(
                d.getId(), d.getDriverId(),
                d.getDocType().name(), d.getDocNumber(),
                d.getLicenseCategories(), d.getIssuer(),
                d.getIssueDate(), d.getExpiryDate(),
                d.getFileUrl(), d.getStatus().name(),
                d.daysUntilExpiry(),
                d.getNotes(), d.getCreatedAt(), d.getUpdatedAt()
        );
    }
}
