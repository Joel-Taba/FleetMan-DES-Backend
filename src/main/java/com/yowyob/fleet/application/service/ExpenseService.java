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
    private final CompanyScopeService companyScope;
    private final BudgetJournal budgetJournal;
    private final org.springframework.r2dbc.core.DatabaseClient db;
    private final OperationNotifier notifications;

    // ── 1. CRÉATION MANUELLE ─────────────────────────────────────────────────

    /**
     * Dépense déclarée par un chauffeur (pour lui-même) ou saisie par un gestionnaire. Elle
     * démarre en attente ; son niveau de validation est calculé (gestionnaire, ou admin si
     * saisie par un gestionnaire, au-dessus du seuil de l'entreprise ou en dépassement de budget).
     */
    @Override
    @Transactional
    public Mono<Expense> createManualExpense(CreateManualExpenseCommand cmd) {
        if (cmd.expenseDate() != null && cmd.expenseDate().isAfter(LocalDateTime.now().plusMinutes(5))) {
            return Mono.error(BudgetException.futureExpenseDate());
        }
        if (cmd.expenseType() == Expense.ExpenseType.FUEL || cmd.expenseType() == Expense.ExpenseType.MAINTENANCE
                || cmd.expenseType() == Expense.ExpenseType.INCIDENT) {
            return Mono.error(BudgetException.automaticTypeNotManual());
        }
        if (cmd.expenseType() == Expense.ExpenseType.OTHER && (cmd.description() == null || cmd.description().isBlank())) {
            return Mono.error(BudgetException.descriptionRequired());
        }
        return vehiclePersistence.getLocalDataById(cmd.vehicleId())
                .switchIfEmpty(Mono.error(BudgetException.vehicleNotFound(cmd.vehicleId())))
                .flatMap(vehicle -> isDriver(cmd.createdBy()).flatMap(actorIsDriver -> {
                    // Un chauffeur déclare ses propres frais.
                    UUID driverId = Boolean.TRUE.equals(actorIsDriver) ? cmd.createdBy() : cmd.driverId();
                    Mono<String> driverNameMono = driverId == null
                            ? Mono.just("")
                            : driverPersistence.findById(driverId)
                                    .switchIfEmpty(Mono.error(BudgetException.driverNotFound(driverId)))
                                    .flatMap(d -> java.util.Objects.equals(d.fleetId(), vehicle.fleetId())
                                            ? fullName(driverId)
                                            : Mono.<String>error(BudgetException.driverNotInVehicleFleet()));
                    return driverNameMono.flatMap(driverName -> fleetManagerOf(vehicle.fleetId()).flatMap(managerId -> {
                        Expense expense = new Expense(
                                null,
                                cmd.expenseType(),
                                cmd.amount(),
                                cmd.description(),
                                cmd.expenseDate() != null ? cmd.expenseDate() : LocalDateTime.now(),
                                Expense.SourceType.MANUAL,
                                null,
                                cmd.vehicleId(),
                                vehicle.licensePlate(),
                                vehicle.fleetId(),
                                managerId,
                                driverId,
                                driverName.isBlank() ? null : driverName,
                                null
                        );
                        expense.setCreatedBy(cmd.createdBy());
                        if (cmd.receiptUrl() != null && !cmd.receiptUrl().isBlank()) {
                            expense.attachReceipt(cmd.receiptUrl(), cmd.receiptName(), cmd.receiptMime());
                        }
                        return evaluateApproval(expense).flatMap(expensePersistence::save)
                                .flatMap(saved -> notifications.expenseCreated(saved, cmd.createdBy()).thenReturn(saved));
                    }));
                }));
    }

    @Override
    public Flux<Expense> getAllByCompany(UUID companyId) {
        return db.sql("""
                        SELECT e.id FROM fleet.expenses e JOIN fleet.fleets f ON f.id = e.fleet_id
                        WHERE f.company_id = :c ORDER BY e.expense_date DESC
                        """)
                .bind("c", companyId)
                .map((row, meta) -> row.get("id", UUID.class))
                .all()
                .concatMap(expensePersistence::findById);
    }

    private static final java.util.Map<String, String> INCIDENT_COST_LABELS = java.util.Map.of(
            "REPAIR", "Réparation", "TOWING", "Remorquage", "THIRD_PARTY", "Indemnisation d'un tiers",
            "FINE", "Amende", "OTHER", "Autre coût");

    @Override
    public Mono<Expense> createIncidentCost(IncidentCostCommand cmd) {
        if (cmd.amount() == null || cmd.amount().signum() <= 0) {
            return Mono.error(BudgetException.expenseAmountRequired());
        }
        if (cmd.receiptUrl() == null || cmd.receiptUrl().isBlank()) {
            return Mono.error(BudgetException.receiptRequired());
        }
        String label = INCIDENT_COST_LABELS.getOrDefault(cmd.nature(), "Autre coût");
        return vehiclePersistence.getLocalDataById(cmd.vehicleId())
                .switchIfEmpty(Mono.error(BudgetException.vehicleNotFound(cmd.vehicleId())))
                .flatMap(vehicle -> fleetManagerOf(vehicle.fleetId()).flatMap(managerId -> (cmd.driverId() != null ? fullName(cmd.driverId()) : Mono.just(""))
                        .flatMap(driverName -> {
                            Expense expense = new Expense(
                                    null,
                                    "FINE".equals(cmd.nature()) ? Expense.ExpenseType.FINE : Expense.ExpenseType.INCIDENT,
                                    cmd.amount(),
                                    "Incident : " + label + (cmd.description() != null && !cmd.description().isBlank() ? " · " + cmd.description().trim() : ""),
                                    LocalDateTime.now(),
                                    Expense.SourceType.MANUAL,
                                    cmd.incidentId(),
                                    cmd.vehicleId(),
                                    vehicle.licensePlate(),
                                    vehicle.fleetId(),
                                    managerId,
                                    cmd.driverId(),
                                    driverName.isBlank() ? null : driverName,
                                    null);
                            expense.setCreatedBy(cmd.createdBy());
                            expense.attachReceipt(cmd.receiptUrl(), cmd.receiptName(), cmd.receiptMime());
                            return evaluateApproval(expense).flatMap(expensePersistence::save)
                                    .flatMap(saved -> notifications.expenseCreated(saved, cmd.createdBy()).thenReturn(saved));
                        })));
    }

    /** incidents.cost suit la somme des coûts réels approuvés (rapports, KPI). */
    private Mono<Void> syncIncidentCost(UUID sourceId) {
        if (sourceId == null) return Mono.empty();
        return db.sql("""
                UPDATE fleet.incidents i SET cost = (SELECT SUM(e.amount) FROM fleet.expenses e
                                                     WHERE e.source_id = i.id AND e.status = 'APPROVED')
                WHERE i.id = :id
                """).bind("id", sourceId).fetch().rowsUpdated().then();
    }

    /** Ajoute (ou remplace) le justificatif d'une dépense manuelle en attente. */
    @Override
    public Mono<Expense> attachReceipt(UUID expenseId, String url, String name, String mime) {
        if (url == null || url.isBlank()) {
            return Mono.error(BudgetException.receiptRequired());
        }
        return expensePersistence.findById(expenseId)
                .switchIfEmpty(Mono.error(BudgetException.expenseNotFound(expenseId)))
                .flatMap(expense -> {
                    if (!expense.isManualAndPending()) {
                        return Mono.error(BudgetException.expenseNotEditable(expenseId));
                    }
                    expense.attachReceipt(url, name, mime);
                    return expensePersistence.save(expense);
                });
    }

    /** Gestionnaire de la flotte du véhicule : c'est à lui que la dépense est rattachée. */
    private Mono<UUID> fleetManagerOf(UUID fleetId) {
        return db.sql("SELECT manager_id FROM fleet.fleets WHERE id = :f")
                .bind("f", fleetId)
                .map((row, meta) -> row.get("manager_id", UUID.class))
                .one()
                .switchIfEmpty(Mono.error(BudgetException.fleetNotFound(fleetId)));
    }

    private Mono<Boolean> isDriver(UUID userId) {
        if (userId == null) return Mono.just(false);
        return driverPersistence.findById(userId).hasElement();
    }

    private Mono<String> fullName(UUID userId) {
        return db.sql("SELECT TRIM(COALESCE(first_name, '') || ' ' || COALESCE(last_name, '')) AS n FROM fleet.users WHERE id = :u")
                .bind("u", userId)
                .map((row, meta) -> row.get("n", String.class))
                .one()
                .defaultIfEmpty("");
    }

    // ── Niveau de validation ─────────────────────────────────────────────────

    private record ApprovalNeeds(boolean enteredByStaff, boolean aboveThreshold, BigDecimal threshold, boolean overBudget) {
        boolean admin() { return enteredByStaff || aboveThreshold || overBudget; }

        String reason() {
            java.util.List<String> parts = new java.util.ArrayList<>();
            if (enteredByStaff) parts.add("saisie par un gestionnaire");
            if (aboveThreshold) parts.add("montant supérieur au seuil de " + threshold.stripTrailingZeros().toPlainString() + " XAF");
            if (overBudget) parts.add("dépassement du budget du mois");
            return String.join(", ", parts);
        }
    }

    private Mono<ApprovalNeeds> approvalNeeds(Expense e) {
        Mono<BigDecimal> threshold = db.sql("""
                        SELECT c.expense_approval_threshold AS t FROM fleet.fleets f
                        JOIN fleet.companies c ON c.id = f.company_id WHERE f.id = :f
                        """)
                .bind("f", e.getFleetId())
                .map((row, meta) -> row.get("t", BigDecimal.class))
                .one()
                .defaultIfEmpty(new BigDecimal("100000"));
        Mono<Boolean> staff = e.getCreatedBy() == null
                ? Mono.just(false)
                : db.sql("""
                        SELECT EXISTS (SELECT 1 FROM fleet.fleet_managers WHERE user_id = :u)
                            OR EXISTS (SELECT 1 FROM fleet.companies WHERE id = :u) AS staff
                        """)
                        .bind("u", e.getCreatedBy())
                        .map((row, meta) -> Boolean.TRUE.equals(row.get("staff", Boolean.class)))
                        .one()
                        .defaultIfEmpty(false);
        return Mono.zip(threshold, staff, wouldExceedBudget(e))
                .map(t -> new ApprovalNeeds(t.getT2(), e.getAmount().compareTo(t.getT1()) > 0, t.getT1(), t.getT3()));
    }

    /** Calcule et applique le niveau de validation exigé et l'indicateur de dépassement. */
    private Mono<Expense> evaluateApproval(Expense e) {
        return approvalNeeds(e).map(needs -> {
            e.setApprovalLevel(needs.admin() ? Expense.ApprovalLevel.ADMIN : Expense.ApprovalLevel.MANAGER);
            e.setOverBudget(needs.overBudget());
            return e;
        });
    }

    /** La dépense (si elle était approuvée) ferait-elle dépasser le budget flotte ou véhicule du mois ? */
    private Mono<Boolean> wouldExceedBudget(Expense e) {
        LocalDate month = e.getExpenseDate().toLocalDate().withDayOfMonth(1);
        LocalDateTime start = month.atStartOfDay();
        LocalDateTime end = month.plusMonths(1).atStartOfDay();
        BigDecimal extra = e.getStatus() == Expense.ExpenseStatus.APPROVED ? BigDecimal.ZERO : e.getAmount();
        Mono<Boolean> fleet = budgetPersistence.findByEntityAndMonth(Budget.BudgetScope.FLEET, e.getFleetId(), month)
                .flatMap(b -> expensePersistence.getTotalApprovedByFleetAndMonth(e.getFleetId(), start, end)
                        .defaultIfEmpty(BigDecimal.ZERO)
                        .map(total -> total.add(extra).compareTo(b.getAmount()) > 0))
                .defaultIfEmpty(false);
        Mono<Boolean> vehicle = budgetPersistence.findByEntityAndMonth(Budget.BudgetScope.VEHICLE, e.getVehicleId(), month)
                .flatMap(b -> expensePersistence.getTotalApprovedByVehicleAndMonth(e.getVehicleId(), start, end)
                        .defaultIfEmpty(BigDecimal.ZERO)
                        .map(total -> total.add(extra).compareTo(b.getAmount()) > 0))
                .defaultIfEmpty(false);
        return Mono.zip(fleet, vehicle).map(t -> t.getT1() || t.getT2());
    }

    /** L'acteur est-il l'administrateur de l'entreprise propriétaire de la flotte ? */
    private Mono<Boolean> isCompanyAdminOfFleet(UUID actorId, UUID fleetId) {
        return companyScope.isCompanyAdmin(actorId).flatMap(admin -> !Boolean.TRUE.equals(admin)
                ? Mono.just(false)
                : Mono.zip(companyScope.resolveCompanyId(actorId),
                                db.sql("SELECT company_id FROM fleet.fleets WHERE id = :f").bind("f", fleetId)
                                        .map((row, meta) -> row.get("company_id", UUID.class)).one())
                        .map(t -> t.getT1().equals(t.getT2()))
                        .defaultIfEmpty(false));
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

        return evaluateApproval(expense)
                .flatMap(expensePersistence::save)
                .flatMap(saved -> {
                    log.info("✅ Dépense auto [{}] créée pour véhicule {} : montant: {} FCFA",
                            type, vehicleId, amount);
                    return refreshBudgets(fleetId, vehicleId, saved.getExpenseDate())
                            .then(notifications.expenseCreated(saved, null))
                            .thenReturn(saved);
                });
    }

    /**
     * Les dépenses FUEL / MAINTENANCE / INCIDENT sont générées depuis leur opération
     * (conception du module Dépenses). createAutoExpense existait mais n'était jamais
     * appelée : budgets et page Dépenses ignoraient le carburant, les maintenances et
     * les incidents. La dépense suit désormais le coût de son opération.
     */
    @Override
    public Mono<Void> syncAutoExpense(Expense.ExpenseType type, UUID sourceId, BigDecimal amount,
                                      String description, UUID vehicleId, UUID driverId) {
        boolean positive = amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
        return expensePersistence.findBySourceId(sourceId)
                .filter(e -> e.getSourceType() == Expense.SourceType.AUTO)
                .next()
                .flatMap(existing -> {
                    if (!positive) {
                        return expensePersistence.deleteById(existing.getId())
                                .then(refreshBudgets(existing.getFleetId(), existing.getVehicleId(), existing.getExpenseDate()))
                                .thenReturn(true);
                    }
                    if (existing.getAmount().compareTo(amount) == 0) {
                        return Mono.just(true);
                    }
                    existing.syncWithSource(amount);
                    return expensePersistence.save(existing)
                            .flatMap(saved -> refreshBudgets(saved.getFleetId(), saved.getVehicleId(), saved.getExpenseDate()))
                            .thenReturn(true);
                })
                .switchIfEmpty(Mono.defer(() -> !positive
                        ? Mono.just(false)
                        : vehiclePersistence.getLocalDataById(vehicleId)
                                .flatMap(v -> createAutoExpense(type, amount, description, sourceId,
                                        vehicleId, v.licensePlate(), v.fleetId(), v.managerId(),
                                        driverId, null))
                                .thenReturn(true)))
                .then();
    }

    @Override
    public Mono<Void> syncDeclaredAutoExpense(Expense.ExpenseType type, UUID sourceId, BigDecimal amount, String description,
                                              UUID vehicleId, UUID driverId, String receiptUrl, String receiptName,
                                              String receiptMime) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return syncAutoExpense(type, sourceId, amount, description, vehicleId, driverId);
        }
        return expensePersistence.findBySourceId(sourceId)
                .filter(e -> e.getSourceType() == Expense.SourceType.AUTO)
                .hasElements()
                .flatMap(exists -> exists
                        ? syncAutoExpense(type, sourceId, amount, description, vehicleId, driverId)
                        : vehiclePersistence.getLocalDataById(vehicleId)
                                .flatMap(v -> fullName(driverId).flatMap(name -> {
                                    Expense expense = new Expense(null, type, amount, description, LocalDateTime.now(),
                                            Expense.SourceType.AUTO, sourceId, vehicleId, v.licensePlate(), v.fleetId(),
                                            v.managerId(), driverId, name.isBlank() ? null : name, null);
                                    expense.requireValidation();
                                    expense.setCreatedBy(driverId);
                                    if (receiptUrl != null && !receiptUrl.isBlank()) {
                                        expense.attachReceipt(receiptUrl, receiptName, receiptMime);
                                    }
                                    return evaluateApproval(expense).flatMap(expensePersistence::save)
                                            .flatMap(saved -> notifications.expenseCreated(saved, driverId));
                                }))
                                .then());
    }

    @Override
    public Mono<Void> deleteAutoExpenses(UUID sourceId) {
        return expensePersistence.findBySourceId(sourceId)
                .filter(e -> e.getSourceType() == Expense.SourceType.AUTO)
                .concatMap(e -> expensePersistence.deleteById(e.getId())
                        .then(refreshBudgets(e.getFleetId(), e.getVehicleId(), e.getExpenseDate())))
                .then();
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

    /**
     * Validation à deux niveaux : justificatif exigé pour une dépense manuelle ; personne ne
     * valide sa propre dépense ; le niveau est réévalué au moment de la validation (le budget a pu
     * se remplir entre-temps) et une dépense de niveau ADMIN n'est approuvée que par
     * l'administrateur de l'entreprise.
     */
    @Override
    public Mono<Expense> approve(UUID expenseId, UUID managerId) {
        return expensePersistence.findById(expenseId)
                .switchIfEmpty(Mono.error(BudgetException.expenseNotFound(expenseId)))
                .flatMap(expense -> {
                    if (expense.getStatus() != Expense.ExpenseStatus.PENDING) {
                        return Mono.error(BudgetException.expenseAlreadyValidated(expenseId));
                    }
                    if (expense.getSourceType() == Expense.SourceType.MANUAL && !expense.hasReceipt()) {
                        return Mono.error(BudgetException.receiptRequired());
                    }
                    if (managerId.equals(expense.getCreatedBy())) {
                        return Mono.error(BudgetException.selfApproval());
                    }
                    return Mono.zip(approvalNeeds(expense), isCompanyAdminOfFleet(managerId, expense.getFleetId()))
                            .flatMap(t -> {
                                ApprovalNeeds needs = t.getT1();
                                boolean newlyEscalated = needs.admin() && expense.getApprovalLevel() != Expense.ApprovalLevel.ADMIN;
                                expense.setApprovalLevel(needs.admin() ? Expense.ApprovalLevel.ADMIN : Expense.ApprovalLevel.MANAGER);
                                expense.setOverBudget(needs.overBudget());
                                if (needs.admin() && !Boolean.TRUE.equals(t.getT2())) {
                                    // Escalade : la dépense reste en attente, désormais chez l'administrateur
                                    // (prévenu la première fois que la dépense passe à son niveau).
                                    return expensePersistence.save(expense)
                                            .flatMap(saved -> newlyEscalated
                                                    ? notifications.expenseEscalated(saved, managerId, needs.reason())
                                                    : Mono.<Void>empty())
                                            .then(Mono.<Expense>error(BudgetException.adminApprovalRequired(needs.reason())));
                                }
                                expense.approve(managerId);
                                return expensePersistence.save(expense)
                                        .flatMap(saved -> refreshBudgets(saved.getFleetId(), saved.getVehicleId(),
                                                saved.getExpenseDate()).thenReturn(saved))
                                        .flatMap(saved -> syncIncidentCost(saved.getSourceId()).thenReturn(saved))
                                        .flatMap(saved -> notifications.expenseDecided(saved, managerId).thenReturn(saved))
                                        .doOnSuccess(saved -> log.info("✅ Dépense {} approuvée par {}", expenseId, managerId));
                            });
                });
    }

    @Override
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
                    if (cmd.managerId().equals(expense.getCreatedBy())) {
                        return Mono.error(BudgetException.selfApproval());
                    }
                    return isCompanyAdminOfFleet(cmd.managerId(), expense.getFleetId()).flatMap(admin -> {
                        if (expense.getApprovalLevel() == Expense.ApprovalLevel.ADMIN && !Boolean.TRUE.equals(admin)) {
                            return Mono.<Expense>error(BudgetException.adminApprovalRequired("niveau administrateur"));
                        }
                        expense.reject(cmd.managerId(), cmd.rejectionReason());
                        return expensePersistence.save(expense)
                                .flatMap(saved -> notifications.expenseDecided(saved, cmd.managerId()).thenReturn(saved))
                                .doOnSuccess(saved -> log.info("❌ Dépense {} rejetée par {} : motif: {}",
                                        cmd.expenseId(), cmd.managerId(), cmd.rejectionReason()));
                    });
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
                    return evaluateApproval(expense).flatMap(expensePersistence::save);
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
                    // Une dépense approuvée fait partie des comptes du mois (budget consommé).
                    if (expense.getStatus() == Expense.ExpenseStatus.APPROVED) {
                        return Mono.error(BudgetException.approvedNotDeletable());
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
     * Recalcule (dans la même chaîne, donc la même transaction) le budget flotte et le budget
     * véhicule du mois de la dépense. Avant, ce recalcul partait « en arrière-plan » sans garantie :
     * des budgets affichaient 255 000 consommés pour 2 500 réellement dépensés.
     */
    private Mono<Void> refreshBudgets(UUID fleetId, UUID vehicleId, LocalDateTime expenseDate) {
        if (fleetId == null || expenseDate == null) return Mono.empty();
        LocalDate month = expenseDate.toLocalDate().withDayOfMonth(1);
        Mono<Void> fleet = budgetPersistence.findByEntityAndMonth(Budget.BudgetScope.FLEET, fleetId, month)
                .flatMap(budget -> recalcAndSaveBudget(budget, fleetId, null, month))
                .then();
        Mono<Void> vehicle = vehicleId == null ? Mono.empty()
                : budgetPersistence.findByEntityAndMonth(Budget.BudgetScope.VEHICLE, vehicleId, month)
                        .flatMap(budget -> recalcAndSaveBudget(budget, null, vehicleId, month))
                        .then();
        return fleet.then(vehicle);
    }

    private Mono<Budget> recalcAndSaveBudget(Budget budget, UUID fleetId, UUID vehicleId, LocalDate month) {
        LocalDateTime monthStart = month.atStartOfDay();
        LocalDateTime monthEnd   = month.plusMonths(1).atStartOfDay();

        Mono<BigDecimal> totalMono = (fleetId != null)
                ? expensePersistence.getTotalApprovedByFleetAndMonth(fleetId, monthStart, monthEnd)
                : expensePersistence.getTotalApprovedByVehicleAndMonth(vehicleId, monthStart, monthEnd);

        return totalMono.defaultIfEmpty(BigDecimal.ZERO)
                .flatMap(total -> {
                    Budget.AlertLevel previousLevel = budget.getAlertLevel();
                    budget.updateConsumed(total);
                    return budgetPersistence.save(budget)
                            .flatMap(saved -> budgetJournal.recordLevelChange(saved, previousLevel, null).thenReturn(saved))
                            .doOnSuccess(b -> log.debug(
                                    "Budget {} recalculé : consommé: {} / {} FCFA ({}%)",
                                    b.getId(), b.getConsumed(), b.getAmount(), b.consumptionRate()
                            ));
                });
    }
}
