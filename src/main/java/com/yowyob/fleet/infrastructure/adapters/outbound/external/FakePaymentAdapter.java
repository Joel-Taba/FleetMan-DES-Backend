package com.yowyob.fleet.infrastructure.adapters.outbound.external;

import com.yowyob.fleet.domain.ports.out.ExternalPaymentPort;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.dto.WalletExternalResponse;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Simulateur du service de paiement externe.
 * Activé par : application.external.payment-mode=fake
 * Retourne un solde fictif de 500 000 XAF et simule les transactions.
 */
@Slf4j
public class FakePaymentAdapter implements ExternalPaymentPort {

    private static final BigDecimal FAKE_BALANCE = new BigDecimal("500000.00");

    @Override
    public Mono<BigDecimal> getBalance(UUID ownerId, String token) {
        log.info("🛠 [FAKE PAYMENT] getBalance: owner={} → {} XAF", ownerId, FAKE_BALANCE);
        return Mono.just(FAKE_BALANCE);
    }

    @Override
    public Mono<WalletExternalResponse> initializeWallet(UUID ownerId, String ownerName, String token) {
        UUID walletId = UUID.nameUUIDFromBytes(("wallet-" + ownerId).getBytes());
        log.info("🛠 [FAKE PAYMENT] initializeWallet: owner={} name={} → walletId={}", ownerId, ownerName, walletId);
        return Mono.just(new WalletExternalResponse(walletId, ownerId, ownerName, FAKE_BALANCE));
    }

    @Override
    public Mono<UUID> debitWallet(UUID ownerId, BigDecimal amount, String token) {
        UUID txId = UUID.randomUUID();
        log.info("🛠 [FAKE PAYMENT] debitWallet: owner={} amount={} XAF → txId={}", ownerId, amount, txId);
        return Mono.just(txId);
    }

    @Override
    public Mono<UUID> creditWallet(UUID ownerId, BigDecimal amount, String token) {
        UUID txId = UUID.randomUUID();
        log.info("🛠 [FAKE PAYMENT] creditWallet: owner={} amount={} XAF → txId={}", ownerId, amount, txId);
        return Mono.just(txId);
    }
}
