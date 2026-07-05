package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Requête d'ajout ou mise à jour d'un document véhicule")
public record VehicleDocumentRequest(

        @NotNull(message = "Le véhicule est obligatoire")
        @Schema(description = "ID du véhicule")
        UUID vehicleId,

        @NotBlank(message = "Le type de document est obligatoire")
        @Schema(description = "Type de document",
                allowableValues = {"INSURANCE","REGISTRATION","TECHNICAL_CONTROL",
                                   "TAX_STICKER","TRANSPORT_PERMIT","OTHER"},
                example = "INSURANCE")
        String docType,

        @Schema(description = "Numéro du document", example = "ASS-2026-001234")
        String docNumber,

        @Schema(description = "Organisme émetteur", example = "AXA Assurances Cameroun")
        String issuer,

        @Schema(description = "Date d'émission", example = "2026-01-01")
        LocalDate issueDate,

        @NotNull(message = "La date d'expiration est obligatoire")
        @Schema(description = "Date d'expiration", example = "2027-01-01")
        LocalDate expiryDate,

        @Schema(description = "URL du fichier numérisé")
        String fileUrl,

        @Schema(description = "Notes")
        String notes
) {}
