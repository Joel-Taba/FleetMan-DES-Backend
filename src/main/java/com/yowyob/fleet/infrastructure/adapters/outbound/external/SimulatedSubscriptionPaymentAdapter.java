package com.yowyob.fleet.infrastructure.adapters.outbound.external;

import com.yowyob.fleet.domain.ports.out.SubscriptionPaymentPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * SIMULATEUR de paiement des abonnements — aucun argent n'est encaissé.
 *
 * <p>Actif tant que {@code application.subscription.payment-mode} vaut {@code simulated}
 * (valeur par défaut). Il génère une référence « SIM-… » et laisse la commande en attente ;
 * la confirmation se fait depuis l'écran d'abonnement (bouton « Simuler le paiement »), qui
 * emprunte exactement le même chemin que le futur webhook du service de paiement réel.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "application.subscription.payment-mode", havingValue = "simulated", matchIfMissing = true)
public class SimulatedSubscriptionPaymentAdapter implements SubscriptionPaymentPort {

    @Override
    public String providerName() {
        return "SIMULATOR";
    }

    @Override
    public boolean isSimulation() {
        return true;
    }

    @Override
    public Mono<PaymentSession> initiate(PaymentRequest request) {
        String reference = "SIM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("💳 [PAIEMENT SIMULÉ] Commande {} : {} {} → référence {}",
                request.orderId(), request.amount(), request.currency(), reference);
        return Mono.just(new PaymentSession(reference, PaymentSession.Status.PENDING,
                "Paiement simulé (service de paiement non encore branché) : confirmez-le depuis votre écran d'abonnement."));
    }
}
