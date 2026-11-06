package com.yowyob.fleet.infrastructure.adapters.outbound.messaging;

import com.yowyob.fleet.domain.ports.out.SmsPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Aucun fournisseur SMS n'est encore branché : l'envoi échoue proprement (false) et le
 * gestionnaire remet le code en main propre. Un adaptateur réel (passerelle opérateur,
 * notification Kernel) remplacera ce bean en implémentant {@link SmsPort}.
 */
@Slf4j
@Component
public class UnconfiguredSmsAdapter implements SmsPort {

    @Override
    public Mono<Boolean> sendSms(String phoneNumber, String text) {
        // Le contenu (code d'activation) n'est jamais journalisé.
        log.info("[SMS] Aucun fournisseur SMS configuré : message non envoyé.");
        return Mono.just(false);
    }
}
