package com.yowyob.fleet.domain.ports.out;

import reactor.core.publisher.Mono;

/**
 * Envoi de SMS transactionnels (codes d'activation des chauffeurs).
 * Renvoie {@code false} sans erreur quand l'envoi est impossible (aucun fournisseur configuré,
 * numéro invalide) : l'appelant propose alors la remise du code en main propre.
 */
public interface SmsPort {

    Mono<Boolean> sendSms(String phoneNumber, String text);
}
