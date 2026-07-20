package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import com.yowyob.fleet.domain.ports.out.ExternalKycPort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.SubscriptionDocumentEntity;

import java.util.Map;
import java.util.UUID;

public record KycDocumentVerificationResponse(
        UUID documentId,
        String docType,
        String fileOriginalName,
        String documentType,
        String documentNumber,
        String issuingCountry,
        String holderName,
        String dateOfBirth,
        String issueDate,
        String expirationDate,
        boolean isValid,
        String validationMessage,
        Double confidenceScore,
        Boolean hasUncertainty,
        Map<String, String> additionalFields,
        String rawExtractedText,
        String suggestedDecision,
        String suggestedDecisionReason,
        String storedDocNumber,
        Boolean docNumberMatches
) {
    public static KycDocumentVerificationResponse from(
            SubscriptionDocumentEntity document,
            ExternalKycPort.DocumentAnalysis analysis) {
        boolean numberMatches = matchesStoredNumber(document.getDocNumber(), analysis.documentNumber());
        Decision decision = computeDecision(
                document.getDocType(),
                document.getDocNumber(),
                analysis,
                numberMatches);

        return new KycDocumentVerificationResponse(
                document.getId(),
                document.getDocType(),
                document.getFileOriginalName(),
                analysis.documentType(),
                analysis.documentNumber(),
                analysis.issuingCountry(),
                analysis.holderName(),
                analysis.dateOfBirth(),
                analysis.issueDate(),
                analysis.expirationDate(),
                analysis.isValid(),
                analysis.validationMessage(),
                analysis.confidenceScore(),
                analysis.hasUncertainty(),
                analysis.additionalFields(),
                analysis.rawExtractedText(),
                decision.value(),
                decision.reason(),
                document.getDocNumber(),
                numberMatches
        );
    }

    private static boolean matchesStoredNumber(String stored, String extracted) {
        if (stored == null || stored.isBlank() || extracted == null || extracted.isBlank()) {
            return false;
        }
        return normalize(stored).equals(normalize(extracted));
    }

    private static String normalize(String value) {
        return value.replaceAll("\\s+", "").toUpperCase();
    }

    private static Decision computeDecision(
            String expectedDocType,
            String storedDocNumber,
            ExternalKycPort.DocumentAnalysis analysis,
            boolean numberMatches) {
        double confidence = analysis.confidenceScore() != null ? analysis.confidenceScore() : 0.0;
        boolean uncertain = Boolean.TRUE.equals(analysis.hasUncertainty());

        if (analysis.isValid() && confidence >= 0.6 && !uncertain) {
            if ("ID_CARD".equals(expectedDocType) && !"ID_CARD".equalsIgnoreCase(analysis.documentType())) {
                return new Decision("REJECT",
                        "Le document déclaré comme CNI n'a pas été reconnu comme pièce d'identité.");
            }
            if (storedDocNumber != null && !storedDocNumber.isBlank() && !numberMatches) {
                return new Decision("REVIEW",
                        "Document valide selon KYC, mais le numéro extrait ne correspond pas à celui déclaré.");
            }
            return new Decision("ACCEPT", "Document valide et conforme selon l'analyse KYC.");
        }

        if (!analysis.isValid() && analysis.validationMessage() != null
                && analysis.validationMessage().toLowerCase().contains("expir")) {
            return new Decision("REJECT", analysis.validationMessage());
        }

        if ("UNKNOWN".equalsIgnoreCase(analysis.documentType()) || uncertain || confidence < 0.5) {
            return new Decision("REJECT",
                    analysis.validationMessage() != null ? analysis.validationMessage()
                            : "Document non reconnu ou confiance insuffisante.");
        }

        return new Decision("REVIEW",
                analysis.validationMessage() != null ? analysis.validationMessage()
                        : "Vérification manuelle recommandée.");
    }

    private record Decision(String value, String reason) {}
}
