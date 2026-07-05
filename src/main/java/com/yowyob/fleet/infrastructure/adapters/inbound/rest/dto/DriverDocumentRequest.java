package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Requête d'ajout ou mise à jour d'un document conducteur")
public record DriverDocumentRequest(

        @NotNull(message = "Le conducteur est obligatoire")
        @Schema(description = "ID du conducteur")
        UUID driverId,

        @NotBlank(message = "Le type de document est obligatoire")
        @Schema(description = "Type de document",
                allowableValues = {"DRIVING_LICENSE","MEDICAL_CERT","PROFESSIONAL_CARD",
                                   "WORK_CONTRACT","ID_CARD","OTHER"},
                example = "DRIVING_LICENSE")
        String docType,

        @Schema(description = "Numéro du document", example = "PERM-CM-2024-789")
        String docNumber,

        @Schema(description = "Catégories du permis (si applicable)", example = "B,C,D")
        String licenseCategories,

        @Schema(description = "Organisme émetteur", example = "Préfecture de Yaoundé")
        String issuer,

        @Schema(description = "Date d'émission", example = "2020-03-15")
        LocalDate issueDate,

        @Schema(description = "Date d'expiration (null si pas d'expiration)", example = "2030-03-15")
        LocalDate expiryDate,

        @Schema(description = "URL du fichier numérisé")
        String fileUrl,

        @Schema(description = "Notes")
        String notes
) {}
