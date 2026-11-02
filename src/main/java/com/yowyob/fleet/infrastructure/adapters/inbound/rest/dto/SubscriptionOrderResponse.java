package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.SubscriptionOrderEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Commande d'abonnement. {@code paymentInstructions} : consigne de paiement à afficher (null
 * si rien à payer) ; {@code simulation} : true tant que le paiement passe par le simulateur.
 */
public record SubscriptionOrderResponse(
        UUID id,
        UUID managerId,
        UUID planId,
        String planName,
        String billingCycle,
        BigDecimal amount,
        String currency,
        String status,
        String paymentProvider,
        String paymentReference,
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDateTime createdAt,
        LocalDateTime paidAt,
        String paymentInstructions,
        boolean simulation
) {
    public static SubscriptionOrderResponse from(SubscriptionOrderEntity o, String instructions, boolean simulation) {
        return new SubscriptionOrderResponse(o.getId(), o.getCompanyId() != null ? o.getCompanyId() : o.getManagerId(), o.getPlanId(), o.getPlanName(),
                o.getBillingCycle(), o.getAmount(), o.getCurrency(), o.getStatus(), o.getPaymentProvider(),
                o.getPaymentReference(), o.getPeriodStart(), o.getPeriodEnd(), o.getCreatedAt(), o.getPaidAt(),
                instructions, simulation);
    }
}
