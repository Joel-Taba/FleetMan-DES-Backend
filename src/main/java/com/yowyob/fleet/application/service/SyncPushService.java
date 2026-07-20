package com.yowyob.fleet.application.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.sync.MutationItem;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.SyncMutationEntity;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.sync.MutationResult;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.sync.PushMutationsRequest;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.sync.PushMutationsResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class SyncPushService {

    private final WebClient syncInternalWebClient;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public SyncPushService(
            @Qualifier("syncInternalWebClient") WebClient syncInternalWebClient,
            IdempotencyService idempotencyService,
            ObjectMapper objectMapper
    ) {
        this.syncInternalWebClient = syncInternalWebClient;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    public Mono<PushMutationsResponse> push(
            Authentication auth,
            PushMutationsRequest request,
            String authorizationHeader
    ) {
        UUID userId = getUserId(auth);
        return Flux.fromIterable(request.mutations())
                .concatMap(item -> processMutation(userId, item, authorizationHeader))
                .collectList()
                .map(PushMutationsResponse::new);
    }

    private Mono<MutationResult> processMutation(
            UUID userId,
            MutationItem item,
            String authorizationHeader
    ) {
        return idempotencyService.find(item.clientMutationId(), userId)
                .flatMap(existing -> {
                    if (!idempotencyService.samePayload(existing, item.body())) {
                        return Mono.just(new MutationResult(
                                item.clientMutationId(),
                                "ERROR",
                                422,
                                existing.getEntityId(),
                                null,
                                "IDEMPOTENCY_PAYLOAD_MISMATCH",
                                "Payload différent pour la même clé d'idempotence"
                        ));
                    }
                    Map<String, Object> storedBody = readStoredBody(existing);
                    return Mono.just(new MutationResult(
                            item.clientMutationId(),
                            "DUPLICATE",
                            existing.getResponseStatus(),
                            existing.getEntityId(),
                            storedBody,
                            null,
                            null
                    ));
                })
                .switchIfEmpty(executeAndStore(userId, item, authorizationHeader));
    }

    private Mono<MutationResult> executeAndStore(
            UUID userId,
            MutationItem item,
            String authorizationHeader
    ) {
        HttpMethod method = HttpMethod.valueOf(item.method().toUpperCase());
        WebClient.RequestBodySpec spec = syncInternalWebClient
                .method(method)
                .uri(item.path())
                .header("Authorization", authorizationHeader)
                .header("Idempotency-Key", item.clientMutationId().toString());

        if (item.clientEntityId() != null) {
            spec = spec.header("X-Client-Entity-Id", item.clientEntityId().toString());
        }

        WebClient.ResponseSpec responseSpec;
        if (item.body() != null && requiresBody(method)) {
            responseSpec = spec.bodyValue(item.body()).retrieve();
        } else {
            responseSpec = spec.retrieve();
        }

        return responseSpec
                .toEntity(String.class)
                .flatMap(entity -> {
                    int status = entity.getStatusCode().value();
                    String rawBody = entity.getBody();
                    Map<String, Object> bodyMap = parseBody(rawBody);
                    UUID entityId = extractEntityId(bodyMap);
                    return idempotencyService.save(
                            item.clientMutationId(),
                            userId,
                            item.method(),
                            item.path(),
                            item.body(),
                            status,
                            bodyMap.isEmpty() ? rawBody : bodyMap,
                            entityId
                    ).thenReturn(new MutationResult(
                            item.clientMutationId(),
                            "OK",
                            status,
                            entityId,
                            bodyMap.isEmpty() ? null : bodyMap,
                            null,
                            null
                    ));
                })
                .onErrorResume(WebClientResponseException.class, ex -> handleHttpError(userId, item, ex));
    }

    private Mono<MutationResult> handleHttpError(
            UUID userId,
            MutationItem item,
            WebClientResponseException ex
    ) {
        int status = ex.getStatusCode().value();
        Map<String, Object> errorBody = parseBody(ex.getResponseBodyAsString());
        String errorCode = errorBody.getOrDefault("errorCode", errorBody.getOrDefault("code", "")).toString();
        String errorMessage = errorBody.getOrDefault("detail", errorBody.getOrDefault("message", ex.getMessage())).toString();
        String resultStatus = status == 409 ? "CONFLICT" : "ERROR";

        if (status == 409) {
            return idempotencyService.save(
                    item.clientMutationId(),
                    userId,
                    item.method(),
                    item.path(),
                    item.body(),
                    status,
                    errorBody,
                    null
            ).thenReturn(new MutationResult(
                    item.clientMutationId(),
                    resultStatus,
                    status,
                    null,
                    errorBody.isEmpty() ? null : errorBody,
                    errorCode,
                    errorMessage
            ));
        }

        return Mono.just(new MutationResult(
                item.clientMutationId(),
                resultStatus,
                status,
                null,
                errorBody.isEmpty() ? null : errorBody,
                errorCode.isBlank() ? null : errorCode,
                errorMessage
        ));
    }

    private boolean requiresBody(HttpMethod method) {
        return method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH;
    }

    private Map<String, Object> parseBody(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of("raw", raw);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readStoredBody(SyncMutationEntity entity) {
        if (entity.getResponseBody() == null || entity.getResponseBody().isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(entity.getResponseBody(), new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of("raw", entity.getResponseBody());
        }
    }

    private UUID extractEntityId(Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        Object raw = body.get("id");
        if (raw == null) {
            raw = body.get("userId");
        }
        if (raw instanceof String str && !str.isBlank()) {
            try {
                return UUID.fromString(str);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    private UUID getUserId(Authentication auth) {
        return ((AuthPort.UserDetail) auth.getPrincipal()).id();
    }
}
