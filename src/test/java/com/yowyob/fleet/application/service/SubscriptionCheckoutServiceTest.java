package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.SubscriptionException;
import com.yowyob.fleet.domain.model.BillingCycle;
import com.yowyob.fleet.domain.ports.out.SubscriptionPaymentPort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.SubscriptionOrderEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.SubscriptionPlanEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.SubscriptionOrderR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.SubscriptionPlanR2dbcRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubscriptionCheckoutServiceTest {

    @Mock SubscriptionPlanR2dbcRepository planRepo;
    @Mock SubscriptionOrderR2dbcRepository orderRepo;
    @Mock SubscriptionPlanService planService;
    @Mock SubscriptionPaymentPort paymentPort;
    @Mock DatabaseClient db;

    private SubscriptionCheckoutService service;
    private final UUID managerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SubscriptionCheckoutService(planRepo, orderRepo, planService, paymentPort, db);
        when(orderRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(orderRepo.findByCompanyId(managerId)).thenReturn(Flux.empty());
        when(planService.assertUsageFits(any(), any())).thenReturn(Mono.empty());
        when(paymentPort.providerName()).thenReturn("SIMULATOR");
        when(paymentPort.isSimulation()).thenReturn(true);
        when(paymentPort.initiate(any())).thenReturn(Mono.just(new SubscriptionPaymentPort.PaymentSession(
                "SIM-1", SubscriptionPaymentPort.PaymentSession.Status.PENDING, "simulé")));
    }

    private SubscriptionPlanEntity plan(String name, long monthly, Long annual, boolean active) {
        SubscriptionPlanEntity p = new SubscriptionPlanEntity();
        p.setId(UUID.randomUUID());
        p.setName(name);
        p.setMonthlyPrice(BigDecimal.valueOf(monthly));
        p.setAnnualPrice(annual != null ? BigDecimal.valueOf(annual) : null);
        p.setCurrency("XAF");
        p.setActive(active);
        when(planRepo.findById(p.getId())).thenReturn(Mono.just(p));
        return p;
    }

    @Test
    void paidPlan_createsPendingOrder_withServerSidePrice() {
        SubscriptionPlanEntity pro = plan("Pro", 45000, 450000L, true);

        StepVerifier.create(service.checkout(managerId, pro.getId(), BillingCycle.ANNUAL))
                .assertNext(r -> {
                    assertEquals("PENDING", r.order().getStatus());
                    assertEquals(0, BigDecimal.valueOf(450000).compareTo(r.order().getAmount()));
                    assertEquals("SIM-1", r.order().getPaymentReference());
                    assertEquals("ANNUAL", r.order().getBillingCycle());
                })
                .verifyComplete();
        verify(planService, never()).activateSubscription(any(), any(), any(), any());
    }

    @Test
    void freePlan_isActivatedImmediately_withoutPayment() {
        SubscriptionPlanEntity free = plan("Découverte", 0, null, true);
        when(planService.activateSubscription(eq(managerId), eq(free), any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(service.checkout(managerId, free.getId(), BillingCycle.MONTHLY))
                .assertNext(r -> assertEquals("PAID", r.order().getStatus()))
                .verifyComplete();
        verify(paymentPort, never()).initiate(any());
    }

    @Test
    void inactivePlan_andMissingAnnualPrice_areRejected() {
        SubscriptionPlanEntity old = plan("Test Plan", 5000, null, false);
        StepVerifier.create(service.checkout(managerId, old.getId(), BillingCycle.MONTHLY))
                .expectErrorSatisfies(e -> assertEquals("SUB_010", ((SubscriptionException) e).getBusinessCode()))
                .verify();

        SubscriptionPlanEntity monthlyOnly = plan("Mensuel", 5000, null, true);
        StepVerifier.create(service.checkout(managerId, monthlyOnly.getId(), BillingCycle.ANNUAL))
                .expectErrorSatisfies(e -> assertEquals("SUB_012", ((SubscriptionException) e).getBusinessCode()))
                .verify();
    }

    @Test
    void confirmingAnAlreadyPaidOrder_doesNotExtendTwice() {
        SubscriptionOrderEntity paid = new SubscriptionOrderEntity();
        paid.setId(UUID.randomUUID());
        paid.setCompanyId(managerId);
        paid.setStatus("PAID");
        paid.setPeriodEnd(LocalDate.of(2027, 1, 1));
        when(orderRepo.findByIdForUpdate(paid.getId())).thenReturn(Mono.just(paid));

        StepVerifier.create(service.confirmPayment(paid.getId()))
                .assertNext(o -> assertEquals(LocalDate.of(2027, 1, 1), o.getPeriodEnd()))
                .verifyComplete();
        verify(planService, never()).activateSubscription(any(), any(), any(), any());
        verify(orderRepo, never()).save(any());
    }

    @Test
    void cancelledOrder_cannotBePaid() {
        SubscriptionOrderEntity cancelled = new SubscriptionOrderEntity();
        cancelled.setId(UUID.randomUUID());
        cancelled.setCompanyId(managerId);
        cancelled.setStatus("CANCELLED");
        when(orderRepo.findByIdForUpdate(cancelled.getId())).thenReturn(Mono.just(cancelled));

        StepVerifier.create(service.confirmPayment(cancelled.getId()))
                .expectErrorSatisfies(e -> assertEquals("SUB_014", ((SubscriptionException) e).getBusinessCode()))
                .verify();
    }

    @Test
    void simulation_isRefused_onceARealProviderIsPlugged() {
        when(paymentPort.isSimulation()).thenReturn(false);
        StepVerifier.create(service.simulatePayment(managerId, UUID.randomUUID()))
                .expectErrorSatisfies(e -> assertEquals("SUB_015", ((SubscriptionException) e).getBusinessCode()))
                .verify();
    }
}
