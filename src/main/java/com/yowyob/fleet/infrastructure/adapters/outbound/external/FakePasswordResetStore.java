package com.yowyob.fleet.infrastructure.adapters.outbound.external;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stockage en mémoire des tokens de réinitialisation (mode fake-auth uniquement).
 * TTL : 1 heure.
 */
@Component
public class FakePasswordResetStore {

    private static final long TTL_SECONDS = 3600;

    public record Entry(String email, Instant expiresAt) {}

    private final Map<String, Entry> tokens = new ConcurrentHashMap<>();

    public String issueToken(String email) {
        purgeExpired();
        String token = UUID.randomUUID().toString().replace("-", "");
        tokens.put(token, new Entry(email.trim().toLowerCase(), Instant.now().plusSeconds(TTL_SECONDS)));
        return token;
    }

    public Optional<String> consumeToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        purgeExpired();
        Entry entry = tokens.remove(token.trim());
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(entry.email());
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        tokens.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
    }
}
