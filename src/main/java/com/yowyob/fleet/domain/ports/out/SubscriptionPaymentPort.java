package com.yowyob.fleet.domain.ports.out;

import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Point de branchement du service de paiement des abonnements.
 *
 * <p>FleetMan crée la commande (montant calculé côté serveur) puis demande au fournisseur
 * d'initier le règlement. Le fournisseur confirme ensuite le paiement de façon asynchrone
 * (webhook) en appelant {@code SubscriptionCheckoutService.confirmPayment(reference)} ; le
 * paiement n'est jamais considéré comme acquis sur la seule déclaration du client.
 *
 * <p>Tant que le service de paiement réel n'est pas branché, l'adaptateur actif est
 * {@code SimulatedSubscriptionPaymentAdapter}, clairement identifié comme simulateur.
 */
public interface SubscriptionPaymentPort {

    /** Nom du fournisseur, enregistré sur la commande (ex. SIMULATOR, MOBILE_MONEY…). */
    String providerName();

    /** true pour le simulateur : la confirmation manuelle (bouton « payer ») est alors permise. */
    boolean isSimulation();

    /** Initie le règlement d'une commande ; le statut retourné est en général PENDING. */
    Mono<PaymentSession> initiate(PaymentRequest request);

    record PaymentRequest(UUID orderId, UUID managerId, BigDecimal amount, String currency, String description) {}

    /**
     * @param reference    référence du paiement chez le fournisseur (sert à la confirmation)
     * @param instructions consigne à afficher au payeur (lien de paiement, code USSD…)
     */
    record PaymentSession(String reference, Status status, String instructions) {
        public enum Status { PENDING, SUCCEEDED, FAILED }
    }
}
