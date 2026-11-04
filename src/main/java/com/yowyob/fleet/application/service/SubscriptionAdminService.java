package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.SubscriptionException;
import com.yowyob.fleet.domain.model.BillingCycle;
import com.yowyob.fleet.domain.model.SubscriptionPlanRules;
import com.yowyob.fleet.domain.model.SubscriptionState;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.SubscriptionAdminDtos;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.SubscriptionPlanR2dbcRepository;
import io.r2dbc.spi.Row;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Pilotage des abonnements par le super-administrateur : portefeuille, revenus, actions tracées. */
@Service
@RequiredArgsConstructor
public class SubscriptionAdminService {

    private static final Set<String> PAYMENT_METHODS = Set.of("CASH", "BANK_TRANSFER", "MOBILE_MONEY", "CHEQUE", "OTHER");
    private static final List<Integer> REMINDER_DAYS = List.of(30, 15, 7, 1);

    private static final String COMPANY_SELECT = """
            SELECT c.id, c.name, c.city, c.plan_id, c.subscription_status, c.subscription_start, c.subscription_end,
                   c.billing_cycle, c.suspension_reason, c.suspended_at, c.created_at,
                   c.legal_name, c.legal_form, c.registration_number, c.tax_id, c.sector, c.address, c.contact_phone, c.contact_email,
                   sp.name AS plan_name, sp.monthly_price, sp.annual_price,
                   COALESCE(sp.max_fleets, 0) AS max_fleets, COALESCE(sp.max_vehicles, 0) AS max_vehicles, COALESCE(sp.max_drivers, 0) AS max_drivers,
                   COALESCE((SELECT setting_value::int FROM fleet.app_settings WHERE setting_key = 'subscription_grace_days'),
                            sp.grace_days, 7) AS grace_days,
                   NULLIF(TRIM(COALESCE(u.first_name, '') || ' ' || COALESCE(u.last_name, '')), '') AS admin_name,
                   u.username, u.email, u.phone,
                   (SELECT COUNT(*) FROM fleet.fleets f WHERE f.company_id = c.id AND f.deleted_at IS NULL) AS fleets,
                   (SELECT COUNT(*) FROM fleet.vehicles v JOIN fleet.fleets f ON f.id = v.fleet_id
                    WHERE f.company_id = c.id AND v.deleted_at IS NULL AND v.status <> 'RETIRED') AS vehicles,
                   (SELECT COUNT(*) FROM fleet.drivers d JOIN fleet.fleets f ON f.id = d.fleet_id
                    WHERE f.company_id = c.id AND d.deleted_at IS NULL AND d.status <> 'LEFT') AS drivers,
                   (SELECT COUNT(*) FROM fleet.fleet_managers fm WHERE fm.created_by_admin_id = c.id AND fm.account_status <> 'CLOSED') AS managers,
                   (SELECT MAX(o.paid_at) FROM fleet.subscription_orders o WHERE o.company_id = c.id AND o.status = 'PAID' AND o.amount > 0) AS last_payment_at,
                   (SELECT COUNT(*) FROM fleet.subscription_orders o WHERE o.company_id = c.id AND o.status = 'PENDING') AS pending_orders
            FROM fleet.companies c
            JOIN fleet.users u ON u.id = c.id
            LEFT JOIN fleet.subscription_plans sp ON sp.id = c.plan_id
            WHERE u.deleted_at IS NULL AND COALESCE(u.approval_status, '') <> 'REJECTED'
            """;

    private final DatabaseClient db;
    private final SubscriptionPlanR2dbcRepository planRepo;
    private final SubscriptionPlanService planService;
    private final SubscriptionCheckoutService checkout;
    private final SubscriptionJournal journal;

    public Flux<SubscriptionAdminDtos.CompanyRow> companies() {
        return db.sql(COMPANY_SELECT + " ORDER BY LOWER(c.name)")
                .map((row, meta) -> toRow(row))
                .all();
    }

    public Mono<SubscriptionAdminDtos.Overview> overview() {
        Mono<BigDecimal[]> collected = db.sql("""
                SELECT COALESCE(SUM(amount) FILTER (WHERE paid_at >= DATE_TRUNC('month', CURRENT_DATE)), 0) AS this_month,
                       COALESCE(SUM(amount), 0) AS total
                FROM fleet.subscription_orders WHERE status = 'PAID'
                """)
                .map((row, meta) -> new BigDecimal[] {row.get("this_month", BigDecimal.class), row.get("total", BigDecimal.class)})
                .one();
        Mono<Object[]> pending = db.sql("""
                SELECT COUNT(*) AS n, COALESCE(SUM(amount), 0) AS total FROM fleet.subscription_orders WHERE status = 'PENDING'
                """)
                .map((row, meta) -> new Object[] {row.get("n", Long.class), row.get("total", BigDecimal.class)})
                .one();
        Mono<List<SubscriptionAdminDtos.MonthRevenue>> revenue = db.sql("""
                SELECT TO_CHAR(m, 'YYYY-MM') AS month,
                       (SELECT COALESCE(SUM(o.amount), 0) FROM fleet.subscription_orders o
                        WHERE o.status = 'PAID' AND o.paid_at >= m AND o.paid_at < m + INTERVAL '1 month') AS total
                FROM GENERATE_SERIES(DATE_TRUNC('month', CURRENT_DATE) - INTERVAL '11 months', DATE_TRUNC('month', CURRENT_DATE), INTERVAL '1 month') AS m
                ORDER BY m
                """)
                .map((row, meta) -> new SubscriptionAdminDtos.MonthRevenue(row.get("month", String.class), row.get("total", BigDecimal.class)))
                .all()
                .collectList();
        return Mono.zip(companies().collectList(), collected, pending, revenue).map(t -> {
            List<SubscriptionAdminDtos.CompanyRow> rows = t.getT1();
            Map<String, Long> states = new LinkedHashMap<>();
            Map<String, long[]> planCounts = new LinkedHashMap<>();
            Map<String, BigDecimal> planRevenue = new LinkedHashMap<>();
            BigDecimal mrr = BigDecimal.ZERO;
            long paying = 0;
            long free = 0;
            for (SubscriptionAdminDtos.CompanyRow row : rows) {
                states.merge(row.state(), 1L, Long::sum);
                SubscriptionState.State state = SubscriptionState.State.valueOf(row.state());
                if (SubscriptionState.isPaying(state)) {
                    paying++;
                    mrr = mrr.add(row.monthlyRevenue());
                }
                if (state == SubscriptionState.State.FREE) {
                    free++;
                }
                if (row.planName() != null && state != SubscriptionState.State.PENDING) {
                    planCounts.computeIfAbsent(row.planName(), k -> new long[1])[0]++;
                    planRevenue.merge(row.planName(), row.monthlyRevenue(), BigDecimal::add);
                }
            }
            List<SubscriptionAdminDtos.StateCount> stateList = new ArrayList<>();
            states.forEach((k, v) -> stateList.add(new SubscriptionAdminDtos.StateCount(k, v)));
            List<SubscriptionAdminDtos.PlanShare> planList = new ArrayList<>();
            planCounts.forEach((k, v) -> planList.add(new SubscriptionAdminDtos.PlanShare(k, v[0], planRevenue.get(k))));
            planList.sort(Comparator.comparing(SubscriptionAdminDtos.PlanShare::companies).reversed());
            List<SubscriptionAdminDtos.CompanyRow> expiring = rows.stream()
                    .filter(r -> "EXPIRING".equals(r.state()) || "GRACE".equals(r.state()))
                    .sorted(Comparator.comparing(SubscriptionAdminDtos.CompanyRow::subscriptionEnd))
                    .limit(8)
                    .toList();
            return new SubscriptionAdminDtos.Overview(
                    rows.size(), paying, free, states.getOrDefault("PENDING", 0L),
                    mrr.setScale(0, RoundingMode.HALF_UP),
                    mrr.multiply(BigDecimal.valueOf(12)).setScale(0, RoundingMode.HALF_UP),
                    t.getT2()[0], t.getT2()[1], (Long) t.getT3()[0], (BigDecimal) t.getT3()[1],
                    stateList, planList, t.getT4(), expiring, checkout.isSimulation());
        });
    }

    public Mono<SubscriptionAdminDtos.CompanyDetail> company(UUID id) {
        Mono<SubscriptionAdminDtos.CompanyDetail> head = db.sql(COMPANY_SELECT + " AND c.id = :id")
                .bind("id", id)
                .map((row, meta) -> new SubscriptionAdminDtos.CompanyDetail(
                        toRow(row),
                        row.get("legal_name", String.class), row.get("legal_form", String.class),
                        row.get("registration_number", String.class), row.get("tax_id", String.class),
                        row.get("sector", String.class), row.get("address", String.class),
                        row.get("contact_phone", String.class), row.get("contact_email", String.class),
                        List.of(), List.of()))
                .one()
                .switchIfEmpty(Mono.error(SubscriptionException.companyNotFound()));
        Mono<List<SubscriptionAdminDtos.OrderRow>> orderList = orders(id).collectList();
        Mono<List<SubscriptionAdminDtos.EventRow>> events = db.sql("""
                SELECT e.id, e.event_type, e.plan_name, e.period_end, e.amount, e.reference, e.reason, e.created_at,
                       NULLIF(TRIM(COALESCE(u.first_name, '') || ' ' || COALESCE(u.last_name, '')), '') AS actor_name
                FROM fleet.subscription_events e LEFT JOIN fleet.users u ON u.id = e.actor_id
                WHERE e.company_id = :id ORDER BY e.created_at DESC LIMIT 100
                """)
                .bind("id", id)
                .map((row, meta) -> new SubscriptionAdminDtos.EventRow(
                        row.get("id", UUID.class), row.get("event_type", String.class), row.get("actor_name", String.class),
                        row.get("plan_name", String.class), row.get("period_end", LocalDate.class), row.get("amount", BigDecimal.class),
                        row.get("reference", String.class), row.get("reason", String.class), row.get("created_at", LocalDateTime.class)))
                .all()
                .collectList();
        return Mono.zip(head, orderList, events).map(t -> {
            SubscriptionAdminDtos.CompanyDetail d = t.getT1();
            return new SubscriptionAdminDtos.CompanyDetail(d.company(), d.legalName(), d.legalForm(), d.registrationNumber(),
                    d.taxId(), d.sector(), d.address(), d.companyPhone(), d.companyEmail(), t.getT2(), t.getT3());
        });
    }

    /** Toutes les commandes, ou celles d'une entreprise. */
    public Flux<SubscriptionAdminDtos.OrderRow> orders(UUID companyId) {
        String sql = """
                SELECT o.id, o.company_id, c.name AS company_name, o.plan_name, o.billing_cycle, o.amount, o.currency, o.status,
                       o.payment_provider, o.payment_method, o.payment_reference, o.payment_note, o.period_start, o.period_end,
                       o.created_at, o.paid_at
                FROM fleet.subscription_orders o LEFT JOIN fleet.companies c ON c.id = o.company_id
                """ + (companyId != null ? " WHERE o.company_id = :id" : "") + " ORDER BY o.created_at DESC LIMIT 500";
        DatabaseClient.GenericExecuteSpec spec = db.sql(sql);
        if (companyId != null) {
            spec = spec.bind("id", companyId);
        }
        return spec.map((row, meta) -> new SubscriptionAdminDtos.OrderRow(
                row.get("id", UUID.class), row.get("company_id", UUID.class), row.get("company_name", String.class),
                row.get("plan_name", String.class), row.get("billing_cycle", String.class), row.get("amount", BigDecimal.class),
                row.get("currency", String.class), row.get("status", String.class), row.get("payment_provider", String.class),
                row.get("payment_method", String.class), row.get("payment_reference", String.class), row.get("payment_note", String.class),
                row.get("period_start", LocalDate.class), row.get("period_end", LocalDate.class),
                row.get("created_at", LocalDateTime.class), row.get("paid_at", LocalDateTime.class)))
                .all();
    }

    /** Attribue un plan sans passer par le paiement : la période repart d'aujourd'hui. */
    @Transactional
    public Mono<SubscriptionAdminDtos.CompanyDetail> changePlan(UUID companyId, UUID actorId, SubscriptionAdminDtos.ChangePlanRequest request) {
        String reason = requireReason(request.reason());
        BillingCycle cycle = BillingCycle.parseOrDefault(request.billingCycle(), BillingCycle.MONTHLY);
        return requireManaged(companyId)
                .then(planRepo.findById(request.planId()))
                .switchIfEmpty(Mono.error(SubscriptionException.planNotFound()))
                .flatMap(plan -> {
                    if (!plan.isActive()) {
                        return Mono.error(SubscriptionException.planInactive(plan.getName()));
                    }
                    if (SubscriptionPlanRules.priceFor(plan.getMonthlyPrice(), plan.getAnnualPrice(), cycle) == null) {
                        return Mono.error(SubscriptionException.billingCycleUnavailable(plan.getName()));
                    }
                    return planService.assertUsageFits(companyId, plan)
                            .then(cancelOpenOrders(companyId))
                            .then(planService.activateSubscription(companyId, plan, cycle, LocalDate.now())
                                    .map(java.util.Optional::of)
                                    .defaultIfEmpty(java.util.Optional.empty()))
                            .flatMap(end -> clearSuspension(companyId)
                                    .then(journal.log(new SubscriptionJournal.Entry(companyId, "PLAN_CHANGED", actorId, plan.getName(),
                                            end.orElse(null), null, null, reason)))
                                    .then(journal.notifyAdmin(companyId, "Votre abonnement est passé au plan " + plan.getName()
                                            + end.map(e -> " jusqu'au " + e).orElse("") + ".")));
                })
                .then(Mono.defer(() -> company(companyId)));
    }

    @Transactional
    public Mono<SubscriptionAdminDtos.CompanyDetail> extend(UUID companyId, UUID actorId, SubscriptionAdminDtos.ExtendRequest request) {
        String reason = requireReason(request.reason());
        if (request.days() < 1 || request.days() > SubscriptionState.MAX_EXTENSION_DAYS) {
            return Mono.error(SubscriptionException.invalidExtension(SubscriptionState.MAX_EXTENSION_DAYS));
        }
        return subscription(companyId).flatMap(sub -> {
            if ("PENDING".equals(sub.status())) {
                return Mono.error(SubscriptionException.requestStillPending());
            }
            if (sub.end() == null) {
                return Mono.error(SubscriptionException.freePlanHasNoDeadline());
            }
            LocalDate newEnd = SubscriptionState.extendedEnd(sub.end(), LocalDate.now(), request.days());
            // Une suspension reste en place : la prolongation ne rend pas l'accès.
            String status = "EXPIRED".equals(sub.status()) ? "ACTIVE" : sub.status();
            return db.sql("UPDATE fleet.companies SET subscription_end = :end, subscription_status = :status, updated_at = NOW() WHERE id = :id")
                    .bind("end", newEnd).bind("status", status).bind("id", companyId)
                    .fetch().rowsUpdated()
                    .then(journal.log(new SubscriptionJournal.Entry(companyId, "PERIOD_EXTENDED", actorId, sub.planName(), newEnd, null, null, reason)))
                    .then(journal.notifyAdmin(companyId, "Votre abonnement a été prolongé jusqu'au " + newEnd + "."));
        }).then(Mono.defer(() -> company(companyId)));
    }

    @Transactional
    public Mono<SubscriptionAdminDtos.CompanyDetail> suspend(UUID companyId, UUID actorId, String rawReason) {
        String reason = requireReason(rawReason);
        return subscription(companyId).flatMap(sub -> {
            if ("PENDING".equals(sub.status())) {
                return Mono.error(SubscriptionException.requestStillPending());
            }
            if ("SUSPENDED".equals(sub.status())) {
                return Mono.error(SubscriptionException.alreadySuspended());
            }
            return db.sql("""
                    UPDATE fleet.companies SET subscription_status = 'SUSPENDED', suspension_reason = :reason,
                        suspended_at = NOW(), updated_at = NOW() WHERE id = :id
                    """)
                    .bind("reason", reason).bind("id", companyId)
                    .fetch().rowsUpdated()
                    .then(journal.log(new SubscriptionJournal.Entry(companyId, "SUSPENDED", actorId, sub.planName(), sub.end(), null, null, reason)))
                    .then(journal.notifyAdmin(companyId, "Votre abonnement a été suspendu : " + reason));
        }).then(Mono.defer(() -> company(companyId)));
    }

    @Transactional
    public Mono<SubscriptionAdminDtos.CompanyDetail> reactivate(UUID companyId, UUID actorId, String rawReason) {
        String reason = requireReason(rawReason);
        return subscription(companyId).flatMap(sub -> {
            if (!"SUSPENDED".equals(sub.status())) {
                return Mono.error(SubscriptionException.notSuspended());
            }
            String status = SubscriptionState.statusAfterReactivation(sub.end(), LocalDate.now(), sub.graceDays());
            return db.sql("""
                    UPDATE fleet.companies SET subscription_status = :status, suspension_reason = NULL,
                        suspended_at = NULL, updated_at = NOW() WHERE id = :id
                    """)
                    .bind("status", status).bind("id", companyId)
                    .fetch().rowsUpdated()
                    .then(journal.log(new SubscriptionJournal.Entry(companyId, "REACTIVATED", actorId, sub.planName(), sub.end(), null, null, reason)))
                    .then(journal.notifyAdmin(companyId, "ACTIVE".equals(status)
                            ? "Votre abonnement a été réactivé."
                            : "La suspension est levée, mais votre abonnement est expiré : renouvelez votre plan."));
        }).then(Mono.defer(() -> company(companyId)));
    }

    /** Relance manuelle de l'administrateur de l'entreprise. */
    public Mono<SubscriptionAdminDtos.CompanyDetail> remind(UUID companyId, UUID actorId) {
        return subscription(companyId).flatMap(sub -> {
            if (sub.end() == null) {
                return Mono.error(SubscriptionException.freePlanHasNoDeadline());
            }
            return journal.log(new SubscriptionJournal.Entry(companyId, "REMINDER", actorId, sub.planName(), sub.end(), null, null, null))
                    .then(journal.notifyAdmin(companyId, reminderText(sub.planName(), sub.end())));
        }).then(Mono.defer(() -> company(companyId)));
    }

    /** Rappels automatiques à J-30, J-15, J-7 et J-1, une seule fois par échéance et par palier. */
    public Mono<Long> sendDueReminders() {
        LocalDate today = LocalDate.now();
        return Flux.fromIterable(REMINDER_DAYS)
                .concatMap(days -> db.sql("""
                        SELECT c.id, c.subscription_end, sp.name AS plan_name
                        FROM fleet.companies c LEFT JOIN fleet.subscription_plans sp ON sp.id = c.plan_id
                        WHERE c.subscription_status = 'ACTIVE' AND c.subscription_end = :end
                          AND NOT EXISTS (SELECT 1 FROM fleet.subscription_events e
                                          WHERE e.company_id = c.id AND e.event_type = :type AND e.period_end = c.subscription_end)
                        """)
                        .bind("end", today.plusDays(days))
                        .bind("type", "REMINDER_J" + days)
                        .map((row, meta) -> new DueReminder(row.get("id", UUID.class), row.get("subscription_end", LocalDate.class),
                                row.get("plan_name", String.class), days))
                        .all())
                .concatMap(due -> journal.log(new SubscriptionJournal.Entry(due.companyId(), "REMINDER_J" + due.days(), null,
                                due.planName(), due.end(), null, null, null))
                        .then(journal.notifyAdmin(due.companyId(), reminderText(due.planName(), due.end())))
                        .thenReturn(1L))
                .count();
    }

    public Mono<SubscriptionAdminDtos.OrderRow> recordPayment(UUID orderId, UUID actorId, SubscriptionAdminDtos.RecordPaymentRequest request) {
        if (request.method() == null || !PAYMENT_METHODS.contains(request.method())) {
            return Mono.error(SubscriptionException.invalidPaymentMethod());
        }
        String reference = request.reference() == null || request.reference().isBlank() ? null : request.reference().trim();
        if (reference == null && !"CASH".equals(request.method())) {
            return Mono.error(SubscriptionException.paymentReferenceRequired());
        }
        String note = request.note() == null || request.note().isBlank() ? null : request.note().trim();
        return checkout.recordManualPayment(orderId, actorId, request.method(), reference, note)
                .flatMap(order -> journal.notifyAdmin(order.getCompanyId(), "Votre règlement a été enregistré : abonnement "
                                + order.getPlanName() + (order.getPeriodEnd() != null ? " jusqu'au " + order.getPeriodEnd() : "") + ".")
                        .then(orderRow(orderId)));
    }

    public Mono<SubscriptionAdminDtos.OrderRow> cancelOrder(UUID orderId, UUID actorId, String rawReason) {
        String reason = requireReason(rawReason);
        return checkout.cancelOrderAsAdmin(orderId, actorId, reason).then(Mono.defer(() -> orderRow(orderId)));
    }

    private record DueReminder(UUID companyId, LocalDate end, String planName, int days) {}

    private record Subscription(String status, LocalDate end, String planName, int graceDays) {}

    private Mono<SubscriptionAdminDtos.OrderRow> orderRow(UUID orderId) {
        return orders(null).filter(o -> orderId.equals(o.id())).next();
    }

    private static String reminderText(String planName, LocalDate end) {
        return "Votre abonnement " + (planName != null ? planName + " " : "") + "arrive à échéance le " + end + " : pensez à le renouveler.";
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw SubscriptionException.reasonRequired();
        }
        return reason.trim();
    }

    private Mono<Subscription> subscription(UUID companyId) {
        return db.sql("""
                SELECT c.subscription_status, c.subscription_end, sp.name AS plan_name,
                       COALESCE((SELECT setting_value::int FROM fleet.app_settings WHERE setting_key = 'subscription_grace_days'),
                                sp.grace_days, 7) AS grace_days
                FROM fleet.companies c LEFT JOIN fleet.subscription_plans sp ON sp.id = c.plan_id
                WHERE c.id = :id
                """)
                .bind("id", companyId)
                .map((row, meta) -> new Subscription(row.get("subscription_status", String.class), row.get("subscription_end", LocalDate.class),
                        row.get("plan_name", String.class), row.get("grace_days", Integer.class)))
                .one()
                .switchIfEmpty(Mono.error(SubscriptionException.companyNotFound()));
    }

    private Mono<Void> requireManaged(UUID companyId) {
        return subscription(companyId).flatMap(sub -> "PENDING".equals(sub.status())
                ? Mono.<Void>error(SubscriptionException.requestStillPending())
                : Mono.<Void>empty());
    }

    private Mono<Void> clearSuspension(UUID companyId) {
        return db.sql("UPDATE fleet.companies SET suspension_reason = NULL, suspended_at = NULL WHERE id = :id")
                .bind("id", companyId)
                .fetch().rowsUpdated().then();
    }

    private Mono<Void> cancelOpenOrders(UUID companyId) {
        return db.sql("UPDATE fleet.subscription_orders SET status = 'CANCELLED', updated_at = NOW() WHERE company_id = :id AND status = 'PENDING'")
                .bind("id", companyId)
                .fetch().rowsUpdated().then();
    }

    private static SubscriptionAdminDtos.CompanyRow toRow(Row row) {
        String status = row.get("subscription_status", String.class);
        String cycle = row.get("billing_cycle", String.class);
        LocalDate end = row.get("subscription_end", LocalDate.class);
        BigDecimal monthly = row.get("monthly_price", BigDecimal.class);
        BigDecimal annual = row.get("annual_price", BigDecimal.class);
        int grace = row.get("grace_days", Integer.class);
        boolean free = monthly == null || monthly.signum() == 0;
        LocalDate today = LocalDate.now();
        SubscriptionState.State state = SubscriptionState.of(status, free, end, today, grace);
        BigDecimal revenue = free ? BigDecimal.ZERO : SubscriptionState.monthlyEquivalent(monthly, annual, cycle);
        return new SubscriptionAdminDtos.CompanyRow(
                row.get("id", UUID.class), row.get("name", String.class), row.get("admin_name", String.class),
                row.get("username", String.class), row.get("email", String.class), row.get("phone", String.class),
                row.get("city", String.class), row.get("plan_id", UUID.class), row.get("plan_name", String.class),
                monthly, annual, cycle, status, state.name(),
                row.get("subscription_start", LocalDate.class), end,
                end != null ? ChronoUnit.DAYS.between(today, end) : null,
                grace, revenue,
                row.get("max_fleets", Integer.class), row.get("max_vehicles", Integer.class), row.get("max_drivers", Integer.class),
                row.get("fleets", Long.class), row.get("vehicles", Long.class), row.get("drivers", Long.class), row.get("managers", Long.class),
                row.get("last_payment_at", LocalDateTime.class), row.get("pending_orders", Long.class),
                row.get("suspension_reason", String.class), row.get("suspended_at", LocalDateTime.class),
                row.get("created_at", LocalDateTime.class));
    }
}
