package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.BudgetException;
import com.yowyob.fleet.domain.model.Budget;
import com.yowyob.fleet.domain.ports.in.ManageBudgetUseCase;
import com.yowyob.fleet.domain.ports.out.BudgetPersistencePort;
import com.yowyob.fleet.domain.ports.out.ExpensePersistencePort;
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
public class BudgetService implements ManageBudgetUseCase {

    private final BudgetPersistencePort budgetPersistence;
    private final ExpensePersistencePort expensePersistence;

    // ── 1. CRÉATION ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    public Mono<Budget> createBudget(CreateBudgetCommand cmd) {
        LocalDate month = cmd.budgetMonth().withDayOfMonth(1);

        // Vérification unicité : un seul budget par entité par mois
        return budgetPersistence.existsByEntityAndMonth(cmd.scope(), cmd.entityId(), month)
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(BudgetException.budgetAlreadyExists(cmd.entityId(), month));
                    }

                    Budget budget = new Budget(
                            null,
                            cmd.scope(),
                            cmd.entityId(),
                            cmd.managerId(),
                            month,
                            cmd.amount(),
                            BigDecimal.ZERO,    // consommé = 0 à la création
                            Budget.AlertLevel.NORMAL,
                            false,
                            false,
                            cmd.notes(),
                            null,
                            null
                    );

                    return budgetPersistence.save(budget)
                            .flatMap(saved -> {
                                // Recalcul immédiat depuis les dépenses déjà existantes ce mois
                                return recalculateConsumed(saved.getId()).defaultIfEmpty(saved);
                            });
                });
    }

    // ── 2. LECTURE ───────────────────────────────────────────────────────────

    @Override
    public Mono<Budget> getById(UUID id) {
        return budgetPersistence.findById(id)
                .switchIfEmpty(Mono.error(BudgetException.budgetNotFound(id)));
    }

    @Override
    public Flux<Budget> getAllByManager(UUID managerId) {
        return budgetPersistence.findAllByManagerId(managerId);
    }

    @Override
    public Flux<Budget> getByFleet(UUID fleetId) {
        return budgetPersistence.findByFleetId(fleetId);
    }

    @Override
    public Flux<Budget> getByVehicle(UUID vehicleId) {
        return budgetPersistence.findByVehicleId(vehicleId);
    }

    @Override
    public Mono<Budget> getCurrentBudget(Budget.BudgetScope scope, UUID entityId) {
        LocalDate currentMonth = LocalDate.now().withDayOfMonth(1);
        return budgetPersistence.findByEntityAndMonth(scope, entityId, currentMonth);
    }

    @Override
    public Mono<Budget> getBudgetForMonth(Budget.BudgetScope scope, UUID entityId, LocalDate month) {
        return budgetPersistence.findByEntityAndMonth(scope, entityId, month.withDayOfMonth(1));
    }

    // ── 3. MISE À JOUR ───────────────────────────────────────────────────────

    @Override
    @Transactional
    public Mono<Budget> update(UpdateBudgetCommand cmd) {
        return budgetPersistence.findById(cmd.budgetId())
                .switchIfEmpty(Mono.error(BudgetException.budgetNotFound(cmd.budgetId())))
                .flatMap(budget -> {
                    if (cmd.amount() != null) {
                        budget.updateAmount(cmd.amount());
                    }
                    if (cmd.notes() != null) {
                        budget.setNotes(cmd.notes());
                    }
                    return budgetPersistence.save(budget);
                });
    }

    // ── 4. SUPPRESSION ───────────────────────────────────────────────────────

    @Override
    public Mono<Void> delete(UUID id) {
        return budgetPersistence.existsById(id)
                .flatMap(exists -> {
                    if (!exists) return Mono.error(BudgetException.budgetNotFound(id));
                    return budgetPersistence.deleteById(id);
                });
    }

    // ── 5. RECALCUL ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    public Mono<Budget> recalculateConsumed(UUID budgetId) {
        return budgetPersistence.findById(budgetId)
                .switchIfEmpty(Mono.error(BudgetException.budgetNotFound(budgetId)))
                .flatMap(budget -> {
                    LocalDateTime monthStart = budget.getBudgetMonth().atStartOfDay();
                    LocalDateTime monthEnd   = budget.getBudgetMonth().plusMonths(1).atStartOfDay();

                    Mono<BigDecimal> totalMono;
                    if (budget.getScope() == Budget.BudgetScope.FLEET) {
                        totalMono = expensePersistence
                                .getTotalApprovedByFleetAndMonth(budget.getEntityId(), monthStart, monthEnd);
                    } else {
                        totalMono = expensePersistence
                                .getTotalApprovedByVehicleAndMonth(budget.getEntityId(), monthStart, monthEnd);
                    }

                    return totalMono.defaultIfEmpty(BigDecimal.ZERO)
                            .flatMap(total -> {
                                Budget.AlertLevel newLevel = budget.updateConsumed(total);
                                log.debug("Budget {} recalculé — {}% consommé [{}]",
                                        budgetId, budget.consumptionRate(), newLevel);
                                return budgetPersistence.save(budget);
                            });
                });
    }

    @Override
    public Flux<Budget> recalculateAllActiveByManager(UUID managerId) {
        LocalDate currentMonth = LocalDate.now().withDayOfMonth(1);
        return budgetPersistence.findActiveByManagerId(managerId, currentMonth)
                .flatMap(budget -> recalculateConsumed(budget.getId())
                        .doOnError(e -> log.error("Erreur recalcul budget {}: {}", budget.getId(), e.getMessage()))
                        .onErrorResume(e -> Mono.empty())
                );
    }
}
