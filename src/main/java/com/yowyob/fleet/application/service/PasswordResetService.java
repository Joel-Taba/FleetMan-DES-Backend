package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.AuthException;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.domain.ports.out.MailPort;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.FakeAuthAdapter;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.FakePasswordResetStore;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.KernelAuthAdapter;
import com.yowyob.fleet.infrastructure.config.bootstrap.DemoTestAccounts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Mot de passe oublié / réinitialisation.
 * <ul>
 *   <li>mode kernel → délègue au Kernel</li>
 *   <li>mode fake → token local + email SMTP (si configuré) + override mdp en mémoire</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final AuthPort authPort;
    private final MailPort mailPort;
    private final FakePasswordResetStore resetStore;

    @Value("${application.frontend.url:http://localhost:3001}")
    private String frontendUrl;

    public Mono<Void> forgotPassword(String email) {
        if (email == null || email.isBlank()) {
            return Mono.error(new AuthException(
                    "Adresse email requise.", HttpStatus.BAD_REQUEST, "AUTH_010"));
        }
        String normalized = email.trim().toLowerCase();

        if (authPort instanceof KernelAuthAdapter kernelAdapter) {
            return kernelAdapter.forgotPassword(normalized);
        }

        // Mode fake : réponse toujours 204 (anti-énumération), email seulement si compte demo
        boolean known = DemoTestAccounts.findByIdentifier(normalized).isPresent();
        if (!known) {
            log.info("🛠 [FAKE] Forgot password — email inconnu (ack silencieux): {}", normalized);
            return Mono.empty();
        }

        String token = resetStore.issueToken(normalized);
        String link = frontendUrl.replaceAll("/$", "") + "/reset-password?token=" + token;
        String subject = "Réinitialisation de votre mot de passe FleetMan";
        String body = """
                Bonjour,

                Vous avez demandé la réinitialisation de votre mot de passe FleetMan.
                Cliquez sur le lien ci-dessous (valide 1 heure) :

                %s

                Si vous n'êtes pas à l'origine de cette demande, ignorez cet email.

                — L'équipe FleetMan
                """.formatted(link);

        log.info("🛠 [FAKE] Forgot password pour {} — lien: {}", normalized, link);

        return mailPort.sendEmail(normalized, subject, body)
                .doOnNext(sent -> {
                    if (!sent) {
                        log.warn("✉️ [FAKE] Email non envoyé (SMTP off/absent). "
                                + "Utilisez le lien loggé ci-dessus pour tester /reset-password.");
                    }
                })
                .then();
    }

    public Mono<Void> resetPassword(String resetToken, String newPassword) {
        if (newPassword == null || newPassword.length() < 8) {
            return Mono.error(new AuthException(
                    "Le mot de passe doit contenir au moins 8 caractères.",
                    HttpStatus.BAD_REQUEST, "AUTH_011"));
        }

        if (authPort instanceof KernelAuthAdapter kernelAdapter) {
            return kernelAdapter.resetPassword(resetToken, newPassword);
        }

        if (!(authPort instanceof FakeAuthAdapter fakeAuth)) {
            return Mono.empty();
        }

        var emailOpt = resetStore.consumeToken(resetToken);
        if (emailOpt.isEmpty()) {
            return Mono.error(new AuthException(
                    "Lien de réinitialisation invalide ou expiré.",
                    HttpStatus.BAD_REQUEST, "AUTH_013"));
        }
        String email = emailOpt.get();
        if (!fakeAuth.overridePassword(email, newPassword)) {
            return Mono.error(new AuthException(
                    "Compte introuvable pour ce token.",
                    HttpStatus.BAD_REQUEST, "AUTH_012"));
        }
        log.info("🛠 [FAKE] Mot de passe réinitialisé pour {}", email);
        return Mono.empty();
    }
}
