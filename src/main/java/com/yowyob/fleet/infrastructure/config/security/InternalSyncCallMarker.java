package com.yowyob.fleet.infrastructure.config.security;

import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Valeur de l'en-tête {@code X-Internal-Sync-Call} posée par le WebClient de
 * replay de SyncPushService. Tirée au hasard à chaque démarrage : un client
 * externe ne peut pas la deviner pour se faire passer pour un replay interne
 * (et contourner IdempotencyWebFilter).
 */
@Component
public class InternalSyncCallMarker {

    public static final String HEADER = "X-Internal-Sync-Call";

    private final String value = UUID.randomUUID().toString();

    public String value() {
        return value;
    }

    public boolean matches(String headerValue) {
        return headerValue != null && MessageDigest.isEqual(
                value.getBytes(StandardCharsets.UTF_8),
                headerValue.getBytes(StandardCharsets.UTF_8));
    }
}
