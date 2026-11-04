package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.SubscriptionException;
import com.yowyob.fleet.domain.model.BillingCycle;
import com.yowyob.fleet.domain.model.PlanFeature;
import com.yowyob.fleet.domain.model.SubscriptionPlan;
import com.yowyob.fleet.domain.model.SubscriptionPlanRules;
import com.yowyob.fleet.domain.ports.in.ManageSubscriptionPlanUseCase;
import com.yowyob.fleet.domain.ports.out.ExternalActorPort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.SubscriptionPlanEntity;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.ActiveSubscriptionDto;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.PendingSubscriptionDto;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.SubscriptionHistoryDto;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.PlanFeatureEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.DriverR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.FleetManagerR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.FleetR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.VehicleLocalR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.PlanFeatureR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.SubscriptionPlanR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.UserLocalR2dbcRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubscriptionPlanService implements ManageSubscriptionPlanUseCase {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionPlanService.class);

    private final SubscriptionPlanR2dbcRepository planRepo;
    private final PlanFeatureR2dbcRepository featureRepo;
    private final UserLocalR2dbcRepository userRepo;
    private final FleetManagerR2dbcRepository managerRepo;
    private final DatabaseClient db;
    private final SubscriptionJournal journal;
    private final SubscriptionRegistrationService registrationService;
    private final ExternalActorPort externalActorPort;
    private final PlanLimitGuard planLimitGuard;

    @Override
    @Transactional
    public Mono<SubscriptionPlan> createPlan(CreatePlanCommand cmd) {
        String currency = normalizeCurrency(cmd.currency());
        return Mono.fromRunnable(() -> SubscriptionPlanRules.validate(cmd.name(), cmd.maxFleets(),
                        cmd.maxVehicles(), cmd.maxDrivers(), cmd.monthlyPrice(), cmd.annualPrice(), currency))
                .then(assertNameAvailable(cmd.name(), null))
                .then(Mono.defer(() -> {
                    SubscriptionPlanEntity e = new SubscriptionPlanEntity();
                    e.setId(UUID.randomUUID());
                    applyCommand(e, cmd.name(), cmd.description(), cmd.maxFleets(), cmd.maxVehicles(),
                            cmd.maxDrivers(), cmd.monthlyPrice(), cmd.annualPrice(), currency,
                            cmd.sortOrder(), cmd.recommended());
                    e.setActive(true);
                    e.setCreatedAt(Instant.now());
                    e.setNew(true);
                    return planRepo.save(e);
                }))
                .flatMap(saved -> saveFeatures(saved, cmd.technicalFeatures()))
                .flatMap(this::withFeatures);
    }

    @Override
    public Flux<SubscriptionPlan> listPlans() {
        return planRepo.findAll()
                .flatMap(this::withFeatures)
                .sort(java.util.Comparator.comparing(SubscriptionPlan::getSortOrder)
                        .thenComparing(p -> p.getMonthlyPrice() != null ? p.getMonthlyPrice() : java.math.BigDecimal.ZERO));
    }

    @Override
    public Mono<SubscriptionPlan> getPlan(UUID id) {
        return planRepo.findById(id)
            .switchIfEmpty(Mono.error(SubscriptionException.planNotFound()))
            .flatMap(this::withFeatures);
    }

    @Override
    @Transactional
    public Mono<SubscriptionPlan> updatePlan(UUID id, UpdatePlanCommand cmd) {
        return planRepo.findById(id)
            .switchIfEmpty(Mono.error(SubscriptionException.planNotFound()))
            .flatMap(e -> {
                String currency = cmd.currency() != null ? normalizeCurrency(cmd.currency()) : e.getCurrency();
                SubscriptionPlanRules.validate(cmd.name(), cmd.maxFleets(), cmd.maxVehicles(), cmd.maxDrivers(),
                        cmd.monthlyPrice(), cmd.annualPrice(), currency);
                return assertNameAvailable(cmd.name(), id).then(Mono.defer(() -> {
                    applyCommand(e, cmd.name(), cmd.description(), cmd.maxFleets(), cmd.maxVehicles(),
                            cmd.maxDrivers(), cmd.monthlyPrice(), cmd.annualPrice(), currency,
                            cmd.sortOrder() != null ? cmd.sortOrder() : e.getSortOrder(),
                            cmd.recommended() != null ? cmd.recommended() : e.isRecommended());
                    e.setNew(false);
                    return planRepo.save(e);
                }));
            })
            .flatMap(saved -> saveFeatures(saved, cmd.technicalFeatures()))
            .flatMap(this::withFeatures);
    }

    @Override
    public Mono<Void> deactivatePlan(UUID id) {
        return planRepo.findById(id)
            .switchIfEmpty(Mono.error(SubscriptionException.planNotFound()))
            .flatMap(e -> { e.setActive(false); e.setUpdatedAt(Instant.now()); e.setNew(false); return planRepo.save(e); })
            .then();
    }

    /**
     * Affectation directe par l'administration (sans paiement) : le plan doit être proposé et
     * couvrir l'usage actuel du gestionnaire ; la durée dépend du cycle (plan gratuit : sans fin).
     */
    @Override
    public Mono<Void> assignPlanToManager(UUID managerId, UUID planId, BillingCycle billingCycle) {
        return planRepo.findById(planId)
                .switchIfEmpty(Mono.error(SubscriptionException.planNotFound()))
                .flatMap(plan -> {
                    if (!plan.isActive()) {
                        return Mono.error(SubscriptionException.planInactive(plan.getName()));
                    }
                    BillingCycle cycle = billingCycle != null ? billingCycle : BillingCycle.MONTHLY;
                    if (SubscriptionPlanRules.priceFor(plan.getMonthlyPrice(), plan.getAnnualPrice(), cycle) == null) {
                        return Mono.error(SubscriptionException.billingCycleUnavailable(plan.getName()));
                    }
                    return assertUsageFits(managerId, plan)
                            .then(activateSubscription(managerId, plan, cycle, LocalDate.now()))
                            .then();
                });
    }

    /** Refuse un plan dont les limites sont inférieures à l'usage actuel de l'entreprise. */
    public Mono<Void> assertUsageFits(UUID companyId, SubscriptionPlanEntity plan) {
        return planLimitGuard.getCompanySubscription(companyId)
                .flatMap(c -> {
                    List<String> overflows = SubscriptionPlanRules.usageOverflows(plan.getMaxFleets(),
                            plan.getMaxVehicles(), plan.getMaxDrivers(),
                            c.currentFleets(), c.currentVehicles(), c.currentDrivers());
                    return overflows.isEmpty()
                            ? Mono.<Void>empty()
                            : Mono.error(SubscriptionException.usageExceedsPlan(plan.getName(), String.join(", ", overflows)));
                });
    }

    /**
     * Active (ou prolonge) l'abonnement : plan gratuit → sans date de fin ni cycle ; plan
     * payant → période d'un mois ou d'un an à partir de {@code start}. Réactive le compte.
     *
     * @return la fin de période appliquée (null pour un plan gratuit)
     */
    public Mono<LocalDate> activateSubscription(UUID managerId, SubscriptionPlanEntity plan,
                                                BillingCycle cycle, LocalDate start) {
        boolean free = plan.getMonthlyPrice() == null || plan.getMonthlyPrice().signum() == 0;
        LocalDate end = free ? null : cycle.periodEnd(start);
        var spec = db.sql("""
                UPDATE fleet.companies
                SET plan_id = :planId,
                    subscription_status = 'ACTIVE',
                    subscription_start = :start,
                    subscription_end = :end,
                    billing_cycle = :cycle,
                    updated_at = NOW()
                WHERE id = :managerId
                """)
                .bind("planId", plan.getId())
                .bind("start", start)
                .bind("managerId", managerId);
        spec = end != null ? spec.bind("end", end) : spec.bindNull("end", LocalDate.class);
        spec = free ? spec.bindNull("cycle", String.class) : spec.bind("cycle", cycle.name());
        return spec.fetch().rowsUpdated()
                .then(userRepo.findById(managerId)
                        .flatMap(u -> {
                            u.setActive(true);
                            u.setNewRecord(false);
                            return userRepo.save(u);
                        })
                        .then())
                .then(Mono.justOrEmpty(end));
    }

    private Mono<Void> assertNameAvailable(String name, UUID currentId) {
        return planRepo.findAll()
                .filter(p -> p.getName() != null && p.getName().trim().equalsIgnoreCase(name.trim()))
                .filter(p -> !p.getId().equals(currentId))
                .hasElements()
                .flatMap(taken -> Boolean.TRUE.equals(taken)
                        ? Mono.<Void>error(SubscriptionException.planNameTaken(name.trim()))
                        : Mono.<Void>empty());
    }

    private static void applyCommand(SubscriptionPlanEntity e, String name, String description,
                                     int maxFleets, int maxVehicles, int maxDrivers,
                                     java.math.BigDecimal monthlyPrice, java.math.BigDecimal annualPrice,
                                     String currency, Integer sortOrder, Boolean recommended) {
        java.math.BigDecimal monthly = monthlyPrice != null ? monthlyPrice : java.math.BigDecimal.ZERO;
        e.setName(name.trim());
        e.setDescription(description != null && !description.isBlank() ? description.trim() : null);
        e.setMaxFleets(maxFleets);
        e.setMaxVehicles(maxVehicles);
        e.setMaxDrivers(maxDrivers);
        e.setMonthlyPrice(monthly);
        e.setAnnualPrice(monthly.signum() == 0 ? null : annualPrice);
        e.setCurrency(currency);
        e.setSortOrder(sortOrder != null ? sortOrder : 0);
        e.setRecommended(Boolean.TRUE.equals(recommended));
        e.setUpdatedAt(Instant.now());
    }

    private static String normalizeCurrency(String currency) {
        return currency == null || currency.isBlank() ? "XAF" : currency.trim().toUpperCase();
    }

    /**
     * Enregistre les fonctionnalités (si fournies) et aligne le libellé marketing du plan sur
     * celles qui sont réellement activées : une seule source de vérité pour l'affichage et l'accès.
     */
    private Mono<SubscriptionPlanEntity> saveFeatures(SubscriptionPlanEntity plan,
                                                      List<ManageSubscriptionPlanUseCase.PlanFeatureCommand> features) {
        Mono<Void> replace = features == null || features.isEmpty()
                ? Mono.empty()
                : replacePlanFeatures(plan.getId(), features.stream()
                        .filter(f -> f.key() != null && PlanFeature.fromKey(f.key()).isPresent())
                        .map(f -> new ManageSubscriptionPlanUseCase.PlanFeatureCommand(
                                f.key(), PlanFeature.labelOf(f.key()), f.enabled()))
                        .toList());
        return replace
                .then(featureRepo.findByPlanId(plan.getId())
                        .filter(PlanFeatureEntity::isEnabled)
                        .map(f -> PlanFeature.labelOf(f.getFeatureKey()))
                        .sort()
                        .collectList())
                .flatMap(labels -> {
                    plan.setFeatures(labels.isEmpty() ? null : String.join(", ", labels));
                    plan.setNew(false);
                    return planRepo.save(plan);
                });
    }

    private Mono<SubscriptionPlan> withFeatures(SubscriptionPlanEntity e) {
        return featureRepo.findByPlanId(e.getId())
                .filter(PlanFeatureEntity::isEnabled)
                .map(PlanFeatureEntity::getFeatureKey)
                .collectList()
                .map(keys -> {
                    SubscriptionPlan plan = toDomain(e);
                    plan.setIncludedFeatures(keys);
                    return plan;
                });
    }

    // ── Workflow approbation B3 ───────────────────────────────────────────────

    @Override
    public Flux<PendingSubscriptionDto> listPendingSubscriptions() {
        return db.sql("""
                SELECT u.id, u.username, u.email, u.first_name, u.last_name,
                       c.name AS company_name, c.requested_plan_id AS plan_id,
                       COALESCE(c.created_at, NOW()) AS created_at
                FROM fleet.users u
                JOIN fleet.companies c ON c.id = u.id
                WHERE u.approval_status = 'PENDING'
                ORDER BY u.username
                """)
            .fetch().all()
            .map(row -> new PendingSubscriptionDto(
                    (UUID) row.get("id"),
                    str(row.get("username")),
                    str(row.get("email")),
                    str(row.get("first_name")),
                    str(row.get("last_name")),
                    row.get("company_name") != null ? row.get("company_name").toString() : null,
                    toInstant(row.get("created_at")),
                    null,
                    row.get("plan_id") instanceof UUID planId ? planId : null
            ));
    }

    @Override
    public Flux<SubscriptionHistoryDto> listSubscriptionHistory() {
        return db.sql("""
                SELECT u.id, u.username, u.email, u.first_name, u.last_name,
                       c.name AS company_name, u.approved_at AS processed_at,
                       u.approval_status AS status, sp.name AS plan_name,
                       u.rejection_reason,
                       COALESCE(approver.username, approver.email) AS processed_by
                FROM fleet.users u
                JOIN fleet.companies c ON c.id = u.id
                LEFT JOIN fleet.subscription_plans sp ON sp.id = c.plan_id
                LEFT JOIN fleet.users approver ON approver.id = u.approved_by
                WHERE u.approval_status IN ('APPROVED', 'REJECTED')
                  AND u.approved_at IS NOT NULL
                ORDER BY u.approved_at DESC
                """)
            .fetch().all()
            .map(row -> {
                Instant processedAt = toInstant(row.get("processed_at"));
                return new SubscriptionHistoryDto(
                        (UUID) row.get("id"),
                        str(row.get("username")),
                        str(row.get("email")),
                        str(row.get("first_name")),
                        str(row.get("last_name")),
                        row.get("company_name") != null ? row.get("company_name").toString() : null,
                        processedAt,
                        processedAt,
                        str(row.get("status")),
                        row.get("plan_name") != null ? row.get("plan_name").toString() : null,
                        row.get("rejection_reason") != null ? row.get("rejection_reason").toString() : null,
                        row.get("processed_by") != null ? row.get("processed_by").toString() : null
                );
            });
    }

    private static String str(Object value) {
        return value != null ? value.toString() : "";
    }

    private static Instant toInstant(Object value) {
        if (value == null) return Instant.now();
        if (value instanceof Instant i) return i;
        if (value instanceof java.time.OffsetDateTime odt) return odt.toInstant();
        if (value instanceof java.time.LocalDateTime ldt) {
            return ldt.atZone(java.time.ZoneId.systemDefault()).toInstant();
        }
        return Instant.parse(value.toString());
    }

    @Override
    public Mono<Void> approveSubscription(ApproveSubscriptionCommand cmd) {
        return userRepo.findById(cmd.managerId())
            .switchIfEmpty(Mono.error(new RuntimeException("Utilisateur introuvable: " + cmd.managerId())))
            .flatMap(u -> {
                u.setActive(true);
                u.setApprovalStatus("APPROVED");
                u.setApprovedBy(cmd.approvedBy());
                u.setApprovedAt(Instant.now());
                u.setNewRecord(false);
                return userRepo.save(u);
            })
            // Plan attribué : celui choisi, sinon celui demandé à l'inscription, sinon le premier
            // plan gratuit actif. Un compte approuvé sans plan aurait un accès illimité gratuit.
            .then(resolveApprovalPlan(cmd.managerId(), cmd.planId())
                    .flatMap(planId -> requestedBillingCycle(cmd.managerId())
                            .flatMap(requested -> assignPlanToManager(cmd.managerId(), planId,
                                    cmd.billingCycle() != null ? cmd.billingCycle() : requested))))
            .then(journal.log(SubscriptionJournal.Entry.of(cmd.managerId(), "REQUEST_APPROVED", cmd.approvedBy(), null)))
            // L'inscription publique crée le compte Kernel de l'administrateur de l'entreprise sans
            // rôle : il reçoit FLEET_ADMIN à l'approbation, puis crée ses gestionnaires. Best-effort :
            // ne doit pas faire échouer l'approbation en base si le Kernel est indisponible.
            .then(externalActorPort.assignPlatformRole(cmd.managerId(), "FLEET_ADMIN")
                    .onErrorResume(e -> {
                        log.warn("⚠️ Assignation rôle FLEET_ADMIN ignorée pour {} : {}",
                                cmd.managerId(), e.getMessage());
                        return Mono.empty();
                    }));
    }

    @Override
    public Mono<Void> rejectSubscription(RejectSubscriptionCommand cmd) {
        return userRepo.findById(cmd.managerId())
            .switchIfEmpty(Mono.error(new RuntimeException("Utilisateur introuvable: " + cmd.managerId())))
            .flatMap(u -> {
                u.setActive(false);
                u.setApprovalStatus("REJECTED");
                u.setRejectionReason(cmd.reason());
                u.setApprovedBy(cmd.rejectedBy());
                u.setApprovedAt(Instant.now());
                u.setNewRecord(false);
                String email = u.getEmail();
                String subject = cmd.subject() != null && !cmd.subject().isBlank()
                        ? cmd.subject() : "Votre demande d'inscription FleetMan a été rejetée";
                String body = cmd.message() != null && !cmd.message().isBlank()
                        ? cmd.message() : cmd.reason();
                return userRepo.save(u)
                        .then(journal.log(SubscriptionJournal.Entry.of(cmd.managerId(), "REQUEST_REJECTED", cmd.rejectedBy(), cmd.reason())))
                        .then(registrationService.sendRejectionEmail(email, subject, body));
            }).then();
    }

    @Override
    public Flux<ManageSubscriptionPlanUseCase.PlanFeatureCommand> getPlanFeatures(UUID planId) {
        return featureRepo.findByPlanId(planId)
                .map(f -> new ManageSubscriptionPlanUseCase.PlanFeatureCommand(
                        f.getFeatureKey(), f.getFeatureLabel(), f.isEnabled()));
    }

    @Override
    @Transactional
    public Mono<Void> replacePlanFeatures(UUID planId, List<ManageSubscriptionPlanUseCase.PlanFeatureCommand> features) {
        // Upsert ligne par ligne (au lieu d'un DELETE global suivi de INSERT concurrents) :
        // évite la violation de la contrainte unique (plan_id, feature_key) — via GlobalExceptionHandler,
        // DataIntegrityViolationException remonte en 400 — qui pouvait survenir si deux requêtes de
        // remplacement se chevauchaient (double clic, requête réseau relancée...).
        List<String> newKeys = features.stream()
                .map(ManageSubscriptionPlanUseCase.PlanFeatureCommand::key)
                .toList();

        return featureRepo.findByPlanId(planId)
                .collectList()
                .flatMap(existing -> {
                    Map<String, PlanFeatureEntity> existingByKey = existing.stream()
                            .collect(Collectors.toMap(PlanFeatureEntity::getFeatureKey, e -> e));

                    Flux<PlanFeatureEntity> upserts = Flux.fromIterable(features)
                            .concatMap(f -> {
                                PlanFeatureEntity e = existingByKey.get(f.key());
                                if (e == null) {
                                    e = new PlanFeatureEntity();
                                    e.setId(UUID.randomUUID());
                                    e.setPlanId(planId);
                                    e.setFeatureKey(f.key());
                                    e.setNew(true);
                                }
                                e.setFeatureLabel(f.label());
                                e.setEnabled(f.enabled());
                                return featureRepo.save(e);
                            });

                    List<UUID> staleIds = existing.stream()
                            .filter(e -> !newKeys.contains(e.getFeatureKey()))
                            .map(PlanFeatureEntity::getId)
                            .toList();
                    Mono<Void> deleteStale = staleIds.isEmpty()
                            ? Mono.empty()
                            : featureRepo.deleteAllById(staleIds);

                    return upserts.then(deleteStale);
                })
                .then();
    }

    @Override
    public Flux<ActiveSubscriptionDto> listActiveSubscriptions() {
        return db.sql("""
                SELECT c.id AS user_id, c.name AS company_name, u.email, sp.name AS plan_name,
                       c.subscription_status, c.subscription_start, c.subscription_end
                FROM fleet.companies c
                JOIN fleet.users u ON u.id = c.id
                LEFT JOIN fleet.subscription_plans sp ON sp.id = c.plan_id
                WHERE c.subscription_status IN ('ACTIVE', 'EXPIRED') AND u.deleted_at IS NULL
                ORDER BY c.subscription_end ASC NULLS LAST
                """)
                .fetch()
                .all()
                .map(row -> {
                    LocalDate end = row.get("subscription_end") != null
                            ? LocalDate.parse(row.get("subscription_end").toString().substring(0, 10)) : null;
                    long days = end != null ? ChronoUnit.DAYS.between(LocalDate.now(), end) : 999;
                    return new ActiveSubscriptionDto(
                            (UUID) row.get("user_id"),
                            row.get("company_name") != null ? row.get("company_name").toString() : "",
                            row.get("email") != null ? row.get("email").toString() : "",
                            row.get("plan_name") != null ? row.get("plan_name").toString() : "-",
                            row.get("subscription_status").toString(),
                            row.get("subscription_start") != null
                                    ? LocalDate.parse(row.get("subscription_start").toString().substring(0, 10)) : null,
                            end,
                            days
                    );
                });
    }

    private SubscriptionPlan toDomain(SubscriptionPlanEntity e) {
        SubscriptionPlan plan = new SubscriptionPlan(e.getId(), e.getName(), e.getDescription(),
            e.getMaxFleets(), e.getMaxVehicles(), e.getMaxDrivers(),
            e.getMonthlyPrice(), e.getAnnualPrice(), e.getCurrency(),
            e.getFeatures(), e.isActive(), e.getCreatedAt(), e.getUpdatedAt());
        plan.setSortOrder(e.getSortOrder());
        plan.setRecommended(e.isRecommended());
        return plan;
    }

    private Mono<UUID> resolveApprovalPlan(UUID managerId, UUID chosenPlanId) {
        if (chosenPlanId != null) {
            return Mono.just(chosenPlanId);
        }
        Mono<UUID> requested = db.sql("SELECT requested_plan_id FROM fleet.companies WHERE id = :id")
                .bind("id", managerId)
                .map((row, meta) -> java.util.Optional.ofNullable(row.get("requested_plan_id", UUID.class)))
                .one()
                .flatMap(Mono::justOrEmpty)
                .filterWhen(id -> planRepo.findById(id).map(SubscriptionPlanEntity::isActive).defaultIfEmpty(false));
        Mono<UUID> firstFree = planRepo.findAll()
                .filter(SubscriptionPlanEntity::isActive)
                .filter(p -> p.getMonthlyPrice() == null || p.getMonthlyPrice().signum() == 0)
                .sort(java.util.Comparator.comparingInt(SubscriptionPlanEntity::getSortOrder))
                .next()
                .map(SubscriptionPlanEntity::getId);
        return requested.switchIfEmpty(firstFree);
    }

    /** Cycle demandé à l'inscription (mensuel par défaut). */
    private Mono<BillingCycle> requestedBillingCycle(UUID managerId) {
        return db.sql("SELECT requested_billing_cycle FROM fleet.companies WHERE id = :id")
                .bind("id", managerId)
                .map((row, meta) -> java.util.Optional.ofNullable(row.get("requested_billing_cycle", String.class)))
                .one()
                .map(v -> BillingCycle.parseOrDefault(v.orElse(null), BillingCycle.MONTHLY))
                .defaultIfEmpty(BillingCycle.MONTHLY);
    }
}
