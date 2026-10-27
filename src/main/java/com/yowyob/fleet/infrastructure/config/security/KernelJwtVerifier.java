package com.yowyob.fleet.infrastructure.config.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.fleet.domain.exception.AuthException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Vérifie la signature RS256 des JWT émis par le Kernel avec sa clé publique
 * (JWKS : GET {application.kernel.url}/.well-known/jwks.json).
 *
 * Les clés sont mises en cache par {@code kid}. Un {@code kid} inconnu déclenche
 * un rechargement du JWKS (rotation de clé côté Kernel), au plus une fois par
 * minute pour qu'un token forgé avec des {@code kid} aléatoires ne puisse pas
 * provoquer un appel au Kernel à chaque requête.
 */
@Slf4j
public class KernelJwtVerifier {

    private static final Duration MIN_RELOAD_INTERVAL = Duration.ofMinutes(1);

    private final Supplier<Mono<String>> jwksFetcher;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, PublicKey> keysByKid = new ConcurrentHashMap<>();
    private volatile Instant lastReload = Instant.EPOCH;

    public KernelJwtVerifier(Supplier<Mono<String>> jwksFetcher) {
        this.jwksFetcher = jwksFetcher;
    }

    public static KernelJwtVerifier fromKernel(WebClient kernelWebClient) {
        return new KernelJwtVerifier(() -> kernelWebClient.get()
                .uri("/.well-known/jwks.json")
                .retrieve()
                .bodyToMono(String.class));
    }

    /**
     * Termine à vide si la signature est valide, sinon en erreur
     * {@link AuthException#tokenExpired()} (même réponse qu'un token malformé).
     */
    public Mono<Void> verify(String rawToken) {
        String[] parts = rawToken.split("\\.");
        if (parts.length != 3) {
            return Mono.error(AuthException.tokenExpired());
        }
        JsonNode header;
        try {
            header = objectMapper.readTree(Base64.getUrlDecoder().decode(parts[0]));
        } catch (Exception e) {
            log.warn("⚠️ [JWT] En-tête illisible : {}", e.getMessage());
            return Mono.error(AuthException.tokenExpired());
        }
        if (!"RS256".equals(header.path("alg").asText())) {
            log.warn("⚠️ [JWT] Algorithme refusé : {}", header.path("alg").asText());
            return Mono.error(AuthException.tokenExpired());
        }
        String kid = header.hasNonNull("kid") ? header.get("kid").asText() : null;

        return resolveKey(kid).flatMap(key -> {
            if (hasValidSignature(key, parts)) {
                return Mono.<Void>empty();
            }
            log.warn("⚠️ [JWT] Signature invalide (kid={})", kid);
            return Mono.<Void>error(AuthException.tokenExpired());
        });
    }

    private Mono<PublicKey> resolveKey(String kid) {
        PublicKey cached = findKey(kid);
        if (cached != null) {
            return Mono.just(cached);
        }
        if (Instant.now().isBefore(lastReload.plus(MIN_RELOAD_INTERVAL))) {
            log.warn("⚠️ [JWT] Clé inconnue (kid={}), rechargement JWKS trop récent", kid);
            return Mono.error(AuthException.tokenExpired());
        }
        lastReload = Instant.now();
        return jwksFetcher.get()
                .doOnNext(this::loadKeys)
                .onErrorResume(e -> {
                    log.error("❌ [JWT] Chargement JWKS Kernel impossible : {}", e.getMessage());
                    return Mono.error(AuthException.remoteServiceUnavailable());
                })
                .then(Mono.defer(() -> {
                    PublicKey key = findKey(kid);
                    if (key == null) {
                        log.warn("⚠️ [JWT] Aucune clé Kernel pour kid={}", kid);
                        return Mono.<PublicKey>error(AuthException.tokenExpired());
                    }
                    return Mono.just(key);
                }));
    }

    /** Sans {@code kid} dans l'en-tête, on n'accepte que si le JWKS ne contient qu'une clé. */
    private PublicKey findKey(String kid) {
        if (kid != null) {
            return keysByKid.get(kid);
        }
        return keysByKid.size() == 1 ? keysByKid.values().iterator().next() : null;
    }

    private void loadKeys(String jwksJson) {
        try {
            JsonNode keys = objectMapper.readTree(jwksJson).path("keys");
            Map<String, PublicKey> loaded = new ConcurrentHashMap<>();
            for (JsonNode jwk : keys) {
                if (!"RSA".equals(jwk.path("kty").asText())) continue;
                if (jwk.hasNonNull("use") && !"sig".equals(jwk.get("use").asText())) continue;
                BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(jwk.path("n").asText()));
                BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(jwk.path("e").asText()));
                PublicKey key = KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus, exponent));
                loaded.put(jwk.path("kid").asText(""), key);
            }
            keysByKid.clear();
            keysByKid.putAll(loaded);
            log.info("🔑 [JWT] {} clé(s) Kernel chargée(s) : {}", loaded.size(), loaded.keySet());
        } catch (Exception e) {
            throw new IllegalStateException("JWKS Kernel invalide : " + e.getMessage(), e);
        }
    }

    private static boolean hasValidSignature(PublicKey key, String[] parts) {
        try {
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(key);
            verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            return verifier.verify(Base64.getUrlDecoder().decode(parts[2]));
        } catch (Exception e) {
            return false;
        }
    }
}
