package com.yowyob.fleet.domain.ports.out;

import reactor.core.publisher.Mono;

/**
 * Port sortant d'envoi d'emails transactionnels (SMTP).
 * L'implémentation doit se dégrader gracieusement : un échec d'envoi
 * ne doit jamais faire échouer l'opération métier appelante.
 */
public interface MailPort {

    /**
     * Envoie un email texte simple.
     *
     * @param to      adresse email du destinataire
     * @param subject sujet de l'email
     * @param body    corps du message (texte brut)
     * @return true si l'email a été remis au serveur SMTP, false sinon
     */
    Mono<Boolean> sendEmail(String to, String subject, String body);
}
