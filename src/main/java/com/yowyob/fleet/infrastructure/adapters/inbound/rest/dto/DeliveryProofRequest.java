package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import com.yowyob.fleet.domain.model.DeliveryProof;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Requête de dépôt d'une preuve de livraison par le conducteur")
public record DeliveryProofRequest(

        @NotNull(message = "L'identifiant de la mission est obligatoire.")
        @Schema(description = "Identifiant de la mission concernée", example = "123e4567-e89b-12d3-a456-426614174000")
        UUID missionId,

        @Schema(description = "Type de preuve (défaut : PHOTO)", example = "PHOTO",
                allowableValues = {"PHOTO", "SIGNATURE", "QR_SCAN", "MANUAL_CONFIRMATION"})
        DeliveryProof.ProofType type,

        @Schema(description = "Identifiant du fichier sur le service Yowyob File", example = "FILE-PROOF-78901")
        String fileId,

        @Schema(description = "URL publique d'accès au fichier", example = "https://files.yowyob.com/proofs/78901.jpg")
        String fileUrl,

        @Schema(description = "Hash cryptographique HMAC-SHA256 pour garantie d'intégrité", example = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
        String integrityHash,

        @Schema(description = "Nom du destinataire ayant réceptionné le colis", example = "Aissatou Bello")
        String recipientName,

        @Schema(description = "Signature numérique base64", example = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA...")
        String recipientSignature,

        @Schema(description = "Latitude GPS lors de la remise", example = "3.8480")
        Double latitude,

        @Schema(description = "Longitude GPS lors de la remise", example = "11.5021")
        Double longitude,

        @Schema(description = "Commentaire ou remarques du livreur", example = "Colis remis en main propre, bon état.")
        String notes
) {}

