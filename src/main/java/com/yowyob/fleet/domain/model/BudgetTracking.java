package com.yowyob.fleet.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Consolidation de l'utilisation des budgets d'un mois, par niveau : périmètre de l'acteur →
 * flotte → véhicule, et par type, chauffeur et origine de dépense. Le consommé vient toujours des
 * dépenses approuvées ; « engagé » désigne les dépenses en attente, pas encore imputées.
 */
public final class BudgetTracking {

    private BudgetTracking() {
    }

    public record FleetRow(UUID id, String name, String managerName) {
    }

    public record VehicleRow(UUID id, UUID fleetId, String plate) {
    }

    public record BudgetRow(UUID id, String scope, UUID entityId, BigDecimal amount, String alertLevel) {
    }

    public record ExpenseRow(UUID id, UUID fleetId, UUID vehicleId, String plate, UUID driverId, String driverName,
                             String type, String status, String source, BigDecimal amount, LocalDateTime date,
                             String description, boolean overBudget) {
        boolean approved() {
            return "APPROVED".equals(status);
        }

        boolean pending() {
            return "PENDING".equals(status);
        }
    }

    public record Usage(BigDecimal budget, BigDecimal consumed, BigDecimal pending, BigDecimal remaining,
                        Double rate, BigDecimal projected) {
    }

    public record VehicleUsage(UUID vehicleId, String plate, UUID budgetId, Usage usage, String alertLevel,
                               int expenseCount, BigDecimal km, BigDecimal costPerKm) {
    }

    public record FleetUsage(UUID fleetId, String name, String managerName, UUID budgetId, Usage usage, String alertLevel,
                             BigDecimal allocatedToVehicles, BigDecimal unallocated, BigDecimal km, BigDecimal costPerKm,
                             List<VehicleUsage> vehicles) {
    }

    public record Share(String key, String label, BigDecimal amount, int count, double share) {
    }

    public record MonthPoint(LocalDate month, BigDecimal budget, BigDecimal consumed) {
    }

    public record DayPoint(LocalDate date, BigDecimal cumulative) {
    }

    public record Overview(LocalDate month, int daysElapsed, int daysInMonth, Usage totals,
                           BigDecimal allocatedToVehicles, int approvedCount, int pendingCount, int rejectedCount,
                           BigDecimal rejectedAmount, int overBudgetCount, BigDecimal km, BigDecimal costPerKm,
                           List<FleetUsage> fleets, List<Share> byType, List<Share> byDriver, List<Share> bySource,
                           List<MonthPoint> trend, List<DayPoint> daily, List<ExpenseRow> topExpenses) {
    }

    /** Jours écoulés du mois au {@code today} : tout le mois s'il est passé, 0 s'il n'a pas commencé. */
    public static int daysElapsed(LocalDate month, LocalDate today) {
        LocalDate first = month.withDayOfMonth(1);
        if (today.isBefore(first)) return 0;
        if (!today.isBefore(first.plusMonths(1))) return first.lengthOfMonth();
        return today.getDayOfMonth();
    }

    /** Fin de mois au rythme actuel ; pour un mois clos, le consommé ; null si le mois n'a pas commencé. */
    public static BigDecimal project(BigDecimal consumed, int elapsed, int days) {
        if (elapsed == 0) return null;
        return consumed.multiply(BigDecimal.valueOf(days)).divide(BigDecimal.valueOf(elapsed), 0, RoundingMode.HALF_UP);
    }

    static Usage usage(BigDecimal budget, BigDecimal consumed, BigDecimal pending, int elapsed, int days) {
        BigDecimal remaining = budget != null ? budget.subtract(consumed) : null;
        Double rate = budget != null && budget.signum() > 0
                ? consumed.multiply(BigDecimal.valueOf(100)).divide(budget, 1, RoundingMode.HALF_UP).doubleValue()
                : null;
        return new Usage(budget, consumed, pending, remaining, rate, project(consumed, elapsed, days));
    }

    private static BigDecimal sum(List<ExpenseRow> rows, java.util.function.Predicate<ExpenseRow> filter) {
        return rows.stream().filter(filter).map(ExpenseRow::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal costPerKm(BigDecimal consumed, BigDecimal km) {
        return km != null && km.signum() > 0 ? consumed.divide(km, 0, RoundingMode.HALF_UP) : null;
    }

    public static Overview build(LocalDate month, LocalDate today, List<FleetRow> fleets, List<VehicleRow> vehicles,
                                 List<BudgetRow> budgets, List<ExpenseRow> expenses, Map<UUID, BigDecimal> kmByVehicle,
                                 List<MonthPoint> trend, Map<String, String> typeLabels) {
        int days = month.lengthOfMonth();
        int elapsed = daysElapsed(month, today);
        Map<String, BudgetRow> budgetOf = new HashMap<>();
        budgets.forEach(b -> budgetOf.put(b.scope() + b.entityId(), b));

        List<FleetUsage> fleetUsages = new ArrayList<>();
        for (FleetRow fleet : fleets) {
            List<ExpenseRow> ofFleet = expenses.stream().filter(e -> fleet.id().equals(e.fleetId())).toList();
            // Un véhicule sorti de la flotte reste affiché s'il y a dépensé ce mois-là.
            Map<UUID, String> plates = new LinkedHashMap<>();
            vehicles.stream().filter(v -> fleet.id().equals(v.fleetId())).forEach(v -> plates.put(v.id(), v.plate()));
            ofFleet.forEach(e -> plates.putIfAbsent(e.vehicleId(), e.plate()));

            List<VehicleUsage> vehicleUsages = new ArrayList<>();
            BigDecimal allocated = BigDecimal.ZERO;
            BigDecimal fleetKm = BigDecimal.ZERO;
            for (Map.Entry<UUID, String> v : plates.entrySet()) {
                List<ExpenseRow> ofVehicle = ofFleet.stream().filter(e -> v.getKey().equals(e.vehicleId())).toList();
                BudgetRow b = budgetOf.get("VEHICLE" + v.getKey());
                if (b != null) allocated = allocated.add(b.amount());
                BigDecimal consumed = sum(ofVehicle, ExpenseRow::approved);
                BigDecimal km = kmByVehicle.getOrDefault(v.getKey(), BigDecimal.ZERO);
                fleetKm = fleetKm.add(km);
                vehicleUsages.add(new VehicleUsage(v.getKey(), v.getValue(), b != null ? b.id() : null,
                        usage(b != null ? b.amount() : null, consumed, sum(ofVehicle, ExpenseRow::pending), elapsed, days),
                        b != null ? b.alertLevel() : null, ofVehicle.size(), km, costPerKm(consumed, km)));
            }
            vehicleUsages.sort(Comparator.comparing((VehicleUsage v) -> v.usage().consumed()).reversed()
                    .thenComparing(VehicleUsage::plate, Comparator.nullsLast(Comparator.naturalOrder())));

            BudgetRow envelope = budgetOf.get("FLEET" + fleet.id());
            BigDecimal consumed = sum(ofFleet, ExpenseRow::approved);
            fleetUsages.add(new FleetUsage(fleet.id(), fleet.name(), fleet.managerName(), envelope != null ? envelope.id() : null,
                    usage(envelope != null ? envelope.amount() : null, consumed, sum(ofFleet, ExpenseRow::pending), elapsed, days),
                    envelope != null ? envelope.alertLevel() : null, allocated,
                    envelope != null ? envelope.amount().subtract(allocated) : null, fleetKm, costPerKm(consumed, fleetKm),
                    vehicleUsages));
        }
        fleetUsages.sort(Comparator.comparing(FleetUsage::name));

        BigDecimal totalBudget = fleetUsages.stream().map(f -> f.usage().budget()).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal consumed = sum(expenses, ExpenseRow::approved);
        BigDecimal km = fleetUsages.stream().map(FleetUsage::km).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<ExpenseRow> approved = expenses.stream().filter(ExpenseRow::approved).toList();

        return new Overview(month, elapsed, days,
                usage(totalBudget.signum() > 0 ? totalBudget : null, consumed, sum(expenses, ExpenseRow::pending), elapsed, days),
                fleetUsages.stream().map(FleetUsage::allocatedToVehicles).reduce(BigDecimal.ZERO, BigDecimal::add),
                approved.size(), (int) expenses.stream().filter(ExpenseRow::pending).count(),
                (int) expenses.stream().filter(e -> "REJECTED".equals(e.status())).count(),
                sum(expenses, e -> "REJECTED".equals(e.status())),
                (int) expenses.stream().filter(ExpenseRow::overBudget).count(), km, costPerKm(consumed, km),
                fleetUsages,
                shares(approved, ExpenseRow::type, e -> typeLabels.getOrDefault(e.type(), e.type()), consumed),
                shares(approved, e -> e.driverId() != null ? e.driverId().toString() : "NONE",
                        e -> e.driverId() != null ? e.driverName() : null, consumed),
                shares(approved, ExpenseRow::source, ExpenseRow::source, consumed),
                trend, daily(month, approved),
                approved.stream().sorted(Comparator.comparing(ExpenseRow::amount).reversed()).limit(10).toList());
    }

    private static List<Share> shares(List<ExpenseRow> rows, java.util.function.Function<ExpenseRow, String> key,
                                      java.util.function.Function<ExpenseRow, String> label, BigDecimal total) {
        Map<String, List<ExpenseRow>> groups = new LinkedHashMap<>();
        rows.forEach(r -> groups.computeIfAbsent(key.apply(r), k -> new ArrayList<>()).add(r));
        return groups.entrySet().stream().map(g -> {
                    BigDecimal amount = sum(g.getValue(), e -> true);
                    double share = total.signum() > 0
                            ? amount.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP).doubleValue()
                            : 0;
                    return new Share(g.getKey(), label.apply(g.getValue().get(0)), amount, g.getValue().size(), share);
                })
                .sorted(Comparator.comparing(Share::amount).reversed())
                .toList();
    }

    /** Consommé cumulé jour par jour, jusqu'à aujourd'hui pour le mois en cours. */
    private static List<DayPoint> daily(LocalDate month, List<ExpenseRow> approved) {
        LocalDate first = month.withDayOfMonth(1);
        long span = Math.min(first.lengthOfMonth(), Math.max(0, ChronoUnit.DAYS.between(first, LocalDate.now()) + 1));
        BigDecimal running = BigDecimal.ZERO;
        List<DayPoint> points = new ArrayList<>();
        for (int i = 0; i < span; i++) {
            LocalDate day = first.plusDays(i);
            running = running.add(sum(approved, e -> e.date().toLocalDate().equals(day)));
            points.add(new DayPoint(day, running));
        }
        return points;
    }
}
