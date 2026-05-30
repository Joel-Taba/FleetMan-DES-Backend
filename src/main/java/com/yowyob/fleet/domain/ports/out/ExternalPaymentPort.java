package com.yowyob.fleet.domain.ports.out;

import com.yowyob.fleet.infrastructure.adapters.outbound.external.dto.WalletExternalResponse;
import reactor.core.publisher.Mono;
import java.math.BigDecimal;
import java.util.UUID;

public interface ExternalPaymentPort {
    Mono<BigDecimal> getBalance(UUID ownerId, String token);

    // CORRECTION : On renvoie l'objet complet au lieu de juste l'UUID
    Mono<WalletExternalResponse> initializeWallet(UUID ownerId, String ownerName, String token);

    Mono<UUID> debitWallet(UUID ownerId, BigDecimal amount, String token);
    Mono<UUID> creditWallet(UUID ownerId, BigDecimal amount, String token);
}