package com.yowyob.fleet.infrastructure.config.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.fleet.application.service.IdempotencyService;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.SyncMutationEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Intercepte les mutations REST directes portant un header {@code Idempotency-Key}.
 * Les replays via {@code /api/v1/sync/mutations} réutilisent la même table.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyWebFilter implements WebFilter {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private static final Set<HttpMethod> MUTATING = Set.of(
            HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE
    );

    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (!shouldApply(request)) {
            return chain.filter(exchange);
        }

        String keyHeader = request.getHeaders().getFirst(IDEMPOTENCY_HEADER);
        if (!StringUtils.hasText(keyHeader)) {
            return chain.filter(exchange);
        }

        final UUID idempotencyKey;
        try {
            idempotencyKey = UUID.fromString(keyHeader.trim());
        } catch (IllegalArgumentException e) {
            return writeProblem(exchange, HttpStatus.BAD_REQUEST, "Clé Idempotency-Key invalide (UUID attendu)");
        }

        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .filter(Authentication::isAuthenticated)
                .flatMap(auth -> processAuthenticated(exchange, chain, request, auth, idempotencyKey))
                .switchIfEmpty(chain.filter(exchange));
    }

    private Mono<Void> processAuthenticated(
            ServerWebExchange exchange,
            WebFilterChain chain,
            ServerHttpRequest request,
            Authentication auth,
            UUID idempotencyKey
    ) {
        UUID userId = ((AuthPort.UserDetail) auth.getPrincipal()).id();
        String endpoint = request.getURI().getPath();
        String method = request.getMethod().name();

        return DataBufferUtils.join(request.getBody())
                .defaultIfEmpty(exchange.getResponse().bufferFactory().wrap(new byte[0]))
                .flatMap(dataBuffer -> {
                    byte[] bodyBytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bodyBytes);
                    DataBufferUtils.release(dataBuffer);

                    Object bodyObj = parseRequestBody(bodyBytes, request.getHeaders().getContentType());

                    return idempotencyService.find(idempotencyKey, userId)
                            .flatMap(existing -> handleDuplicate(exchange, existing, bodyObj))
                            .switchIfEmpty(Mono.defer(() ->
                                    executeAndRecord(exchange, chain, request, userId, idempotencyKey, method, endpoint, bodyBytes, bodyObj)
                            ));
                });
    }

    private Mono<Void> handleDuplicate(
            ServerWebExchange exchange,
            SyncMutationEntity existing,
            Object bodyObj
    ) {
        if (!idempotencyService.samePayload(existing, bodyObj)) {
            return writeProblem(
                    exchange,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Payload différent pour la même clé d'idempotence"
            );
        }
        return writeStoredResponse(exchange, existing);
    }

    private Mono<Void> executeAndRecord(
            ServerWebExchange exchange,
            WebFilterChain chain,
            ServerHttpRequest request,
            UUID userId,
            UUID idempotencyKey,
            String method,
            String endpoint,
            byte[] bodyBytes,
            Object bodyObj
    ) {
        ServerHttpRequest decoratedRequest = new ServerHttpRequestDecorator(request) {
            @Override
            public Flux<DataBuffer> getBody() {
                if (bodyBytes.length == 0) {
                    return Flux.empty();
                }
                return Flux.just(exchange.getResponse().bufferFactory().wrap(bodyBytes));
            }
        };

        ServerHttpResponse originalResponse = exchange.getResponse();
        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            public Mono<Void> writeWith(org.reactivestreams.Publisher<? extends DataBuffer> body) {
                return DataBufferUtils.join(Flux.from(body))
                        .flatMap(buffer -> {
                            byte[] responseBytes = new byte[buffer.readableByteCount()];
                            buffer.read(responseBytes);
                            DataBufferUtils.release(buffer);

                            int status = getStatusCode() != null ? getStatusCode().value() : 200;
                            String responseText = new String(responseBytes, StandardCharsets.UTF_8);
                            Object responseObj = parseResponseBody(responseText);

                            Mono<Void> writeMono = super.writeWith(
                                    Mono.just(originalResponse.bufferFactory().wrap(responseBytes))
                            );

                            if (status >= 200 && status < 300) {
                                UUID entityId = extractEntityId(responseObj);
                                return idempotencyService.save(
                                        idempotencyKey,
                                        userId,
                                        method,
                                        endpoint,
                                        bodyObj,
                                        status,
                                        responseObj,
                                        entityId
                                )
                                        .onErrorResume(e -> {
                                            log.warn("⚠️ Idempotency non enregistrée (réponse conservée) : {}", e.getMessage());
                                            return Mono.empty();
                                        })
                                        .then(writeMono);
                            }

                            return writeMono;
                        });
            }
        };

        return chain.filter(exchange.mutate()
                .request(decoratedRequest)
                .response(decoratedResponse)
                .build());
    }

    private Mono<Void> writeStoredResponse(ServerWebExchange exchange, SyncMutationEntity existing) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.valueOf(existing.getResponseStatus()));
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes = existing.getResponseBody() != null
                ? existing.getResponseBody().getBytes(StandardCharsets.UTF_8)
                : new byte[0];
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    private Mono<Void> writeProblem(ServerWebExchange exchange, HttpStatus status, String detail) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String json = "{\"title\":\"Idempotency Error\",\"detail\":\"" + detail.replace("\"", "\\\"") + "\"}";
        DataBuffer buffer = response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    private boolean shouldApply(ServerHttpRequest request) {
        HttpMethod method = request.getMethod();
        if (method == null || !MUTATING.contains(method)) {
            return false;
        }
        // SyncPushService rejoue les mutations en file via un appel HTTP loopback
        // (syncInternalWebClient) qui traverse à nouveau toute la chaîne de filtres,
        // donc ce filtre aussi. Sans cette exclusion, la MÊME clé d'idempotence est
        // enregistrée deux fois en parallèle sur fleet.sync_mutations (une fois ici,
        // une fois par SyncPushService lui-même) : la seconde écriture échoue en base
        // ("Row with Id [...] does not exist") et fait échouer la mutation rejouée —
        // symptôme observé côté client : édition perdue / requête rejetée au reconnect.
        if ("true".equalsIgnoreCase(request.getHeaders().getFirst("X-Internal-Sync-Call"))) {
            return false;
        }
        String path = request.getURI().getPath();
        return path.startsWith("/api/v1/")
                && !path.startsWith("/api/v1/sync/")
                && !path.startsWith("/api/v1/auth/")
                && !path.startsWith("/api/v1/public/")
                && !path.startsWith("/api/v1/files/")
                && !path.endsWith("/verify");
    }

    private Object parseRequestBody(byte[] bytes, MediaType contentType) {
        if (bytes.length == 0) {
            return null;
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (contentType != null && MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
            return parseJsonMap(text);
        }
        return text;
    }

    private Object parseResponseBody(String text) {
        if (!StringUtils.hasText(text)) {
            return Map.of();
        }
        return parseJsonMap(text);
    }

    private Object parseJsonMap(String text) {
        try {
            return objectMapper.readValue(text, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of("raw", text);
        }
    }

    private UUID extractEntityId(Object responseObj) {
        if (!(responseObj instanceof Map<?, ?> map) || map.isEmpty()) {
            return null;
        }
        Object raw = map.get("id");
        if (raw == null) {
            raw = map.get("userId");
        }
        if (raw == null) {
            raw = map.get("data");
            if (raw instanceof Map<?, ?> dataMap) {
                Object nested = dataMap.get("id");
                if (nested == null) {
                    nested = dataMap.get("userId");
                }
                raw = nested;
            }
        }
        if (raw instanceof String str && StringUtils.hasText(str)) {
            try {
                return UUID.fromString(str);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }
}
