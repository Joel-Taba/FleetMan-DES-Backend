package com.yowyob.fleet.infrastructure.config;

import com.yowyob.fleet.domain.ports.in.AuthUseCase;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.client.GeofenceAuthClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
@RequiredArgsConstructor
public class SystemUserInitializer {

    private final GeofenceAuthClient geofenceAuthClient;
    private final AuthPort authPort;

    @Value("${application.geofence-system-user.username}")
    private String username;

    @Value("${application.geofence-system-user.password}")
    private String password;

    @EventListener(ApplicationReadyEvent.class)
    public void initSystemUser() {
        log.info("🚀 [SYSTEM_USER] Initialisation pour : {}", username);

        // --- 1. INITIALISATION AUTH CENTRAL ---
        Mono<Void> authCentralFlow = authPort.login(username, password).then()
                .doOnSuccess(res -> log.info("✅ [AUTH_CENTRAL] Login réussi."))
                .onErrorResume(e -> {
                    log.warn("⚠️ [AUTH_CENTRAL] Login impossible. Tentative de création...");
                    return attemptAuthRegistration(1).then();
                }).then();

        // --- 2. INITIALISATION GEOFENCE ENGINE ---
        Map<String, String> loginReq = Map.of(
            "type", "username", 
            "username", username, 
            "password", password
        );

        Mono<Void> geofenceFlow = geofenceAuthClient.login(loginReq).then()
                .doOnSuccess(res -> log.info("✅ [GEOFENCE] Login réussi. Utilisateur opérationnel."))
                .onErrorResume(e -> {
                    log.warn("⚠️ [GEOFENCE] Login impossible. Tentative de synchronisation (Register)...");
                    return syncGeofenceWithRetry(1).then();
                })
                .then(); // <--- ✅ C'EST ICI : Convertit Mono<Map> en Mono<Void>

        // --- EXÉCUTION SÉQUENTIELLE ---
        authCentralFlow
                .then(geofenceFlow)
                .doOnTerminate(() -> log.info("🏁 [SYSTEM_USER] Fin du processus."))
                .subscribe();
    }

    private Mono<AuthPort.AuthResponse> attemptAuthRegistration(int attempt) {
        if (attempt > 3) {
            log.error("❌ [AUTH_CENTRAL] Abandon après 3 tentatives.");
            return Mono.empty();
        }

        String phone = generateRandomCameroonPhone();
        String email = username + attempt + "@yowyob.com"; 

        log.info("📩 [AUTH_CENTRAL] Tentative de création #{}...", attempt);

        AuthUseCase.RegisterCommand authReq = new AuthUseCase.RegisterCommand(
                username, password, email, phone,
                "System", "Fleet", List.of("FLEET_ADMIN"), null
        );

        return authPort.registerInRemote(authReq)
                .doOnSuccess(res -> log.info("✅ [AUTH_CENTRAL] Création réussie."))
                .onErrorResume(err -> {
                    if (isConflict(err)) {
                        log.warn("⚠️ [AUTH_CENTRAL] Conflit détecté, essai suivant...");
                        return attemptAuthRegistration(attempt + 1);
                    }
                    log.error("❌ [AUTH_CENTRAL] Erreur critique : {}", err.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<Void> syncGeofenceWithRetry(int attempt) {
        if (attempt > 3) {
            log.warn("⚠️ [GEOFENCE] Abandon de la synchronisation après 3 essais.");
            return Mono.empty();
        }

        String phone = generateRandomCameroonPhone();
        String email = username + attempt + "@yowyob.com";

        Map<String, Object> req = new HashMap<>();
        req.put("firstname", "System");
        req.put("lastname", "Fleet");
        req.put("username", username);
        req.put("phoneNumber", phone);
        req.put("email", email);
        req.put("password", password);
        req.put("password_confirmation", password);
        req.put("DOB", "1990-01-01");

        return geofenceAuthClient.register(req)
                .then() 
                .doOnSuccess(v -> log.info("✅ [GEOFENCE] Utilisateur synchronisé."))
                .onErrorResume(err -> {
                    if (isConflict(err)) {
                        log.info("ℹ️ [GEOFENCE] Conflit à la tentative #{} (déjà présent ou collision), essai suivant...", attempt);
                        return syncGeofenceWithRetry(attempt + 1);
                    }
                    log.error("❌ [GEOFENCE] Erreur lors de la synchronisation : {}", err.getMessage());
                    return Mono.empty();
                });
    }

    private String generateRandomCameroonPhone() {
        int suffix = ThreadLocalRandom.current().nextInt(10000000, 99999999);
        return "+2376" + suffix;
    }

    private boolean isConflict(Throwable t) {
        if (t instanceof WebClientResponseException wcre) {
            return wcre.getStatusCode() == HttpStatus.CONFLICT;
        }
        return t.getMessage() != null && t.getMessage().contains("409");
    }
}