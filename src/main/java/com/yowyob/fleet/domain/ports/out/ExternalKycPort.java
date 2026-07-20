package com.yowyob.fleet.domain.ports.out;

import reactor.core.publisher.Mono;

import java.util.Map;

/** Port sortant vers le service KYC Kernel (OCR + validation document). */
public interface ExternalKycPort {

    record DocumentAnalysis(
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
            String rawExtractedText
    ) {}

    Mono<DocumentAnalysis> verify(byte[] content, String filename, String mimeType, String bearerToken);
}
