package com.yowyob.fleet.infrastructure.adapters.outbound.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.fleet.domain.ports.out.ExternalKycPort;
import com.yowyob.fleet.infrastructure.adapters.outbound.kernel.exception.KernelException;
import com.yowyob.fleet.infrastructure.config.KernelCallSupport;
import com.yowyob.fleet.infrastructure.config.KernelTokenHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Anti-corruption layer vers kyc-verification-controller Kernel. */
@Slf4j
public class KernelKycAdapter implements ExternalKycPort {

    private final WebClient kernelWebClient;
    private final KernelTokenHolder kernelTokenHolder;
    private final KernelCallSupport kernelCallSupport;
    private final ObjectMapper objectMapper;
    private final String tenantId;

    public KernelKycAdapter(
            WebClient kernelWebClient,
            KernelTokenHolder kernelTokenHolder,
            KernelCallSupport kernelCallSupport,
            ObjectMapper objectMapper,
            String tenantId) {
        this.kernelWebClient = kernelWebClient;
        this.kernelTokenHolder = kernelTokenHolder;
        this.kernelCallSupport = kernelCallSupport;
        this.objectMapper = objectMapper;
        this.tenantId = tenantId;
    }

    @Override
    public Mono<DocumentAnalysis> verify(byte[] content, String filename, String mimeType, String bearerToken) {
        String safeMime = mimeType == null || mimeType.isBlank()
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                : mimeType;
        String safeName = filename == null || filename.isBlank() ? "document.bin" : filename;

        return kernelCallSupport.execute(
                "kernel-kyc",
                resolveToken(bearerToken)
                        .flatMap(token -> {
                            MultipartBodyBuilder builder = new MultipartBodyBuilder();
                            builder.part("file", content)
                                    .filename(safeName)
                                    .contentType(MediaType.parseMediaType(safeMime));

                            return kernelWebClient.post()
                                    .uri("/api/kyc/verify")
                                    .header("Authorization", bearerHeader(token))
                                    .header("X-Tenant-Id", tenantId)
                                    .contentType(MediaType.MULTIPART_FORM_DATA)
                                    .bodyValue(builder.build())
                                    .retrieve()
                                    .bodyToMono(String.class)
                                    .flatMap(this::parseResponse);
                        })
                        .doOnSuccess(r -> log.info("✅ [KERNEL KYC] Analyse OK : type={}, valid={}", r.documentType(), r.isValid()))
                        .onErrorMap(this::wrapError),
                Mono.error(KernelException.of(
                        "KYC_UNAVAILABLE",
                        "Le service de vérification KYC Kernel est indisponible. Réessayez dans quelques instants.")));
    }

    private Mono<DocumentAnalysis> parseResponse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.has("data") ? root.get("data") : root;
            return Mono.just(mapNode(data));
        } catch (Exception e) {
            return Mono.error(new IllegalStateException("Réponse KYC illisible : " + e.getMessage(), e));
        }
    }

    private DocumentAnalysis mapNode(JsonNode node) {
        return new DocumentAnalysis(
                textOrNull(node, "documentType"),
                textOrNull(node, "documentNumber"),
                textOrNull(node, "issuingCountry"),
                textOrNull(node, "holderName"),
                textOrNull(node, "dateOfBirth"),
                textOrNull(node, "issueDate"),
                textOrNull(node, "expirationDate"),
                node.has("isValid") && node.get("isValid").asBoolean(false),
                textOrNull(node, "validationMessage"),
                node.has("confidenceScore") && !node.get("confidenceScore").isNull()
                        ? node.get("confidenceScore").asDouble() : null,
                node.has("hasUncertainty") && !node.get("hasUncertainty").isNull()
                        ? node.get("hasUncertainty").asBoolean() : null,
                mapAdditionalFields(node.get("additionalFields")),
                textOrNull(node, "rawExtractedText")
        );
    }

    private static Map<String, String> mapAdditionalFields(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        Map<String, String> map = new HashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            map.put(entry.getKey(), entry.getValue().asText());
        }
        return map;
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text.isBlank() ? null : text;
    }

    private Mono<String> resolveToken(String bearerToken) {
        if (bearerToken != null && !bearerToken.isBlank()) {
            return Mono.just(bearerToken);
        }
        return kernelTokenHolder.getValidAccessToken();
    }

    private static String bearerHeader(String token) {
        return token.startsWith("Bearer ") ? token : "Bearer " + token;
    }

    private Throwable wrapError(Throwable e) {
        if (e instanceof WebClientResponseException wce) {
            log.error("❌ [KERNEL KYC] {} : {}", wce.getStatusCode(), wce.getResponseBodyAsString());
        }
        return e;
    }
}
