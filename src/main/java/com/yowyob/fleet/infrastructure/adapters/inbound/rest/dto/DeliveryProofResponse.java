package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import com.yowyob.fleet.domain.model.DeliveryProof;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Détail d'une preuve de livraison vérifiable")
public record DeliveryProofResponse(
        @Schema(description = "Identifiant de la preuve")
        UUID id,

        @Schema(description = "Identifiant de la mission liée")
        UUID missionId,

        @Schema(description = "Identifiant du conducteur ayant déposé la preuve")
        UUID driverId,

        @Schema(description = "Type de preuve", example = "PHOTO")
        DeliveryProof.ProofType type,

        @Schema(description = "Statut de révision", example = "PENDING_REVIEW")
        DeliveryProof.ProofStatus status,

        @Schema(description = "Identifiant du fichier hébergé")
        String fileId,

        @Schema(description = "URL publique de visualisation")
        String fileUrl,

        @Schema(description = "Hash d'intégrité HMAC-SHA256")
        String integrityHash,

        @Schema(description = "Nom du destinataire")
        String recipientName,

        @Schema(description = "Latitude GPS de dépôt")
        Double latitude,

        @Schema(description = "Longitude GPS de dépôt")
        Double longitude,

        @Schema(description = "Notes libres")
        String notes,

        @Schema(description = "Date/heure de dépôt")
        LocalDateTime submittedAt,

        @Schema(description = "Date/heure de validation")
        LocalDateTime reviewedAt,

        @Schema(description = "Identifiant du responsable ayant validé")
        UUID reviewedBy,

        @Schema(description = "Indique si la preuve a été formellement acceptée")
        boolean accepted
) {
    public static DeliveryProofResponse fromDomain(DeliveryProof p) {
        return new DeliveryProofResponse(
                p.getId(),
                p.getMissionId(),
                p.getDriverId(),
                p.getType(),
                p.getStatus(),
                p.getFileId(),
                p.getFileUrl(),
                p.getIntegrityHash(),
                p.getRecipientName(),
                p.getLatitude(),
                p.getLongitude(),
                p.getNotes(),
                p.getSubmittedAt(),
                p.getReviewedAt(),
                p.getReviewedBy(),
                p.isAccepted()
        );
    }
}

