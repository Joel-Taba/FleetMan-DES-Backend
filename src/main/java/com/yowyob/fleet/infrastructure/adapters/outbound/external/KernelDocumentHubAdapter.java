package com.yowyob.fleet.infrastructure.adapters.outbound.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.yowyob.fleet.domain.ports.out.DocumentHubPort;
import com.yowyob.fleet.infrastructure.config.KernelTokenHolder;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * file-core du Kernel : POST /api/document-hub/links, POST /api/document-governance/documents/{id}/reviews,
 * PUT /api/document-governance/organizations/{org}/policies/{targetType}/{category}.
 * Écrit d'après le contrat OpenAPI du Kernel, pas encore essayé contre le service réel.
 */
public class KernelDocumentHubAdapter implements DocumentHubPort {

    private static final Logger log = LoggerFactory.getLogger(KernelDocumentHubAdapter.class);

    private final WebClient kernelWebClient;
    private final KernelTokenHolder tokenHolder;
    private final String tenantId;
    private final UUID organizationId;

    public KernelDocumentHubAdapter(WebClient kernelWebClient, KernelTokenHolder tokenHolder,
                                    String tenantId, UUID organizationId) {
        this.kernelWebClient = kernelWebClient;
        this.tokenHolder = tokenHolder;
        this.tenantId = tenantId;
        this.organizationId = organizationId;
    }

    @Override
    public Mono<UUID> attach(String targetType, UUID targetId, UUID kernelFileId, String category, String label) {
        if (kernelFileId == null) {
            return Mono.empty();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("targetType", targetType);
        body.put("targetId", targetId);
        body.put("fileId", kernelFileId);
        body.put("documentCategory", category);
        body.put("label", label);
        return tokenHolder.getValidAccessToken()
                .flatMap(token -> kernelWebClient.post()
                        .uri("/api/document-hub/links")
                        .headers(h -> headers(h, token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(JsonNode.class))
                .map(json -> UUID.fromString(json.path("data").path("id").asText()))
                .onErrorResume(e -> {
                    log.warn("Hub documentaire Kernel : rattachement ignoré : {}", e.getMessage());
                    return Mono.empty();
                });
    }

    @Override
    public Mono<Void> review(UUID linkId, String reviewStatus, LocalDate expiresAt, String notes) {
        if (linkId == null) {
            return Mono.empty();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reviewStatus", reviewStatus);
        if (expiresAt != null) body.put("expiresAt", expiresAt.atStartOfDay().toString());
        body.put("notes", notes);
        return tokenHolder.getValidAccessToken()
                .flatMap(token -> kernelWebClient.post()
                        .uri("/api/document-governance/documents/{id}/reviews", linkId)
                        .headers(h -> headers(h, token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(body)
                        .retrieve()
                        .toBodilessEntity())
                .then()
                .onErrorResume(e -> {
                    log.warn("Hub documentaire Kernel : revue ignorée : {}", e.getMessage());
                    return Mono.empty();
                });
    }

    @Override
    public Mono<Void> upsertPolicy(String targetType, String category, boolean mandatory) {
        Map<String, Object> body = Map.of("mandatory", mandatory, "approvalRequired", true);
        return tokenHolder.getValidAccessToken()
                .flatMap(token -> kernelWebClient.put()
                        .uri("/api/document-governance/organizations/{org}/policies/{type}/{category}",
                                organizationId, targetType, category)
                        .headers(h -> headers(h, token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(body)
                        .retrieve()
                        .toBodilessEntity())
                .then()
                .onErrorResume(e -> {
                    log.warn("Hub documentaire Kernel : politique ignorée : {}", e.getMessage());
                    return Mono.empty();
                });
    }

    private void headers(org.springframework.http.HttpHeaders h, String token) {
        h.setBearerAuth(token.startsWith("Bearer ") ? token.substring(7) : token);
        h.set("X-Tenant-Id", tenantId);
        h.set("X-Organization-Id", organizationId.toString());
    }
}
