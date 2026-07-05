package com.yowyob.fleet.infrastructure.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * Maintient en mémoire un token Kernel valide pour le compte système.
 * Renouvelle automatiquement le token par re-login si celui-ci est
 * expiré ou sur le point d'expirer (marge de 60 secondes).
 *
 * Utilisation : injecter KernelTokenHolder et appeler getValidAccessToken()
 * à chaque fois qu'un appel Kernel au nom du système est nécessaire.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KernelTokenHolder {

    private final AuthPort authPort;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${application.geofence-system-user.username}")
    private String username;

    @Value("${application.geofence-system-user.password}")
    private String password;

    /** Marge de sécurité : on renouvelle si moins de 60s restantes */
    private static final long REFRESH_MARGIN_SECONDS = 60L;

    /** Token courant, stocké en mémoire (volatile = visible par tous les threads) */
    private volatile HeldToken currentToken = null;

    // -------------------------------------------------------------------------
    // Record interne — représente un token avec sa date d'expiration
    // -------------------------------------------------------------------------

    public record HeldToken(String accessToken, Instant expiresAt) {
        /**
         * Retourne true si le token est encore valide avec la marge de sécurité.
         */
        public boolean isStillValid() {
            return Instant.now().isBefore(expiresAt.minusSeconds(REFRESH_MARGIN_SECONDS));
        }
    }

    // -------------------------------------------------------------------------
    // Méthode principale — à appeler partout où le token système est nécessaire
    // -------------------------------------------------------------------------

    /**
     * Retourne un accessToken valide pour le compte système.
     * Si le token courant est expiré ou absent, effectue un nouveau login.
     */
    public Mono<String> getValidAccessToken() {
        HeldToken held = currentToken;
        if (held != null && held.isStillValid()) {
            log.debug("🔑 [KERNEL TOKEN] Token en cache encore valide jusqu'à {}", held.expiresAt());
            return Mono.just(held.accessToken());
        }
        log.info("🔄 [KERNEL TOKEN] Token absent ou expiré — nouveau login en cours...");
        return doLogin();
    }

    // -------------------------------------------------------------------------
    // Login interne — refait un login complet et met à jour le cache
    // -------------------------------------------------------------------------

    private Mono<String> doLogin() {
        return authPort.login(username, password)
                .flatMap(loginResponse -> {
                    // AuthPort.AuthResponse est un record : accessToken() (pas getAccessToken())
                    String token = loginResponse.accessToken();

                    // Extraire l'expiration directement depuis le claim 'exp' du JWT
                    Instant expiresAt = extractExpFromJwt(token);
                    long remainingSeconds = expiresAt.getEpochSecond() - Instant.now().getEpochSecond();

                    currentToken = new HeldToken(token, expiresAt);

                    log.info("✅ [KERNEL TOKEN] Nouveau token obtenu, valide jusqu'à {} ({} secondes)",
                            expiresAt, remainingSeconds);

                    return Mono.just(token);
                })
                .onErrorResume(e -> {
                    log.error("❌ [KERNEL TOKEN] Impossible d'obtenir un token système : {}. " +
                              "Les appels Kernel au nom du système échoueront.", e.getMessage());
                    return Mono.error(e);
                });
    }

    // -------------------------------------------------------------------------
    // Utilitaires
    // -------------------------------------------------------------------------

    /**
     * Extrait l'instant d'expiration depuis le claim 'exp' du JWT.
     * Fallback à now + 15 minutes si le JWT ne peut pas être décodé.
     */
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
            log.warn("⚠️ [KERNEL TOKEN] Impossible de lire 'exp' depuis le JWT, fallback 15min : {}", e.getMessage());
            return Instant.now().plusSeconds(900);
        }
    }

    /**
     * Retourne l'instant d'expiration du token courant, ou null si aucun token.
     */
    public Instant getCurrentTokenExpiry() {
        HeldToken held = currentToken;
        return held != null ? held.expiresAt() : null;
    }

    /**
     * Force un renouvellement même si le token est encore valide.
     * Utile après une révocation ou un changement de mot de passe.
     */
    public Mono<String> forceRefresh() {
        log.info("🔄 [KERNEL TOKEN] Renouvellement forcé demandé.");
        currentToken = null;
        return doLogin();
    }
}
