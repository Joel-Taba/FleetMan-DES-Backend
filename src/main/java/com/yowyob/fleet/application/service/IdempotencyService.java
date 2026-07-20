package com.yowyob.fleet.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.SyncMutationEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.SyncMutationR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final SyncMutationR2dbcRepository repository;
    private final ObjectMapper objectMapper;

    public Mono<SyncMutationEntity> find(UUID clientMutationId, UUID userId) {
        return repository.findById(clientMutationId)
                .filter(entity -> entity.getUserId().equals(userId));
    }

    public Mono<SyncMutationEntity> save(
            UUID clientMutationId,
            UUID userId,
            String httpMethod,
            String endpoint,
            Object requestBody,
            int responseStatus,
            Object responseBody,
            UUID entityId
    ) {
        SyncMutationEntity entity = new SyncMutationEntity();
        entity.setClientMutationId(clientMutationId);
        entity.setUserId(userId);
        entity.setHttpMethod(httpMethod);
        entity.setEndpoint(endpoint);
        entity.setRequestHash(hashBody(requestBody));
        entity.setResponseStatus(responseStatus);
        entity.setResponseBody(writeJson(responseBody));
        entity.setEntityId(entityId);
        entity.setProcessedAt(Instant.now());
        return repository.save(entity);
    }

    public String hashBody(Object body) {
        if (body == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(writeJson(body).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public boolean samePayload(SyncMutationEntity existing, Object newBody) {
        String newHash = hashBody(newBody);
        return existing.getRequestHash() != null && existing.getRequestHash().equals(newHash);
    }

    @SuppressWarnings("unchecked")
    public <T> T readResponseBody(SyncMutationEntity entity, Class<T> type) {
        if (entity.getResponseBody() == null || entity.getResponseBody().isBlank()) {
            return null;
        }
        try {
            if (type.equals(String.class)) {
                return (T) entity.getResponseBody();
            }
            return objectMapper.readValue(entity.getResponseBody(), type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid stored sync response", e);
        }
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String str) {
            return str;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize sync payload", e);
        }
    }
}
