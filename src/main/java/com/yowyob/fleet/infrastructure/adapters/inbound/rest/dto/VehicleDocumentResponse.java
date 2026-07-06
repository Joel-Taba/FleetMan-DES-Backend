package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import com.yowyob.fleet.domain.model.VehicleDocument;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Réponse document véhicule")
public record VehicleDocumentResponse(
        UUID id,
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
        String status,
        long daysUntilExpiry,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static VehicleDocumentResponse from(VehicleDocument d) {
        return new VehicleDocumentResponse(
                d.getId(), d.getVehicleId(),
                d.getDocType().name(), d.getDocNumber(),
                d.getIssuer(), d.getIssueDate(), d.getExpiryDate(),
                d.getFileUrl(), d.getFileOriginalName(), d.getFileMimeType(),
                d.getFileSizeBytes(),
                d.getStatus().name(),
                d.daysUntilExpiry(),
                d.getNotes(), d.getCreatedAt(), d.getUpdatedAt()
        );
    }
}
