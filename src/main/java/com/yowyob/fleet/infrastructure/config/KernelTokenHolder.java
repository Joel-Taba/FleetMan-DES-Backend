package com.yowyob.fleet.infrastructure.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.client.KernelAuthApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Maintient en mémoire un token Kernel valide pour le compte owner (bootstrap admin).
 * Utilise {@link KernelAuthApiClient} directement pour éviter la dépendance circulaire avec {@code AuthPort}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KernelTokenHolder {

    private final KernelAuthApiClient kernelClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${application.bootstrap.admin.email:joeltaba4@gmail.com}")
    private String username;

    @Value("${application.bootstrap.admin.password:FleetMan2026!}")
    private String password;

    @Value("${application.kernel.organization-id:}")
    private String defaultOrgId;

    private static final long REFRESH_MARGIN_SECONDS = 60L;

    private volatile HeldToken currentToken = null;

    public record HeldToken(String accessToken, Instant expiresAt) {
        public boolean isStillValid() {
            return Instant.now().isBefore(expiresAt.minusSeconds(REFRESH_MARGIN_SECONDS));
        }
    }

    public Mono<String> getValidAccessToken() {
        HeldToken held = currentToken;
        if (held != null && held.isStillValid()) {
            log.debug("🔑 [KERNEL TOKEN] Token en cache encore valide jusqu'à {}", held.expiresAt());
            return Mono.just(held.accessToken());
        }
        log.info("🔄 [KERNEL TOKEN] Token absent ou expiré — nouveau login en cours...");
        return doLogin();
    }

    private Mono<String> doLogin() {
        return kernelClient.discoverContexts(new KernelAuthApiClient.LoginRequest(username, password))
                .flatMap(discoverResp -> {
                    if (!discoverResp.success() || discoverResp.data() == null
                            || discoverResp.data().contexts() == null
                            || discoverResp.data().contexts().isEmpty()) {
                        return Mono.error(new IllegalStateException("discover-contexts échoué pour owner"));
                    }
                    var ctx = discoverResp.data().contexts().get(0);
                    UUID orgId = resolveDefaultOrgId();
                    return kernelClient.selectContext(new KernelAuthApiClient.SelectContextRequest(
                            discoverResp.data().selectionToken(),
                            ctx.contextId(),
                            orgId
                    ));
                })
                .map(selectResp -> {
                    if (!selectResp.success() || selectResp.data() == null
                            || selectResp.data().session() == null) {
                        throw new IllegalStateException("select-context échoué pour owner");
                    }
                    return selectResp.data().session().accessToken();
                })
                .flatMap(token -> {
                    Instant expiresAt = extractExpFromJwt(token);
                    currentToken = new HeldToken(token, expiresAt);
                    log.info("✅ [KERNEL TOKEN] Nouveau token owner, expire à {}", expiresAt);
                    return Mono.just(token);
                })
                .onErrorResume(e -> {
                    log.error("❌ [KERNEL TOKEN] Login owner impossible : {}", e.getMessage());
                    return Mono.error(e);
                });
    }

    private UUID resolveDefaultOrgId() {
        if (defaultOrgId == null || defaultOrgId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(defaultOrgId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Instant extractExpFromJwt(String token) {
        try {
            String rawToken = token.startsWith("Bearer ") ? token.substring(7) : token;
            String[] parts = rawToken.split("\\.");
            if (parts.length < 2) return Instant.now().plusSeconds(900);

            String payloadJson = new String(
                    Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            JsonNode payload = objectMapper.readTree(payloadJson);

            long exp = payload.has("exp") ? payload.get("exp").asLong(0) : 0;
            return exp > 0 ? Instant.ofEpochSecond(exp) : Instant.now().plusSeconds(900);
        } catch (Exception e) {
            log.warn("⚠️ [KERNEL TOKEN] JWT exp illisible, fallback 15min");
            return Instant.now().plusSeconds(900);
        }
    }

    public Instant getCurrentTokenExpiry() {
        HeldToken held = currentToken;
        return held != null ? held.expiresAt() : null;
    }

    public Mono<String> forceRefresh() {
        log.info("🔄 [KERNEL TOKEN] Renouvellement forcé demandé.");
        currentToken = null;
        return doLogin();
    }
}
