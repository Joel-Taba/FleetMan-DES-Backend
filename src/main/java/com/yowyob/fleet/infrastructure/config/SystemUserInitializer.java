package com.yowyob.fleet.infrastructure.config;

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
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Initialise le compte système au démarrage en obtenant un token via KernelTokenHolder.
 * Plus de logique de création de compte ici — le compte doit exister dans Kernel au préalable.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemUserInitializer {

    private final KernelTokenHolder tokenHolder;
    private final GeofenceAuthClient geofenceAuthClient;

    @Value("${application.geofence-system-user.username}")
    private String username;

    @Value("${application.geofence-system-user.password}")
    private String password;

    @Value("${application.geofence.enabled:false}")
    private boolean geofenceEnabled;

    @EventListener(ApplicationReadyEvent.class)
    public void initSystemUser() {
        log.info("🚀 [SYSTEM_USER] Initialisation pour : {}", username);

        // --- 1. INITIALISATION AUTH CENTRAL via KernelTokenHolder ---
        Mono<Void> authCentralFlow = tokenHolder.getValidAccessToken()
                .doOnSuccess(token -> log.info(
                        "✅ [AUTH_CENTRAL] Login réussi. Token système valide jusqu'à : {}",
                        tokenHolder.getCurrentTokenExpiry()))
                .doOnError(e -> log.error(
                        "❌ [AUTH_CENTRAL] Login du compte système impossible : {}. " +
                        "Vérifiez que '{}' existe dans Kernel et que le mot de passe " +
                        "dans application.yml est correct.", e.getMessage(), username))
                .onErrorResume(e -> Mono.empty())
                .then();

        // Si Geofence est désactivé, on s'arrête après Auth Central
        if (!geofenceEnabled) {
            authCentralFlow
                    .doOnTerminate(() -> log.info("🏁 [SYSTEM_USER] Fin du processus (Geofence désactivé)."))
                    .subscribe();
            return;
        }

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
                .then();

        // --- EXÉCUTION SÉQUENTIELLE ---
        authCentralFlow
                .then(geofenceFlow)
                .doOnTerminate(() -> log.info("🏁 [SYSTEM_USER] Fin du processus."))
                .subscribe();
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
