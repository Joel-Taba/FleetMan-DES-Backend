package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.SubscriptionException;
import com.yowyob.fleet.domain.model.BillingCycle;
import com.yowyob.fleet.domain.model.SubscriptionPlanRules;
import com.yowyob.fleet.domain.ports.out.SubscriptionPaymentPort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.SubscriptionOrderEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.SubscriptionPlanEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.SubscriptionOrderR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.SubscriptionPlanR2dbcRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Souscription, changement de plan et renouvellement par le gestionnaire.
 *
 * <ol>
 *   <li>{@link #checkout} : le plan doit être proposé et couvrir l'usage actuel ; le montant
 *       est calculé côté serveur selon le cycle. Plan gratuit : activation immédiate, sans
 *       paiement. Plan payant : commande PENDING, règlement initié auprès du port de paiement.</li>
 *   <li>{@link #confirmPayment} : appelé par le fournisseur (webhook) ou par le simulateur.
 *       Idempotent : une commande n'est payée — et l'abonnement prolongé — qu'une seule fois.
 *       Même plan encore actif : la période est ajoutée à la fin en cours ; sinon elle
 *       démarre le jour du paiement.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionCheckoutService {

    public static final String PENDING = "PENDING";
    public static final String PAID = "PAID";
    public static final String CANCELLED = "CANCELLED";
    public static final String FAILED = "FAILED";

    private final SubscriptionPlanR2dbcRepository planRepo;
    private final SubscriptionOrderR2dbcRepository orderRepo;
    private final SubscriptionPlanService planService;
    private final SubscriptionPaymentPort paymentPort;
    private final DatabaseClient db;

    /** Commande créée, avec la consigne de paiement à afficher (null si rien à payer). */
    public record CheckoutResult(SubscriptionOrderEntity order, String paymentInstructions, boolean simulation) {}

    @Transactional
    public Mono<CheckoutResult> checkout(UUID managerId, UUID planId, BillingCycle cycle) {
        BillingCycle billingCycle = cycle != null ? cycle : BillingCycle.MONTHLY;
        return planRepo.findById(planId)
                .switchIfEmpty(Mono.error(SubscriptionException.planNotFound()))
                .flatMap(plan -> {
                    if (!plan.isActive()) {
                        return Mono.error(SubscriptionException.planInactive(plan.getName()));
                    }
                    BigDecimal amount = SubscriptionPlanRules.priceFor(plan.getMonthlyPrice(), plan.getAnnualPrice(), billingCycle);
                    if (amount == null) {
                        return Mono.error(SubscriptionException.billingCycleUnavailable(plan.getName()));
                    }
                    return planService.assertUsageFits(managerId, plan)
                            .then(cancelPendingOrders(managerId))
                            .then(amount.signum() == 0
                                    ? activateFreePlan(managerId, plan)
                                    : createPendingOrder(managerId, plan, billingCycle, amount));
                });
    }

    /**
     * Confirmation d'un paiement réussi (webhook du fournisseur ou simulateur). Idempotente :
     * une commande déjà payée est renvoyée telle quelle, sans prolonger une seconde fois.
     */
    @Transactional
    public Mono<SubscriptionOrderEntity> confirmPayment(UUID orderId) {
        return orderRepo.findByIdForUpdate(orderId)
                .switchIfEmpty(Mono.error(SubscriptionException.orderNotFound()))
                .flatMap(order -> {
                    if (PAID.equals(order.getStatus())) {
                        return Mono.just(order);
                    }
                    if (!PENDING.equals(order.getStatus())) {
                        return Mono.error(SubscriptionException.orderNotPayable(order.getStatus()));
                    }
                    return planRepo.findById(order.getPlanId())
                            .switchIfEmpty(Mono.error(SubscriptionException.planNotFound()))
                            .flatMap(plan -> planService.assertUsageFits(order.getCompanyId(), plan)
                                    .then(applyPaidPeriod(order, plan)));
                });
    }

    /** Confirmation par référence de paiement (forme attendue d'un webhook fournisseur). */
    public Mono<SubscriptionOrderEntity> confirmPaymentByReference(String reference) {
        return orderRepo.findByPaymentReference(reference)
                .switchIfEmpty(Mono.error(SubscriptionException.orderNotFound()))
                .flatMap(order -> confirmPayment(order.getId()));
    }

    /** Bouton « Simuler le paiement » : réservé au simulateur, jamais au service réel. */
    public Mono<SubscriptionOrderEntity> simulatePayment(UUID managerId, UUID orderId) {
        if (!paymentPort.isSimulation()) {
            return Mono.error(SubscriptionException.paymentSimulationDisabled());
        }
        return ownedOrder(managerId, orderId).flatMap(order -> confirmPayment(order.getId()));
    }

    public Mono<SubscriptionOrderEntity> cancelOrder(UUID managerId, UUID orderId) {
        return ownedOrder(managerId, orderId).flatMap(order -> {
            if (!PENDING.equals(order.getStatus())) {
                return Mono.error(SubscriptionException.orderNotPayable(order.getStatus()));
            }
            order.setStatus(CANCELLED);
            order.setUpdatedAt(LocalDateTime.now());
            order.setNew(false);
            return orderRepo.save(order);
        });
    }

    public Flux<SubscriptionOrderEntity> listOrders(UUID managerId) {
        return orderRepo.findByCompanyId(managerId);
    }

    public Flux<SubscriptionOrderEntity> listAllOrders() {
        return orderRepo.findAllOrderByCreatedAtDesc();
    }

    public boolean isSimulation() {
        return paymentPort.isSimulation();
    }

    // ── Interne ──────────────────────────────────────────────────────────────

    private Mono<SubscriptionOrderEntity> ownedOrder(UUID managerId, UUID orderId) {
        return orderRepo.findById(orderId)
                .filter(o -> managerId.equals(o.getCompanyId()))
                .switchIfEmpty(Mono.error(SubscriptionException.orderNotFound()));
    }

    /** Une seule commande en attente à la fois : la nouvelle remplace les précédentes. */
    private Mono<Void> cancelPendingOrders(UUID managerId) {
        return orderRepo.findByCompanyId(managerId)
                .filter(o -> PENDING.equals(o.getStatus()))
                .flatMap(o -> {
                    o.setStatus(CANCELLED);
                    o.setUpdatedAt(LocalDateTime.now());
                    o.setNew(false);
                    return orderRepo.save(o);
                })
                .then();
    }

    private Mono<CheckoutResult> activateFreePlan(UUID managerId, SubscriptionPlanEntity plan) {
        LocalDate today = LocalDate.now();
        SubscriptionOrderEntity order = newOrder(managerId, plan, null, BigDecimal.ZERO);
        order.setStatus(PAID);
        order.setPaymentProvider("NONE");
        order.setPaidAt(LocalDateTime.now());
        order.setPeriodStart(today);
        return planService.activateSubscription(managerId, plan, BillingCycle.MONTHLY, today)
                .then(orderRepo.save(order))
                .map(saved -> new CheckoutResult(saved, null, false));
    }

    private Mono<CheckoutResult> createPendingOrder(UUID managerId, SubscriptionPlanEntity plan,
                                                    BillingCycle cycle, BigDecimal amount) {
        SubscriptionOrderEntity order = newOrder(managerId, plan, cycle, amount);
        order.setPaymentProvider(paymentPort.providerName());
        return orderRepo.save(order)
                .flatMap(saved -> paymentPort.initiate(new SubscriptionPaymentPort.PaymentRequest(
                                saved.getId(), managerId, amount, saved.getCurrency(),
                                "Abonnement FleetMan " + plan.getName() + " ("
                                        + (cycle == BillingCycle.MONTHLY ? "mensuel" : "annuel") + ")"))
                        .flatMap(session -> {
                            saved.setPaymentReference(session.reference());
                            saved.setUpdatedAt(LocalDateTime.now());
                            saved.setNew(false);
                            return orderRepo.save(saved).map(o -> new CheckoutResult(
                                    o, session.instructions(), paymentPort.isSimulation()));
                        }));
    }

    private SubscriptionOrderEntity newOrder(UUID managerId, SubscriptionPlanEntity plan,
                                             BillingCycle cycle, BigDecimal amount) {
        SubscriptionOrderEntity order = new SubscriptionOrderEntity();
        order.setId(UUID.randomUUID());
        order.setCompanyId(managerId);
        order.setPlanId(plan.getId());
        order.setPlanName(plan.getName());
        order.setBillingCycle(cycle != null ? cycle.name() : null);
        order.setAmount(amount);
        order.setCurrency(plan.getCurrency() != null ? plan.getCurrency() : "XAF");
        order.setStatus(PENDING);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        order.setNew(true);
        return order;
    }

    /** Prolonge (même plan encore actif) ou démarre la période payée, puis marque la commande payée. */
    private Mono<SubscriptionOrderEntity> applyPaidPeriod(SubscriptionOrderEntity order, SubscriptionPlanEntity plan) {
        BillingCycle cycle = BillingCycle.parseOrDefault(order.getBillingCycle(), BillingCycle.MONTHLY);
        LocalDate today = LocalDate.now();
        return currentSubscription(order.getCompanyId())
                .flatMap(current -> {
                    boolean extend = plan.getId().equals(current.planId())
                            && "ACTIVE".equals(current.status())
                            && current.end() != null && !current.end().isBefore(today);
                    if (extend) {
                        LocalDate newEnd = cycle.periodEnd(current.end());
                        order.setPeriodStart(current.end());
                        order.setPeriodEnd(newEnd);
                        return db.sql("UPDATE fleet.companies SET subscription_end = :end, billing_cycle = :cycle, updated_at = NOW() WHERE id = :id")
                                .bind("end", newEnd).bind("cycle", cycle.name()).bind("id", order.getCompanyId())
                                .fetch().rowsUpdated().then();
                    }
                    order.setPeriodStart(today);
                    return planService.activateSubscription(order.getCompanyId(), plan, cycle, today)
                            .doOnNext(order::setPeriodEnd)
                            .then();
                })
                .then(Mono.defer(() -> {
                    order.setStatus(PAID);
                    order.setPaidAt(LocalDateTime.now());
                    order.setUpdatedAt(LocalDateTime.now());
                    order.setNew(false);
                    log.info("✅ Abonnement {} payé pour {} (période {} → {})",
                            plan.getName(), order.getCompanyId(), order.getPeriodStart(), order.getPeriodEnd());
                    return orderRepo.save(order);
                }));
    }

    private record CurrentSubscription(UUID planId, String status, LocalDate end) {}

    private Mono<CurrentSubscription> currentSubscription(UUID managerId) {
        return db.sql("SELECT plan_id, subscription_status, subscription_end FROM fleet.companies WHERE id = :id")
                .bind("id", managerId)
                .map((row, meta) -> new CurrentSubscription(
                        row.get("plan_id", UUID.class),
                        row.get("subscription_status", String.class),
                        row.get("subscription_end", LocalDate.class)))
                .one()
                .defaultIfEmpty(new CurrentSubscription(null, null, null));
    }
}
