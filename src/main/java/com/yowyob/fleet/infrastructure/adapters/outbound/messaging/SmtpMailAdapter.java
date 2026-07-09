package com.yowyob.fleet.infrastructure.adapters.outbound.messaging;

import com.yowyob.fleet.domain.ports.out.MailPort;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;

/**
 * Adaptateur SMTP d'envoi d'emails transactionnels.
 *
 * JavaMailSender est une API bloquante : l'envoi est déporté sur le
 * scheduler boundedElastic pour ne pas bloquer les threads réactifs.
 *
 * Dégradation gracieuse : si l'envoi échoue (SMTP indisponible,
 * identifiants absents, désactivé), la méthode retourne false sans
 * propager d'erreur.
 */
@Slf4j
@Component
public class SmtpMailAdapter implements MailPort {

    private final JavaMailSender mailSender;

    @Value("${application.mail.enabled:true}")
    private boolean enabled;

    @Value("${application.mail.from:no-reply@fleetman.cm}")
    private String from;

    @Value("${application.mail.from-name:FleetMan}")
    private String fromName;

    @Value("${spring.mail.username:}")
    private String smtpUsername;

    public SmtpMailAdapter(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public Mono<Boolean> sendEmail(String to, String subject, String body) {
        if (!enabled) {
            log.info("✉️ [MAIL] Envoi désactivé (application.mail.enabled=false). Email non envoyé à {}", to);
            return Mono.just(false);
        }
        if (smtpUsername == null || smtpUsername.isBlank()) {
            log.warn("✉️ [MAIL] Identifiants SMTP absents (MAIL_USERNAME/MAIL_PASSWORD). "
                    + "Email de rejet non envoyé à {}", to);
            return Mono.just(false);
        }
        if (to == null || to.isBlank()) {
            log.warn("✉️ [MAIL] Adresse destinataire vide, envoi ignoré.");
            return Mono.just(false);
        }

        return Mono.fromCallable(() -> {
                    MimeMessage message = mailSender.createMimeMessage();
                    MimeMessageHelper helper = new MimeMessageHelper(
                            message, false, StandardCharsets.UTF_8.name());
                    helper.setTo(to.trim());
                    helper.setSubject(subject != null ? subject : "");
                    helper.setText(body != null ? body : "", false);
                    try {
                        helper.setFrom(new InternetAddress(from, fromName, StandardCharsets.UTF_8.name()));
                    } catch (Exception e) {
                        helper.setFrom(from);
                    }
                    mailSender.send(message);
                    return true;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(ok -> log.info("✅ [MAIL] Email envoyé à {} (sujet: {})", to, subject))
                .onErrorResume(MailException.class, e -> {
                    log.error("⚠️ [MAIL] Échec d'envoi SMTP à {} : {}", to, e.getMessage());
                    return Mono.just(false);
                })
                .onErrorResume(e -> {
                    log.error("⚠️ [MAIL] Erreur inattendue lors de l'envoi à {} : {}", to, e.getMessage());
                    return Mono.just(false);
                });
    }
}
