package com.yowyob.fleet.infrastructure.adapters.outbound.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.fleet.domain.exception.DocumentException;
import com.yowyob.fleet.domain.ports.out.ExternalFilePort;
import com.yowyob.fleet.infrastructure.config.KernelTokenHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Anti-corruption layer vers file-core Kernel (/api/files). */
@Slf4j
public class KernelFileAdapter implements ExternalFilePort {

    private static final Set<String> ALLOWED_MIME = Set.of(
            "application/pdf", "image/jpeg", "image/png", "image/webp");

    private final WebClient kernelWebClient;
    private final KernelTokenHolder kernelTokenHolder;
    private final ObjectMapper objectMapper;
    private final String tenantId;
    private final UUID organizationId;

    public KernelFileAdapter(
            WebClient kernelWebClient,
            KernelTokenHolder kernelTokenHolder,
            ObjectMapper objectMapper,
            String tenantId,
            UUID organizationId) {
        this.kernelWebClient = kernelWebClient;
        this.kernelTokenHolder = kernelTokenHolder;
        this.objectMapper = objectMapper;
        this.tenantId = tenantId;
        this.organizationId = organizationId;
    }

    @Override
    public boolean isKernelMode() {
        return true;
    }

    @Override
    public Mono<UploadResult> upload(FilePart file, String category, String bearerToken) {
        String originalName = file.filename();
        validateFileName(originalName);

        return resolveToken(bearerToken)
                .flatMap(token -> DataBufferUtils.join(file.content())
                        .flatMap(dataBuffer -> {
                            byte[] bytes = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(bytes);
                            DataBufferUtils.release(dataBuffer);

                            MultipartBodyBuilder builder = new MultipartBodyBuilder();
                            builder.part("file", bytes)
                                    .filename(originalName)
                                    .contentType(file.headers().getContentType());

                            return kernelWebClient.post()
                                    .uri(uriBuilder -> uriBuilder
                                            .path("/api/files")
                                            .queryParam("documentType", category)
                                            .build())
                                    .header("Authorization", bearerHeader(token))
                                    .header("X-Tenant-Id", tenantId)
                                    .header("X-Organization-Id", organizationId.toString())
                                    .contentType(MediaType.MULTIPART_FORM_DATA)
                                    .bodyValue(builder.build())
                                    .retrieve()
                                    .bodyToMono(String.class)
                                    .flatMap(this::parseUploadResponse);
                        }))
                .doOnSuccess(r -> log.info("✅ [KERNEL FILE] Upload OK : {} → {}", originalName, r.kernelFileId()))
                .onErrorMap(this::wrapError);
    }

    @Override
    public Mono<DownloadResult> download(UUID fileId, String bearerToken) {
        return resolveToken(bearerToken)
                .flatMap(token -> kernelWebClient.get()
                        .uri("/api/files/{fileId}", fileId)
                        .header("Authorization", bearerHeader(token))
                        .header("X-Tenant-Id", tenantId)
                        .header("X-Organization-Id", organizationId.toString())
                        .accept(MediaType.APPLICATION_OCTET_STREAM, MediaType.ALL)
                        .retrieve()
                        .bodyToMono(byte[].class)
                        .zipWith(getMetadata(fileId, token))
                        .map(tuple -> new DownloadResult(
                                tuple.getT1(),
                                tuple.getT2().mimeType(),
                                tuple.getT2().originalName())))
                .onErrorMap(this::wrapError);
    }

    @Override
    public Mono<UploadResult> getMetadata(UUID fileId, String bearerToken) {
        return resolveToken(bearerToken)
                .flatMap(token -> kernelWebClient.get()
                        .uri("/api/files/{fileId}/metadata", fileId)
                        .header("Authorization", bearerHeader(token))
                        .header("X-Tenant-Id", tenantId)
                        .header("X-Organization-Id", organizationId.toString())
                        .retrieve()
                        .bodyToMono(String.class)
                        .flatMap(this::parseUploadResponse))
                .onErrorMap(this::wrapError);
    }

    private Mono<UploadResult> parseUploadResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.path("success").asBoolean(false)) {
                return Mono.error(new IllegalStateException(
                        "Kernel file error: " + root.path("message").asText()));
            }
            JsonNode data = root.path("data");
            UUID id = UUID.fromString(data.path("id").asText());
            String fileName = data.path("fileName").asText(null);
            String contentType = data.path("contentType").asText("application/octet-stream");
            long size = data.path("size").asLong(0);
            return Mono.just(new UploadResult(
                    id,
                    "/api/v1/files/" + id,
                    fileName,
                    contentType,
                    size));
        } catch (Exception e) {
            return Mono.error(new IllegalStateException("Réponse Kernel file illisible", e));
        }
    }

    private Mono<String> resolveToken(String userToken) {
        if (userToken != null && !userToken.isBlank()) {
            return Mono.just(userToken);
        }
        return kernelTokenHolder.getValidAccessToken();
    }

    private String bearerHeader(String token) {
        return token.startsWith("Bearer ") ? token : "Bearer " + token;
    }

    private void validateFileName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            throw DocumentException.invalidFileType("nom de fichier manquant");
        }
        String ext = originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.')).toLowerCase(Locale.ROOT)
                : "";
        if (!Set.of(".pdf", ".jpg", ".jpeg", ".png", ".webp").contains(ext)) {
            throw DocumentException.invalidFileType(ext);
        }
    }

    private Throwable wrapError(Throwable ex) {
        if (ex instanceof WebClientResponseException wex) {
            log.error("❌ [KERNEL FILE] HTTP {} — {}", wex.getStatusCode(), wex.getResponseBodyAsString());
        }
        return ex;
    }
}
