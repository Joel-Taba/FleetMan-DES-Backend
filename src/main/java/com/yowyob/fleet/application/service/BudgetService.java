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
    private final CompanyScopeService companyScope;
    private final org.springframework.r2dbc.core.DatabaseClient db;
    private final BudgetJournal journal;

    /** Flotte d'une cible de budget : la flotte elle-même, ou celle du véhicule. */
    private record FleetInfo(UUID fleetId, UUID managerId, UUID companyId) {}

    private Mono<FleetInfo> fleetInfo(Budget.BudgetScope scope, UUID entityId) {
        String sql = scope == Budget.BudgetScope.FLEET
                ? "SELECT f.id, f.manager_id, f.company_id FROM fleet.fleets f WHERE f.id = :e"
                : "SELECT f.id, f.manager_id, f.company_id FROM fleet.vehicles v JOIN fleet.fleets f ON f.id = v.fleet_id WHERE v.id = :e";
        return db.sql(sql).bind("e", entityId)
                .map((row, meta) -> new FleetInfo(row.get("id", UUID.class), row.get("manager_id", UUID.class),
                        row.get("company_id", UUID.class)))
                .one()
                .switchIfEmpty(Mono.error(scope == Budget.BudgetScope.FLEET
                        ? BudgetException.fleetNotFound(entityId) : BudgetException.vehicleNotFound(entityId)));
    }

    private Mono<Boolean> isCompanyAdmin(UUID actorId, UUID companyId) {
        return companyScope.isCompanyAdmin(actorId)
                .flatMap(admin -> !Boolean.TRUE.equals(admin)
                        ? Mono.just(false)
                        : companyScope.resolveCompanyId(actorId).map(c -> c.equals(companyId)).defaultIfEmpty(false));
    }

    /** Somme des budgets de véhicules d'une flotte pour un mois (hors {@code excludeId}). */
    private Mono<BigDecimal> vehicleBudgetsTotal(UUID fleetId, LocalDate month, UUID excludeId) {
        var spec = db.sql("""
                        SELECT COALESCE(SUM(b.amount), 0) AS total FROM fleet.budgets b
                        JOIN fleet.vehicles v ON v.id = b.entity_id
                        WHERE b.scope = 'VEHICLE' AND v.fleet_id = :f AND b.budget_month = :m
                          AND (CAST(:x AS uuid) IS NULL OR b.id <> CAST(:x AS uuid))
                        """)
                .bind("f", fleetId).bind("m", month);
        spec = excludeId != null ? spec.bind("x", excludeId) : spec.bindNull("x", UUID.class);
        return spec.map((row, meta) -> row.get("total", BigDecimal.class)).one().defaultIfEmpty(BigDecimal.ZERO);
    }

    /**
     * Règles d'une enveloppe : la flotte relève de l'administrateur ; un budget de véhicule
     * (gestionnaire ou admin) exige l'enveloppe du mois et la somme des budgets de véhicules
     * ne la dépasse pas. Les mois écoulés sont clos.
     */
    private Mono<Void> assertCanAllocate(Budget.BudgetScope scope, UUID entityId, LocalDate month,
                                         BigDecimal amount, UUID excludeId, UUID actorId) {
        if (month.isBefore(LocalDate.now().withDayOfMonth(1))) {
            return Mono.error(BudgetException.monthClosed());
        }
        return fleetInfo(scope, entityId).flatMap(info -> {
            if (scope == Budget.BudgetScope.FLEET) {
                return isCompanyAdmin(actorId, info.companyId())
                        .flatMap(admin -> !Boolean.TRUE.equals(admin)
                                ? Mono.<Void>error(BudgetException.fleetEnvelopeAdminOnly())
                                : vehicleBudgetsTotal(info.fleetId(), month, null)
                                        .flatMap(allocated -> amount != null && allocated.compareTo(amount) > 0
                                                ? Mono.<Void>error(BudgetException.vehicleBudgetsExceedEnvelope(allocated, amount))
                                                : Mono.<Void>empty()));
            }
            return budgetPersistence.findByEntityAndMonth(Budget.BudgetScope.FLEET, info.fleetId(), month)
                    .switchIfEmpty(Mono.error(BudgetException.fleetEnvelopeRequired()))
                    .flatMap(envelope -> vehicleBudgetsTotal(info.fleetId(), month, excludeId)
                            .flatMap(others -> {
                                BigDecimal total = others.add(amount != null ? amount : BigDecimal.ZERO);
                                return total.compareTo(envelope.getAmount()) > 0
                                        ? Mono.<Void>error(BudgetException.vehicleBudgetsExceedEnvelope(total, envelope.getAmount()))
                                        : Mono.<Void>empty();
                            }));
        });
    }

    // ── 1. CRÉATION ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    public Mono<Budget> createBudget(CreateBudgetCommand cmd) {
        LocalDate month = cmd.budgetMonth().withDayOfMonth(1);

        // Vérification unicité : un seul budget par entité par mois
        return assertCanAllocate(cmd.scope(), cmd.entityId(), month, cmd.amount(), null, cmd.managerId())
                .then(budgetPersistence.existsByEntityAndMonth(cmd.scope(), cmd.entityId(), month))
                .zipWith(fleetInfo(cmd.scope(), cmd.entityId()))
                .flatMap(t -> {
                    if (Boolean.TRUE.equals(t.getT1())) {
                        return Mono.error(BudgetException.budgetAlreadyExists(cmd.entityId(), month));
                    }

                    // Le budget est rattaché au gestionnaire de la flotte, quel que soit son auteur.
                    Budget budget = new Budget(
                            null,
                            cmd.scope(),
                            cmd.entityId(),
                            t.getT2().managerId(),
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
                            .flatMap(saved -> db.sql("UPDATE fleet.budgets SET created_by = :u WHERE id = :id")
                                    .bind("u", cmd.managerId()).bind("id", saved.getId())
                                    .fetch().rowsUpdated().thenReturn(saved))
                            .flatMap(saved -> journal.record(saved, "CREATED", null, saved.getAmount(), cmd.managerId(), cmd.notes())
                                    .thenReturn(saved))
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
    public Mono<Budget> update(UpdateBudgetCommand cmd, UUID actorId) {
        return budgetPersistence.findById(cmd.budgetId())
                .switchIfEmpty(Mono.error(BudgetException.budgetNotFound(cmd.budgetId())))
                .flatMap(budget -> assertCanAllocate(budget.getScope(), budget.getEntityId(), budget.getBudgetMonth(),
                        cmd.amount() != null ? cmd.amount() : budget.getAmount(), budget.getId(), actorId).thenReturn(budget))
                .flatMap(budget -> {
                    BigDecimal previous = budget.getAmount();
                    Budget.AlertLevel previousLevel = budget.getAlertLevel();
                    if (cmd.amount() != null) {
                        budget.updateAmount(cmd.amount());
                    }
                    if (cmd.notes() != null) {
                        budget.setNotes(cmd.notes());
                    }
                    Mono<Void> event = cmd.amount() != null && previous.compareTo(cmd.amount()) != 0
                            ? journal.record(budget, "AMOUNT_CHANGED", previous, cmd.amount(), actorId, cmd.notes())
                            : Mono.empty();
                    return budgetPersistence.save(budget)
                            .flatMap(saved -> event.then(journal.recordLevelChange(saved, previousLevel, actorId)).thenReturn(saved));
                });
    }

    // ── 4. SUPPRESSION ───────────────────────────────────────────────────────

    @Override
    public Mono<Void> delete(UUID id, UUID actorId) {
        return budgetPersistence.findById(id)
                .switchIfEmpty(Mono.error(BudgetException.budgetNotFound(id)))
                .flatMap(budget -> assertCanAllocate(budget.getScope(), budget.getEntityId(), budget.getBudgetMonth(),
                                null, budget.getId(), actorId)
                        .then(budget.getScope() == Budget.BudgetScope.FLEET
                                ? vehicleBudgetsTotal(budget.getEntityId(), budget.getBudgetMonth(), null)
                                        .flatMap(allocated -> allocated.signum() > 0
                                                ? Mono.<Void>error(BudgetException.envelopeHasVehicleBudgets())
                                                : Mono.<Void>empty())
                                : Mono.<Void>empty())
                        .then(journal.record(budget, "DELETED", budget.getAmount(), null, actorId, null))
                        .then(budgetPersistence.deleteById(id)));
    }

    @Override
    public Flux<Budget> getAllByCompany(UUID companyId, LocalDate month) {
        LocalDate m = (month != null ? month : LocalDate.now()).withDayOfMonth(1);
        return db.sql("""
                        SELECT b.id FROM fleet.budgets b
                        LEFT JOIN fleet.vehicles v ON b.scope = 'VEHICLE' AND v.id = b.entity_id
                        JOIN fleet.fleets f ON f.id = CASE WHEN b.scope = 'FLEET' THEN b.entity_id ELSE v.fleet_id END
                        WHERE f.company_id = :c AND b.budget_month = :m
                        ORDER BY b.scope, f.name
                        """)
                .bind("c", companyId).bind("m", m)
                .map((row, meta) -> row.get("id", UUID.class))
                .all()
                .concatMap(budgetPersistence::findById);
    }

    @Override
    @Transactional
    public Mono<Void> ensureFleetEnvelope(UUID fleetId) {
        LocalDate month = LocalDate.now().withDayOfMonth(1);
        return db.sql("SELECT manager_id, monthly_budget FROM fleet.fleets WHERE id = :f AND deleted_at IS NULL")
                .bind("f", fleetId)
                .fetch().one()
                .flatMap(row -> {
                    BigDecimal amount = (BigDecimal) row.get("monthly_budget");
                    UUID managerId = (UUID) row.get("manager_id");
                    if (amount == null || amount.signum() <= 0 || managerId == null) {
                        return Mono.<Void>empty();
                    }
                    return budgetPersistence.findByEntityAndMonth(Budget.BudgetScope.FLEET, fleetId, month)
                            .flatMap(existing -> {
                                // L'enveloppe du mois suit l'enveloppe récurrente, sans descendre sous la
                                // somme déjà répartie entre les véhicules.
                                return vehicleBudgetsTotal(fleetId, month, null).flatMap(allocated -> {
                                    if (allocated.compareTo(amount) > 0) {
                                        return Mono.<Void>error(BudgetException.vehicleBudgetsExceedEnvelope(allocated, amount));
                                    }
                                    BigDecimal previous = existing.getAmount();
                                    Budget.AlertLevel previousLevel = existing.getAlertLevel();
                                    existing.updateAmount(amount);
                                    Mono<Void> event = previous.compareTo(amount) != 0
                                            ? journal.record(existing, "AMOUNT_CHANGED", previous, amount, null,
                                                    "Enveloppe mensuelle de la flotte")
                                            : Mono.empty();
                                    return budgetPersistence.save(existing).then(event)
                                            .then(journal.recordLevelChange(existing, previousLevel, null))
                                            .then(recalculateConsumed(existing.getId())).then();
                                });
                            })
                            .switchIfEmpty(Mono.defer(() -> budgetPersistence.save(new Budget(null, Budget.BudgetScope.FLEET,
                                            fleetId, managerId, month, amount, BigDecimal.ZERO, Budget.AlertLevel.NORMAL,
                                            false, false, "Enveloppe mensuelle de la flotte", null, null))
                                    .flatMap(saved -> journal.record(saved, "CREATED", null, amount, null,
                                                    "Enveloppe mensuelle de la flotte")
                                            .then(recalculateConsumed(saved.getId())))
                                    .then()));
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
                                Budget.AlertLevel previousLevel = budget.getAlertLevel();
                                Budget.AlertLevel newLevel = budget.updateConsumed(total);
                                log.debug("Budget {} recalculé : {}% consommé [{}]",
                                        budgetId, budget.consumptionRate(), newLevel);
                                return budgetPersistence.save(budget)
                                        .flatMap(saved -> journal.recordLevelChange(saved, previousLevel, null).thenReturn(saved));
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
