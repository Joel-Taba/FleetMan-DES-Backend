package com.yowyob.fleet.infrastructure.adapters.outbound.external;

import com.yowyob.fleet.domain.ports.out.ExternalPaymentPort;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.dto.TransactionExternalRequest;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.dto.TransactionExternalResponse;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.dto.WalletCreationRequest;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.dto.WalletExternalResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Component
public class PaymentApiAdapter implements ExternalPaymentPort {

    private final WebClient webClient;

    public PaymentApiAdapter(@Qualifier("paymentWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public Mono<BigDecimal> getBalance(UUID ownerId, String unusedToken) {
        return webClient.get()
                .uri("/api/v1/wallets/owner/" + ownerId)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(WalletExternalResponse.class)
                .map(WalletExternalResponse::balance)
                .onErrorResume(WebClientResponseException.NotFound.class, e -> Mono.just(BigDecimal.ZERO))
                .onErrorResume(WebClientResponseException.InternalServerError.class, e -> Mono.just(BigDecimal.ZERO));
    }

    @Override
    public Mono<WalletExternalResponse> initializeWallet(UUID ownerId, String ownerName, String unusedToken) {
        return webClient.get()
                .uri("/api/v1/wallets/owner/" + ownerId)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(WalletExternalResponse.class)
                .onErrorResume(e -> {
                    boolean isError = e instanceof WebClientResponseException.NotFound
                            || e instanceof WebClientResponseException.InternalServerError;
                    if (isError) {
                        return createWalletDirect(ownerId, ownerName);
                    }
                    return Mono.error(e);
                });
    }

    private Mono<WalletExternalResponse> createWalletDirect(UUID ownerId, String ownerName) {
        WalletCreationRequest request = new WalletCreationRequest(ownerId, ownerName);

        return webClient.post()
                .uri("/api/v1/wallets")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(WalletExternalResponse.class);
    }

    @Override
    public Mono<UUID> debitWallet(UUID ownerId, BigDecimal amount, String unusedToken) {
        // Cible l'endpoint de PAIEMENT (Débit)
        return processTransaction(ownerId, amount, "PAYMENT", "/api/v1/transactions/payment");
    }

    @Override
    public Mono<UUID> creditWallet(UUID ownerId, BigDecimal amount, String unusedToken) {
        // Cible l'endpoint de RECHARGE (Crédit)
        // Note: Si le service distant utilise un autre chemin, il faudra le mettre à jour ici.
        return processTransaction(ownerId, amount, "RECHARGE", "/api/v1/transactions");
    }

    /**
     * Méthode générique pour exécuter une transaction.
     * @param endpointUri L'URI spécifique à appeler (payment ou recharge)
     */
    private Mono<UUID> processTransaction(UUID ownerId, BigDecimal amount, String type, String endpointUri) {
        return webClient.get()
                .uri("/api/v1/wallets/owner/" + ownerId)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(WalletExternalResponse.class)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Wallet introuvable pour l'utilisateur : " + ownerId)))
                .flatMap(wallet -> {
                    // On construit la requête
                    TransactionExternalRequest req = new TransactionExternalRequest(wallet.id(), amount, type);

                    log.info("Envoi transaction {} vers {} pour wallet {}", type, endpointUri, wallet.id());

                    return webClient.post()
                            .uri(endpointUri) // Utilisation de l'URI dynamique
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON)
                            .bodyValue(req)
                            .retrieve()
                            .bodyToMono(TransactionExternalResponse.class);
                })
                .map(TransactionExternalResponse::id)
                .doOnError(e -> log.error("Erreur transaction paiement ({}) : {}", endpointUri, e.getMessage()));
    }
}