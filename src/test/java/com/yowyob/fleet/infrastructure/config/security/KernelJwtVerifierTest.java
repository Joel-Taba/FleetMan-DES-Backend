package com.yowyob.fleet.infrastructure.config.security;

import com.yowyob.fleet.domain.exception.AuthException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KernelJwtVerifierTest {

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final String PAYLOAD =
            "{\"sub\":\"2c9a43d2-8406-4860-b33b-f7ba989885ba\",\"permissions\":[\"ROLE_FLEET_MANAGER\"]}";

    private KeyPair kernelKeys;
    private KeyPair attackerKeys;
    private AtomicInteger jwksCalls;
    private KernelJwtVerifier verifier;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        kernelKeys = generator.generateKeyPair();
        attackerKeys = generator.generateKeyPair();
        jwksCalls = new AtomicInteger();
        String jwks = jwks((RSAPublicKey) kernelKeys.getPublic(), "kernel-core-key-1");
        verifier = new KernelJwtVerifier(() -> Mono.fromSupplier(() -> {
            jwksCalls.incrementAndGet();
            return jwks;
        }));
    }

    @Test
    void acceptsTokenSignedByKernel() throws Exception {
        String token = sign("{\"alg\":\"RS256\",\"kid\":\"kernel-core-key-1\"}", PAYLOAD, kernelKeys.getPrivate());

        StepVerifier.create(verifier.verify(token)).verifyComplete();
        StepVerifier.create(verifier.verify(token)).verifyComplete();
        assertEquals(1, jwksCalls.get(), "le JWKS doit être mis en cache");
    }

    @Test
    void rejectsTamperedPayload() throws Exception {
        String token = sign("{\"alg\":\"RS256\",\"kid\":\"kernel-core-key-1\"}", PAYLOAD, kernelKeys.getPrivate());
        String[] parts = token.split("\\.");
        String forgedPayload = B64.encodeToString(
                PAYLOAD.replace("ROLE_FLEET_MANAGER", "ROLE_FLEET_SUPER_ADMIN").getBytes(StandardCharsets.UTF_8));

        StepVerifier.create(verifier.verify(parts[0] + "." + forgedPayload + "." + parts[2]))
                .expectError(AuthException.class)
                .verify();
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() throws Exception {
        String token = sign("{\"alg\":\"RS256\",\"kid\":\"kernel-core-key-1\"}", PAYLOAD, attackerKeys.getPrivate());

        StepVerifier.create(verifier.verify(token)).expectError(AuthException.class).verify();
    }

    @Test
    void rejectsUnsignedToken() {
        String token = B64.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8))
                + "." + B64.encodeToString(PAYLOAD.getBytes(StandardCharsets.UTF_8)) + ".";

        StepVerifier.create(verifier.verify(token)).expectError(AuthException.class).verify();
        assertEquals(0, jwksCalls.get());
    }

    @Test
    void unknownKidReloadsJwksAtMostOncePerInterval() throws Exception {
        String token = sign("{\"alg\":\"RS256\",\"kid\":\"forged-kid\"}", PAYLOAD, attackerKeys.getPrivate());

        StepVerifier.create(verifier.verify(token)).expectError(AuthException.class).verify();
        StepVerifier.create(verifier.verify(token)).expectError(AuthException.class).verify();
        assertEquals(1, jwksCalls.get(), "un kid inconnu ne doit pas recharger le JWKS à chaque requête");
    }

    private static String sign(String header, String payload, PrivateKey key) throws Exception {
        String signingInput = B64.encodeToString(header.getBytes(StandardCharsets.UTF_8))
                + "." + B64.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(key);
        signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        return signingInput + "." + B64.encodeToString(signature.sign());
    }

    private static String jwks(RSAPublicKey key, String kid) {
        return "{\"keys\":[{\"kty\":\"RSA\",\"use\":\"sig\",\"alg\":\"RS256\",\"kid\":\"" + kid + "\","
                + "\"n\":\"" + B64.encodeToString(unsigned(key.getModulus().toByteArray())) + "\","
                + "\"e\":\"" + B64.encodeToString(unsigned(key.getPublicExponent().toByteArray())) + "\"}]}";
    }

    private static byte[] unsigned(byte[] bytes) {
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] trimmed = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
            return trimmed;
        }
        return bytes;
    }
}
