package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.domain.ports.out.ExternalPaymentPort;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.dto.WalletExternalResponse; // Import du DTO
import com.yowyob.fleet.infrastructure.config.OpenApiConfig;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name =  OpenApiConfig.TAG_PAYMENTS, description = "Intégration Service Paiement")
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {

    private final ExternalPaymentPort paymentPort;

    private AuthPort.UserDetail getUser(Authentication auth) {
        return (AuthPort.UserDetail) auth.getPrincipal();
    }

    @GetMapping("/balance")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_DRIVER')")
    @Operation(summary = "Consulter mon solde")
    public Mono<BigDecimal> getMyBalance(
            Authentication auth,
            @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader
    ) {
        return paymentPort.getBalance(getUser(auth).id(), authHeader);
    }

    @PostMapping("/wallet")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_DRIVER')")
    @Operation(summary = "Initialiser mon wallet")
    // Changement du type de retour : UUID -> WalletExternalResponse
    public Mono<WalletExternalResponse> createMyWallet(
            Authentication auth,
            @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader
    ) {
        var user = getUser(auth);
        String name = user.firstName() + " " + user.lastName();
        return paymentPort.initializeWallet(user.id(), name, authHeader);
    }

    @PostMapping("/recharge")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_DRIVER')")
    @Operation(summary = "Recharger mon compte")
    public Mono<UUID> rechargeWallet(
            @RequestParam BigDecimal amount,
            Authentication auth,
            @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader
    ) {
        return paymentPort.creditWallet(getUser(auth).id(), amount, authHeader);
    }

    @PostMapping("/simulate-debit")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "TEST: Simuler un paiement")
    public Mono<UUID> simulatePayment(
            @RequestParam BigDecimal amount,
            Authentication auth,
            @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader
    ) {
        return paymentPort.debitWallet(getUser(auth).id(), amount, authHeader);
    }
}