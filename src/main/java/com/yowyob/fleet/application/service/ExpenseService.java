package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.BudgetException;
import com.yowyob.fleet.domain.model.Budget;
import com.yowyob.fleet.domain.model.Expense;
import com.yowyob.fleet.domain.ports.in.ManageExpenseUseCase;
import com.yowyob.fleet.domain.ports.out.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseService implements ManageExpenseUseCase {

    private final ExpensePersistencePort expensePersistence;
    private final VehiclePersistencePort vehiclePersistence;
    private final DriverPersistencePort driverPersistence;
    private final BudgetPersistencePort budgetPersistence;

    // ── 1. CRÉATION MANUELLE ─────────────────────────────────────────────────

    @Override
    @Transactional
    public Mono<Expense> createManualExpense(CreateManualExpenseCommand cmd) {

        // 1. Vérification existence du véhicule + récupération fleetId, managerId
        return vehiclePersistence.getLocalDataById(cmd.vehicleId())
                .switchIfEmpty(Mono.error(BudgetException.vehicleNotFound(cmd.vehicleId())))
                .flatMap(vehicle -> {

                    // 2. Résolution optionnelle du chauffeur
                    Mono<String> driverNameMono = cmd.driverId() != null
                            ? driverPersistence.findById(cmd.driverId())
                                    .switchIfEmpty(Mono.error(BudgetException.driverNotFound(cmd.driverId())))
                                    .map(d -> d.userId().toString())
                            : Mono.just("");

                    return driverNameMono.flatMap(driverName -> {

                        LocalDateTime expenseDate = cmd.expenseDate() != null
                                ? cmd.expenseDate()
                                : LocalDateTime.now();

                        Expense expense = new Expense(
                                null,
                                cmd.expenseType(),
                                cmd.amount(),
                                cmd.description(),
                                expenseDate,
                                Expense.SourceType.MANUAL,
                                null,
                                cmd.vehicleId(),
                                vehicle.licensePlate(),
                                vehicle.fleetId(),
                                vehicle.managerId(),
                                cmd.driverId(),
                                driverName.isBlank() ? null : driverName,
                                null
                        );

                        return expensePersistence.save(expense)
                                .flatMap(saved -> {
                                    // 3. Mise à jour asynchrone des budgets actifs (fire & forget)
                                    triggerBudgetUpdate(saved.getFleetId(), saved.getVehicleId(), saved.getExpenseDate());
                                    return Mono.just(saved);
                                });
                    });
                });
    }

    // ── 2. CRÉATION AUTO-GÉNÉRÉE ─────────────────────────────────────────────

    @Override
    @Transactional
    public Mono<Expense> createAutoExpense(Expense.ExpenseType type,
                                            BigDecimal amount,
                                            String description,
                                            UUID sourceId,
                                            UUID vehicleId,
                                            String vehicleRegistration,
                                            UUID fleetId,
                                            UUID managerId,
                                            UUID driverId,
                                            String driverFullName) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.debug("Dépense auto ignorée (montant nul ou négatif) pour le véhicule {}", vehicleId);
            return Mono.empty();
        }

        Expense expense = new Expense(
                null, type, amount, description,
                LocalDateTime.now(),
                Expense.SourceType.AUTO, sourceId,
                vehicleId, vehicleRegistration,
                fleetId, managerId,
                driverId, driverFullName,
                null
        );

        return expensePersistence.save(expense)
                .doOnSuccess(saved -> {
                    log.info("✅ Dépense auto [{}] créée pour véhicule {} — montant: {} FCFA",
                            type, vehicleId, amount);
                    triggerBudgetUpdate(fleetId, vehicleId, saved.getExpenseDate());
                });
    }

    // ── 3. LECTURE ───────────────────────────────────────────────────────────

    @Override
    public Mono<Expense> getById(UUID id) {
        return expensePersistence.findById(id)
                .switchIfEmpty(Mono.error(BudgetException.expenseNotFound(id)));
    }

    @Override
    public Flux<Expense> getAllByManager(UUID managerId) {
        return expensePersistence.findAllByManagerId(managerId);
    }

    @Override
    public Flux<Expense> getByFleet(UUID fleetId) {
        return expensePersistence.findByFleetId(fleetId);
    }

    @Override
    public Flux<Expense> getByVehicle(UUID vehicleId) {
        return vehiclePersistence.getLocalDataById(vehicleId)
                .switchIfEmpty(Mono.error(BudgetException.vehicleNotFound(vehicleId)))
                .thenMany(expensePersistence.findByVehicleId(vehicleId));
    }

    @Override
    public Flux<Expense> getByDriver(UUID driverId) {
        return expensePersistence.findByDriverId(driverId);
    }

    @Override
    public Flux<Expense> getByType(Expense.ExpenseType type, UUID managerId) {
        return expensePersistence.findByTypeAndManagerId(type, managerId);
    }

    @Override
    public Flux<Expense> getByStatus(Expense.ExpenseStatus status, UUID managerId) {
        return expensePersistence.findByStatusAndManagerId(status, managerId);
    }

    @Override
    public Flux<Expense> getByDateRange(LocalDateTime start, LocalDateTime end, UUID managerId) {
        if (start != null && end != null && start.isAfter(end)) {
            return Flux.error(BudgetException.invalidDateRange());
        }
        return expensePersistence.findByDateRange(start, end, managerId);
    }

    @Override
    public Flux<Expense> getPendingExpenses(UUID managerId) {
        return expensePersistence.findByStatusAndManagerId(Expense.ExpenseStatus.PENDING, managerId);
    }

    // ── 4. AGRÉGATS / KPIs ───────────────────────────────────────────────────

    @Override
    public Mono<BigDecimal> getTotalApprovedByVehicle(UUID vehicleId) {
        return expensePersistence.getTotalApprovedByVehicleId(vehicleId)
                .defaultIfEmpty(BigDecimal.ZERO);
    }

    @Override
    public Mono<BigDecimal> getTotalApprovedByFleet(UUID fleetId) {
        return expensePersistence.getTotalApprovedByFleetId(fleetId)
                .defaultIfEmpty(BigDecimal.ZERO);
    }

    @Override
    public Mono<ExpenseSummaryDto> getSummaryByManager(UUID managerId) {
        return expensePersistence.findAllByManagerId(managerId)
                .filter(e -> e.getStatus() == Expense.ExpenseStatus.APPROVED)
                .collectList()
                .map(expenses -> {
                    BigDecimal fuel    = sum(expenses, Expense.ExpenseType.FUEL);
                    BigDecimal maint   = sum(expenses, Expense.ExpenseType.MAINTENANCE);
                    BigDecimal inc     = sum(expenses, Expense.ExpenseType.INCIDENT);
                    BigDecimal fine    = sum(expenses, Expense.ExpenseType.FINE);
                    BigDecimal toll    = sum(expenses, Expense.ExpenseType.TOLL);
                    BigDecimal other   = sum(expenses, Expense.ExpenseType.OTHER);
                    BigDecimal total   = fuel.add(maint).add(inc).add(fine).add(toll).add(other);
                    return new ExpenseSummaryDto(fuel, maint, inc, fine, toll, other, total, expenses.size());
                });
    }

    // ── 5. VALIDATION ────────────────────────────────────────────────────────

    @Override
    @Transactional
    public Mono<Expense> approve(UUID expenseId, UUID managerId) {
        return expensePersistence.findById(expenseId)
                .switchIfEmpty(Mono.error(BudgetException.expenseNotFound(expenseId)))
                .flatMap(expense -> {
                    if (expense.getStatus() != Expense.ExpenseStatus.PENDING) {
                        return Mono.error(BudgetException.expenseAlreadyValidated(expenseId));
                    }
                    expense.approve(managerId);
                    return expensePersistence.save(expense)
                            .doOnSuccess(saved -> {
                                log.info("✅ Dépense {} approuvée par manager {}", expenseId, managerId);
                                triggerBudgetUpdate(saved.getFleetId(), saved.getVehicleId(), saved.getExpenseDate());
                            });
                });
    }

    @Override
    @Transactional
    public Mono<Expense> reject(ValidateExpenseCommand cmd) {
        return expensePersistence.findById(cmd.expenseId())
                .switchIfEmpty(Mono.error(BudgetException.expenseNotFound(cmd.expenseId())))
                .flatMap(expense -> {
                    if (expense.getStatus() != Expense.ExpenseStatus.PENDING) {
                        return Mono.error(BudgetException.expenseAlreadyValidated(cmd.expenseId()));
                    }
                    if (cmd.rejectionReason() == null || cmd.rejectionReason().isBlank()) {
                        return Mono.error(BudgetException.rejectionReasonRequired());
                    }
                    expense.reject(cmd.managerId(), cmd.rejectionReason());
                    return expensePersistence.save(expense)
                            .doOnSuccess(saved ->
                                    log.info("❌ Dépense {} rejetée par manager {} — motif: {}",
                                            cmd.expenseId(), cmd.managerId(), cmd.rejectionReason()));
                });
    }

    // ── 6. MISE À JOUR ───────────────────────────────────────────────────────

    @Override
    @Transactional
    public Mono<Expense> update(UpdateExpenseCommand cmd) {
        return expensePersistence.findById(cmd.expenseId())
                .switchIfEmpty(Mono.error(BudgetException.expenseNotFound(cmd.expenseId())))
                .flatMap(expense -> {
                    if (!expense.isManualAndPending()) {
                        return Mono.error(BudgetException.expenseNotEditable(cmd.expenseId()));
                    }
                    if (cmd.amount() != null) {
                        expense.updateAmount(cmd.amount());
                    }
                    if (cmd.description() != null) {
                        expense.setDescription(cmd.description());
                    }
                    return expensePersistence.save(expense);
                });
    }

    // ── 7. SUPPRESSION ───────────────────────────────────────────────────────

    @Override
    @Transactional
    public Mono<Void> delete(UUID id) {
        return expensePersistence.findById(id)
                .switchIfEmpty(Mono.error(BudgetException.expenseNotFound(id)))
                .flatMap(expense -> {
                    if (expense.getSourceType() == Expense.SourceType.AUTO) {
                        return Mono.error(BudgetException.expenseDeleteForbidden(id));
                    }
                    return expensePersistence.deleteById(id);
                });
    }

    // ── HELPERS PRIVÉS ────────────────────────────────────────────────────────

    private BigDecimal sum(java.util.List<Expense> expenses, Expense.ExpenseType type) {
        return expenses.stream()
                .filter(e -> e.getExpenseType() == type)
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Déclenche la mise à jour asynchrone des budgets actifs (fire & forget).
     * Met à jour le budget flotte ET le budget véhicule si ils existent.
     */
    private void triggerBudgetUpdate(UUID fleetId, UUID vehicleId, LocalDateTime expenseDate) {
        if (fleetId == null || expenseDate == null) return;
        LocalDate month = expenseDate.toLocalDate().withDayOfMonth(1);

        // Budget flotte
        budgetPersistence.findByEntityAndMonth(Budget.BudgetScope.FLEET, fleetId, month)
                .flatMap(budget -> recalcAndSaveBudget(budget, fleetId, null, month))
                .doOnError(e -> log.warn("⚠️ Erreur recalcul budget flotte {}: {}", fleetId, e.getMessage()))
                .subscribe();

        // Budget véhicule (si vehicleId fourni)
        if (vehicleId != null) {
            budgetPersistence.findByEntityAndMonth(Budget.BudgetScope.VEHICLE, vehicleId, month)
                    .flatMap(budget -> recalcAndSaveBudget(budget, null, vehicleId, month))
                    .doOnError(e -> log.warn("⚠️ Erreur recalcul budget véhicule {}: {}", vehicleId, e.getMessage()))
                    .subscribe();
        }
    }

    private Mono<Budget> recalcAndSaveBudget(Budget budget, UUID fleetId, UUID vehicleId, LocalDate month) {
        LocalDateTime monthStart = month.atStartOfDay();
        LocalDateTime monthEnd   = month.plusMonths(1).atStartOfDay();

        Mono<BigDecimal> totalMono = (fleetId != null)
                ? expensePersistence.getTotalApprovedByFleetAndMonth(fleetId, monthStart, monthEnd)
                : expensePersistence.getTotalApprovedByVehicleAndMonth(vehicleId, monthStart, monthEnd);

        return totalMono.defaultIfEmpty(BigDecimal.ZERO)
                .flatMap(total -> {
                    budget.updateConsumed(total);
                    return budgetPersistence.save(budget)
                            .doOnSuccess(b -> log.debug(
                                    "Budget {} recalculé — consommé: {} / {} FCFA ({}%)",
                                    b.getId(), b.getConsumed(), b.getAmount(), b.consumptionRate()
                            ));
                });
    }
}
