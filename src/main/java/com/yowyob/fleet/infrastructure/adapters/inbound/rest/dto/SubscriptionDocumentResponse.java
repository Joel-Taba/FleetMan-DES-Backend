package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.SubscriptionDocumentEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SubscriptionDocumentResponse(
        UUID id,
        UUID userId,
        String docType,
        String docNumber,
        String fileUrl,
        String fileMimeType,
        String fileOriginalName,
        LocalDate expiryDate,
        String issuer,
        LocalDate issueDate,
        String notes,
        Instant createdAt
) {
    public static SubscriptionDocumentResponse from(SubscriptionDocumentEntity e) {
        return new SubscriptionDocumentResponse(
                e.getId(),
                e.getUserId(),
                e.getDocType(),
                e.getDocNumber(),
                e.getFileUrl(),
                e.getFileMimeType(),
                e.getFileOriginalName(),
                e.getExpiryDate(),
                e.getIssuer(),
                e.getIssueDate(),
                e.getNotes(),
                e.getCreatedAt()
        );
    }
}
