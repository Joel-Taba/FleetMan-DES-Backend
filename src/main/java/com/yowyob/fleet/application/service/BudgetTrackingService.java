package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.BudgetException;
import com.yowyob.fleet.domain.model.BudgetTracking;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/** Suivi des budgets : vue consolidée d'un mois et grand livre d'un budget. */
@Service
@RequiredArgsConstructor
public class BudgetTrackingService {

    private static final int TREND_MONTHS = 6;
    private static final String SCOPE_FLEETS = "SELECT id FROM fleet.fleets WHERE deleted_at IS NULL AND (manager_id = :a OR company_id = :a)";
    private static final Map<String, String> TYPE_LABELS = Map.of(
            "FUEL", "Carburant", "MAINTENANCE", "Maintenance", "INCIDENT", "Incident", "TOLL", "Péage",
            "FINE", "Amende", "PARKING", "Stationnement", "INSURANCE", "Assurance", "OTHER", "Autre");

    private final DatabaseClient db;
    private final AccessScopeService accessScope;
    private final ResourceAccessService resourceAccess;

    public record LedgerEntry(LocalDateTime at, String kind, BigDecimal amount, BigDecimal oldAmount, BigDecimal budgetAfter,
                              BigDecimal consumedAfter, BigDecimal remainingAfter, String expenseType, String description,
                              String vehiclePlate, String driverName, String source, UUID sourceId, UUID expenseId,
                              String submittedBy, String validatedBy, LocalDateTime validatedAt, String receiptUrl,
                              boolean overBudget, String actor, String note) {
    }

    public record OpenExpense(UUID id, LocalDateTime date, String type, BigDecimal amount, String status, String description,
                              String vehiclePlate, String driverName, String approvalLevel, String rejectionReason,
                              String submittedBy) {
    }

    public record Ledger(UUID budgetId, String scope, UUID entityId, String entityName, LocalDate month, BigDecimal amount,
                         BigDecimal consumed, BigDecimal remaining, String alertLevel, List<LedgerEntry> entries,
                         List<OpenExpense> pending, List<OpenExpense> rejected) {
    }

    // ── Vue consolidée ───────────────────────────────────────────────────────

    public Mono<BudgetTracking.Overview> overview(UUID actorId, LocalDate requestedMonth, UUID fleetId) {
        LocalDate month = (requestedMonth != null ? requestedMonth : LocalDate.now()).withDayOfMonth(1);
        return accessScope.resolveLocalUserId(actorId).flatMap(actor -> {
            String only = fleetId != null ? " AND id = :f" : "";
            Mono<List<BudgetTracking.FleetRow>> fleets = bindScope(db.sql("""
                    SELECT f.id, f.name, NULLIF(TRIM(COALESCE(u.first_name, '') || ' ' || COALESCE(u.last_name, '')), '') AS manager
                    FROM fleet.fleets f LEFT JOIN fleet.users u ON u.id = f.manager_id
                    WHERE f.id IN (""" + SCOPE_FLEETS + only + ")"), actor, fleetId)
                    .map(row -> new BudgetTracking.FleetRow(row.get("id", UUID.class), row.get("name", String.class),
                            row.get("manager", String.class)))
                    .all().collectList();
            Mono<List<BudgetTracking.VehicleRow>> vehicles = bindScope(db.sql("""
                    SELECT id, fleet_id, license_plate FROM fleet.vehicles
                    WHERE deleted_at IS NULL AND status <> 'RETIRED' AND fleet_id IN (""" + SCOPE_FLEETS + only + ")"), actor, fleetId)
                    .map(row -> new BudgetTracking.VehicleRow(row.get("id", UUID.class), row.get("fleet_id", UUID.class),
                            row.get("license_plate", String.class)))
                    .all().collectList();
            Mono<List<BudgetTracking.BudgetRow>> budgets = bindScope(db.sql("""
                    SELECT b.id, b.scope, b.entity_id, b.amount, b.alert_level FROM fleet.budgets b
                    LEFT JOIN fleet.vehicles v ON b.scope = 'VEHICLE' AND v.id = b.entity_id
                    WHERE b.budget_month = :m
                      AND (CASE WHEN b.scope = 'FLEET' THEN b.entity_id ELSE v.fleet_id END) IN (""" + SCOPE_FLEETS + only + ")"), actor, fleetId)
                    .bind("m", month)
                    .map(row -> new BudgetTracking.BudgetRow(row.get("id", UUID.class), row.get("scope", String.class),
                            row.get("entity_id", UUID.class), row.get("amount", BigDecimal.class), row.get("alert_level", String.class)))
                    .all().collectList();
            Mono<List<BudgetTracking.ExpenseRow>> expenses = bindScope(db.sql("""
                    SELECT e.id, e.fleet_id, e.vehicle_id, COALESCE(e.vehicle_registration, v.license_plate) AS plate, e.driver_id,
                           COALESCE(NULLIF(TRIM(COALESCE(u.first_name, '') || ' ' || COALESCE(u.last_name, '')), ''), e.driver_full_name) AS driver,
                           e.expense_type, e.status, e.source_type, e.amount, e.expense_date, e.description, e.over_budget
                    FROM fleet.expenses e
                    LEFT JOIN fleet.vehicles v ON v.id = e.vehicle_id
                    LEFT JOIN fleet.users u ON u.id = e.driver_id
                    WHERE e.expense_date >= :from AND e.expense_date < :to
                      AND e.fleet_id IN (""" + SCOPE_FLEETS + only + ")"), actor, fleetId)
                    .bind("from", month.atStartOfDay())
                    .bind("to", month.plusMonths(1).atStartOfDay())
                    .map(row -> new BudgetTracking.ExpenseRow(row.get("id", UUID.class), row.get("fleet_id", UUID.class),
                            row.get("vehicle_id", UUID.class), row.get("plate", String.class), row.get("driver_id", UUID.class),
                            row.get("driver", String.class), row.get("expense_type", String.class), row.get("status", String.class),
                            row.get("source_type", String.class), row.get("amount", BigDecimal.class),
                            row.get("expense_date", LocalDateTime.class), row.get("description", String.class),
                            Boolean.TRUE.equals(row.get("over_budget", Boolean.class))))
                    .all().collectList();
            // Km des trajets terminés du mois, à rapprocher des dépenses (coût au kilomètre).
            Mono<Map<UUID, BigDecimal>> km = bindScope(db.sql("""
                    SELECT t.vehicle_id, SUM(COALESCE(t.computed_distance_km, CAST(t.distance_km AS numeric), 0)) AS km
                    FROM fleet.trips t
                    WHERE t.status = 'COMPLETED' AND t.deleted_at IS NULL AND t.end_date >= :from AND t.end_date < :to
                      AND t.fleet_id IN (""" + SCOPE_FLEETS + only + ") GROUP BY t.vehicle_id"), actor, fleetId)
                    .bind("from", month)
                    .bind("to", month.plusMonths(1))
                    .map(row -> Map.entry(row.get("vehicle_id", UUID.class), row.get("km", BigDecimal.class)))
                    .all()
                    .collectMap(Map.Entry::getKey, Map.Entry::getValue);
            return Mono.zip(fleets, vehicles, budgets, expenses, km, trend(actor, month, fleetId))
                    .map(t -> BudgetTracking.build(month, LocalDate.now(), t.getT1(), t.getT2(), t.getT3(), t.getT4(), t.getT5(), t.getT6(), TYPE_LABELS));
        });
    }

    private DatabaseClient.GenericExecuteSpec bindScope(DatabaseClient.GenericExecuteSpec spec, UUID actor, UUID fleetId) {
        spec = spec.bind("a", actor);
        return fleetId != null ? spec.bind("f", fleetId) : spec;
    }

    private Mono<List<BudgetTracking.MonthPoint>> trend(UUID actor, LocalDate month, UUID fleetId) {
        String only = fleetId != null ? " AND id = :f" : "";
        LocalDate from = month.minusMonths(TREND_MONTHS - 1);
        return bindScope(db.sql("""
                SELECT m::date AS month,
                       COALESCE((SELECT SUM(b.amount) FROM fleet.budgets b
                                 WHERE b.scope = 'FLEET' AND b.budget_month = m::date
                                   AND b.entity_id IN (""" + SCOPE_FLEETS + only + """
                       )), 0) AS budget,
                       COALESCE((SELECT SUM(e.amount) FROM fleet.expenses e
                                 WHERE e.status = 'APPROVED' AND e.expense_date >= m AND e.expense_date < m + INTERVAL '1 month'
                                   AND e.fleet_id IN (""" + SCOPE_FLEETS + only + """
                       )), 0) AS consumed
                FROM generate_series(CAST(:from AS timestamp), CAST(:to AS timestamp), INTERVAL '1 month') AS m
                ORDER BY m
                """), actor, fleetId)
                .bind("from", from.atStartOfDay())
                .bind("to", month.atStartOfDay())
                .map(row -> new BudgetTracking.MonthPoint(row.get("month", LocalDate.class), row.get("budget", BigDecimal.class),
                        row.get("consumed", BigDecimal.class)))
                .all().collectList();
    }

    // ── Grand livre d'un budget ──────────────────────────────────────────────

    /**
     * Tous les mouvements d'un budget par ordre chronologique, avec le solde après chacun :
     * création, changements de montant, seuils, et chaque dépense approuvée (qui, quoi, quel
     * véhicule, qui a validé, justificatif). Les dépenses en attente et rejetées sont à part : elles
     * ne pèsent pas sur le solde.
     */
    public Mono<Ledger> ledger(UUID actorId, UUID budgetId) {
        return resourceAccess.assertBudgetAccess(actorId, budgetId)
                .then(db.sql("""
                        SELECT b.scope, b.entity_id, b.budget_month, b.amount, b.alert_level,
                               CASE WHEN b.scope = 'FLEET' THEN f.name ELSE v.license_plate END AS name
                        FROM fleet.budgets b
                        LEFT JOIN fleet.fleets f ON b.scope = 'FLEET' AND f.id = b.entity_id
                        LEFT JOIN fleet.vehicles v ON b.scope = 'VEHICLE' AND v.id = b.entity_id
                        WHERE b.id = :id
                        """).bind("id", budgetId).fetch().one())
                .switchIfEmpty(Mono.error(BudgetException.budgetNotFound(budgetId)))
                .flatMap(row -> {
                    String scope = (String) row.get("scope");
                    UUID entityId = (UUID) row.get("entity_id");
                    LocalDate month = (LocalDate) row.get("budget_month");
                    BigDecimal amount = (BigDecimal) row.get("amount");
                    return Mono.zip(events(budgetId), expensesOf(scope, entityId, month)).map(t -> {
                        List<LedgerEntry> entries = merge(t.getT1(), t.getT2().approved(), amount);
                        BigDecimal consumed = t.getT2().approved().stream().map(ExpenseLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
                        return new Ledger(budgetId, scope, entityId, (String) row.get("name"), month, amount, consumed,
                                amount.subtract(consumed), (String) row.get("alert_level"), entries,
                                t.getT2().pending(), t.getT2().rejected());
                    });
                });
    }

    private record EventLine(LocalDateTime at, String type, BigDecimal oldAmount, BigDecimal newAmount, BigDecimal consumed,
                             String actor, String note) {
    }

    private record ExpenseLine(UUID id, LocalDateTime date, String type, BigDecimal amount, String description, String plate,
                               String driver, String source, UUID sourceId, String submittedBy, String validatedBy,
                               LocalDateTime validatedAt, String receiptUrl, boolean overBudget) {
    }

    private record Expenses(List<ExpenseLine> approved, List<OpenExpense> pending, List<OpenExpense> rejected) {
    }

    private Mono<List<EventLine>> events(UUID budgetId) {
        return db.sql("""
                SELECT e.created_at, e.event_type, e.old_amount, e.new_amount, e.consumed, e.note,
                       NULLIF(TRIM(COALESCE(u.first_name, '') || ' ' || COALESCE(u.last_name, '')), '') AS actor
                FROM fleet.budget_events e LEFT JOIN fleet.users u ON u.id = e.actor_id
                WHERE e.budget_id = :id ORDER BY e.created_at
                """).bind("id", budgetId)
                .map(r -> new EventLine(r.get("created_at", LocalDateTime.class), r.get("event_type", String.class),
                        r.get("old_amount", BigDecimal.class), r.get("new_amount", BigDecimal.class), r.get("consumed", BigDecimal.class),
                        r.get("actor", String.class), r.get("note", String.class)))
                .all().collectList();
    }

    private Mono<Expenses> expensesOf(String scope, UUID entityId, LocalDate month) {
        String target = "FLEET".equals(scope) ? "e.fleet_id = :e" : "e.vehicle_id = :e";
        return db.sql("""
                SELECT e.id, e.expense_date, e.expense_type, e.amount, e.description, e.status, e.source_type, e.source_id,
                       COALESCE(e.vehicle_registration, v.license_plate) AS plate, e.driver_full_name, e.approval_level,
                       e.rejection_reason, e.validated_at, e.receipt_url, e.over_budget,
                       NULLIF(TRIM(COALESCE(c.first_name, '') || ' ' || COALESCE(c.last_name, '')), '') AS submitted_by,
                       NULLIF(TRIM(COALESCE(r.first_name, '') || ' ' || COALESCE(r.last_name, '')), '') AS validated_by
                FROM fleet.expenses e
                LEFT JOIN fleet.vehicles v ON v.id = e.vehicle_id
                LEFT JOIN fleet.users c ON c.id = e.created_by
                LEFT JOIN fleet.users r ON r.id = e.validated_by
                WHERE\s""" + target + """
                  AND e.expense_date >= :from AND e.expense_date < :to
                ORDER BY e.expense_date
                """)
                .bind("e", entityId)
                .bind("from", month.atStartOfDay())
                .bind("to", month.plusMonths(1).atStartOfDay())
                .fetch().all()
                .collectList()
                .map(rows -> {
                    List<ExpenseLine> approved = new ArrayList<>();
                    List<OpenExpense> pending = new ArrayList<>();
                    List<OpenExpense> rejected = new ArrayList<>();
                    for (Map<String, Object> r : rows) {
                        String status = (String) r.get("status");
                        if ("APPROVED".equals(status)) {
                            approved.add(new ExpenseLine((UUID) r.get("id"), (LocalDateTime) r.get("expense_date"),
                                    (String) r.get("expense_type"), (BigDecimal) r.get("amount"), (String) r.get("description"),
                                    (String) r.get("plate"), (String) r.get("driver_full_name"), (String) r.get("source_type"),
                                    (UUID) r.get("source_id"), (String) r.get("submitted_by"), (String) r.get("validated_by"),
                                    (LocalDateTime) r.get("validated_at"), (String) r.get("receipt_url"),
                                    Boolean.TRUE.equals(r.get("over_budget"))));
                        } else {
                            OpenExpense open = new OpenExpense((UUID) r.get("id"), (LocalDateTime) r.get("expense_date"),
                                    (String) r.get("expense_type"), (BigDecimal) r.get("amount"), status, (String) r.get("description"),
                                    (String) r.get("plate"), (String) r.get("driver_full_name"), (String) r.get("approval_level"),
                                    (String) r.get("rejection_reason"), (String) r.get("submitted_by"));
                            ("PENDING".equals(status) ? pending : rejected).add(open);
                        }
                    }
                    return new Expenses(approved, pending, rejected);
                });
    }

    /** Fusionne journal et dépenses par date, en recalculant plafond, consommé et solde après chaque ligne. */
    static List<LedgerEntry> merge(List<EventLine> events, List<ExpenseLine> expenses, BigDecimal currentAmount) {
        record Item(LocalDateTime at, int order, EventLine event, ExpenseLine expense) {
        }
        List<Item> items = new ArrayList<>();
        // À date égale, le journal d'abord : la création précède les dépenses du même instant.
        events.forEach(e -> items.add(new Item(e.at(), 0, e, null)));
        expenses.forEach(e -> items.add(new Item(e.date(), 1, null, e)));
        items.sort(Comparator.comparing(Item::at).thenComparing(Item::order));

        // Plafond de départ : le premier montant connu du journal, sinon le montant actuel.
        BigDecimal budget = events.stream()
                .map(e -> e.oldAmount() != null ? e.oldAmount() : e.newAmount())
                .filter(java.util.Objects::nonNull)
                .findFirst().orElse(currentAmount);
        BigDecimal consumed = BigDecimal.ZERO;
        List<LedgerEntry> out = new ArrayList<>();
        for (Item item : items) {
            if (item.event() != null) {
                EventLine e = item.event();
                if (e.newAmount() != null) budget = e.newAmount();
                out.add(new LedgerEntry(e.at(), e.type(), e.newAmount(), e.oldAmount(), budget, consumed, budget.subtract(consumed),
                        null, null, null, null, null, null, null, null, null, null, null, false, e.actor(), e.note()));
            } else {
                ExpenseLine x = item.expense();
                consumed = consumed.add(x.amount());
                out.add(new LedgerEntry(x.date(), "EXPENSE", x.amount(), null, budget, consumed, budget.subtract(consumed),
                        x.type(), x.description(), x.plate(), x.driver(), x.source(), x.sourceId(), x.id(), x.submittedBy(),
                        x.validatedBy(), x.validatedAt(), x.receiptUrl(), x.overBudget(), null, null));
            }
        }
        return out;
    }
}
