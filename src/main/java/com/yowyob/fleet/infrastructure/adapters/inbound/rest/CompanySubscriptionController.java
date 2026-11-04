package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.application.service.CompanyScopeService;
import com.yowyob.fleet.application.service.PlanLimitGuard;
import com.yowyob.fleet.application.service.SubscriptionCheckoutService;
import com.yowyob.fleet.domain.model.BillingCycle;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.ManagerSubscriptionResponse;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.SubscriptionOrderResponse;
import com.yowyob.fleet.infrastructure.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Abonnement de l'entreprise : consulté par ses administrateurs et gestionnaires, souscrit,
 * changé ou renouvelé par son administrateur uniquement.
 */
@RestController
@RequestMapping("/api/v1/organization/subscription")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_ORGANIZATION)
@SecurityRequirement(name = "bearerAuth")
public class CompanySubscriptionController {

    private final PlanLimitGuard planLimitGuard;
    private final SubscriptionCheckoutService checkoutService;
    private final CompanyScopeService companyScope;

    /** Souscription / changement de plan / renouvellement : cycle MONTHLY (défaut) ou ANNUAL. */
    public record CheckoutRequest(@NotNull UUID planId, BillingCycle billingCycle) {}

    private static UUID userId(Authentication auth) {
        return ((AuthPort.UserDetail) auth.getPrincipal()).id();
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('FLEET_ADMIN', 'FLEET_MANAGER')")
    @Operation(summary = "Abonnement et limites de l'entreprise")
    public Mono<ManagerSubscriptionResponse> get(Authentication auth) {
        return planLimitGuard.getSubscription(userId(auth));
    }

    @PostMapping("/checkout")
    @PreAuthorize("hasRole('FLEET_ADMIN')")
    @Operation(summary = "Souscrire, changer de plan ou renouveler",
               description = "Plan gratuit : activé immédiatement. Plan payant : commande en attente de paiement "
                       + "(montant calculé côté serveur), réglée via le service de paiement, ou le simulateur tant "
                       + "qu'il n'est pas branché.")
    public Mono<SubscriptionOrderResponse> checkout(@Valid @RequestBody CheckoutRequest req, Authentication auth) {
        return companyScope.requireOwnCompany(userId(auth))
                .flatMap(companyId -> checkoutService.checkout(companyId, req.planId(), req.billingCycle()))
                .map(r -> SubscriptionOrderResponse.from(r.order(), r.paymentInstructions(), r.simulation()));
    }

    @GetMapping("/orders")
    @PreAuthorize("hasRole('FLEET_ADMIN')")
    @Operation(summary = "Commandes d'abonnement de l'entreprise")
    public Flux<SubscriptionOrderResponse> orders(Authentication auth) {
        boolean simulation = checkoutService.isSimulation();
        return companyScope.requireOwnCompany(userId(auth))
                .flatMapMany(checkoutService::listOrders)
                .map(o -> SubscriptionOrderResponse.from(o, null, simulation));
    }

    @PostMapping("/orders/{orderId}/simulate-payment")
    @PreAuthorize("hasRole('FLEET_ADMIN')")
    @Operation(summary = "[SIMULATEUR] Confirmer le paiement d'une commande",
               description = "Disponible uniquement tant que le paiement passe par le simulateur ; emprunte le même "
                       + "chemin que la confirmation du futur service de paiement.")
    public Mono<SubscriptionOrderResponse> simulatePayment(@PathVariable UUID orderId, Authentication auth) {
        return companyScope.requireOwnCompany(userId(auth))
                .flatMap(companyId -> checkoutService.simulatePayment(companyId, orderId))
                .map(o -> SubscriptionOrderResponse.from(o, null, true));
    }

    @PostMapping("/orders/{orderId}/cancel")
    @PreAuthorize("hasRole('FLEET_ADMIN')")
    @Operation(summary = "Annuler une commande en attente")
    public Mono<SubscriptionOrderResponse> cancelOrder(@PathVariable UUID orderId, Authentication auth) {
        return companyScope.requireOwnCompany(userId(auth))
                .flatMap(companyId -> checkoutService.cancelOrder(companyId, orderId))
                .map(o -> SubscriptionOrderResponse.from(o, null, checkoutService.isSimulation()));
    }
}
