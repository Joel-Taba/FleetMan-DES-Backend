package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.model.SubscriptionPlan;
import com.yowyob.fleet.domain.ports.in.ManageSubscriptionPlanUseCase;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.SubscriptionPlanEntity;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.ActiveSubscriptionDto;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.PendingSubscriptionDto;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.SubscriptionHistoryDto;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.PlanFeatureEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.FleetManagerR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.PlanFeatureR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.SubscriptionPlanR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.UserLocalR2dbcRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionPlanService implements ManageSubscriptionPlanUseCase {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionPlanService.class);

    private final SubscriptionPlanR2dbcRepository planRepo;
    private final PlanFeatureR2dbcRepository featureRepo;
    private final UserLocalR2dbcRepository userRepo;
    private final FleetManagerR2dbcRepository managerRepo;
    private final DatabaseClient db;
    private final SubscriptionRegistrationService registrationService;

    @Override
    public Mono<SubscriptionPlan> createPlan(CreatePlanCommand cmd) {
        SubscriptionPlanEntity e = new SubscriptionPlanEntity();
        e.setId(UUID.randomUUID());
        e.setName(cmd.name()); e.setDescription(cmd.description());
        e.setMaxFleets(cmd.maxFleets()); e.setMaxVehicles(cmd.maxVehicles()); e.setMaxDrivers(cmd.maxDrivers());
        e.setMonthlyPrice(cmd.monthlyPrice()); e.setAnnualPrice(cmd.annualPrice());
        e.setCurrency(cmd.currency() != null ? cmd.currency() : "XAF");
        e.setFeatures(cmd.features()); e.setActive(true);
        e.setCreatedAt(Instant.now()); e.setUpdatedAt(Instant.now());
        e.setNew(true);
        return planRepo.save(e).map(this::toDomain);
    }

    @Override
    public Flux<SubscriptionPlan> listPlans() {
        return planRepo.findAll().map(this::toDomain);
    }

    @Override
    public Mono<SubscriptionPlan> getPlan(UUID id) {
        return planRepo.findById(id)
            .switchIfEmpty(Mono.error(new RuntimeException("Plan introuvable: " + id)))
            .map(this::toDomain);
    }

    @Override
    public Mono<SubscriptionPlan> updatePlan(UUID id, UpdatePlanCommand cmd) {
        return planRepo.findById(id)
            .switchIfEmpty(Mono.error(new RuntimeException("Plan introuvable: " + id)))
            .flatMap(e -> {
                e.setName(cmd.name()); e.setDescription(cmd.description());
                e.setMaxFleets(cmd.maxFleets()); e.setMaxVehicles(cmd.maxVehicles()); e.setMaxDrivers(cmd.maxDrivers());
                e.setMonthlyPrice(cmd.monthlyPrice()); e.setAnnualPrice(cmd.annualPrice());
                e.setFeatures(cmd.features()); e.setUpdatedAt(Instant.now());
                e.setNew(false);
                return planRepo.save(e);
            }).map(this::toDomain);
    }

    @Override
    public Mono<Void> deactivatePlan(UUID id) {
        return planRepo.findById(id)
            .flatMap(e -> { e.setActive(false); e.setUpdatedAt(Instant.now()); e.setNew(false); return planRepo.save(e); })
            .then();
    }

    @Override
    public Mono<Void> assignPlanToManager(UUID managerId, UUID planId) {
        LocalDate start = LocalDate.now();
        LocalDate end = start.plusYears(1);
        return db.sql("""
                UPDATE fleet.fleet_managers
                SET plan_id = :planId,
                    subscription_status = 'ACTIVE',
                    subscription_start = :start,
                    subscription_end = :end
                WHERE user_id = :managerId
                """)
                .bind("planId", planId)
                .bind("start", start)
                .bind("end", end)
                .bind("managerId", managerId)
                .fetch()
                .rowsUpdated()
                .then(userRepo.findById(managerId)
                        .flatMap(u -> {
                            u.setActive(true);
                            u.setNewRecord(false);
                            return userRepo.save(u);
                        })
                        .then());
    }

    // ── Workflow approbation B3 ───────────────────────────────────────────────

    @Override
    public Flux<PendingSubscriptionDto> listPendingSubscriptions() {
        return db.sql("""
                SELECT u.id, u.username, u.email, u.first_name, u.last_name,
                       fm.company_name, fm.plan_id,
                       COALESCE(u.approved_at, NOW()) AS created_at
                FROM fleet.users u
                LEFT JOIN fleet.fleet_managers fm ON fm.user_id = u.id
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
                       fm.company_name, u.approved_at AS processed_at,
                       u.approval_status AS status, sp.name AS plan_name,
                       u.rejection_reason,
                       COALESCE(approver.username, approver.email) AS processed_by
                FROM fleet.users u
                LEFT JOIN fleet.fleet_managers fm ON fm.user_id = u.id
                LEFT JOIN fleet.subscription_plans sp ON sp.id = fm.plan_id
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
            .then(cmd.planId() != null ? assignPlanToManager(cmd.managerId(), cmd.planId()) : Mono.empty());
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
    public Mono<Void> replacePlanFeatures(UUID planId, List<ManageSubscriptionPlanUseCase.PlanFeatureCommand> features) {
        return featureRepo.deleteByPlanId(planId)
                .thenMany(Flux.fromIterable(features)
                        .flatMap(f -> {
                            PlanFeatureEntity e = new PlanFeatureEntity();
                            e.setId(UUID.randomUUID());
                            e.setPlanId(planId);
                            e.setFeatureKey(f.key());
                            e.setFeatureLabel(f.label());
                            e.setEnabled(f.enabled());
                            e.setNew(true);
                            return featureRepo.save(e);
                        }))
                .then();
    }

    @Override
    public Flux<ActiveSubscriptionDto> listActiveSubscriptions() {
        return db.sql("""
                SELECT fm.user_id, fm.company_name, u.email, sp.name AS plan_name,
                       fm.subscription_status, fm.subscription_start, fm.subscription_end
                FROM fleet.fleet_managers fm
                JOIN fleet.users u ON u.id = fm.user_id
                LEFT JOIN fleet.subscription_plans sp ON sp.id = fm.plan_id
                WHERE fm.subscription_status IN ('ACTIVE', 'EXPIRED')
                ORDER BY fm.subscription_end ASC NULLS LAST
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
                            row.get("plan_name") != null ? row.get("plan_name").toString() : "—",
                            row.get("subscription_status").toString(),
                            row.get("subscription_start") != null
                                    ? LocalDate.parse(row.get("subscription_start").toString().substring(0, 10)) : null,
                            end,
                            days
                    );
                });
    }

    private SubscriptionPlan toDomain(SubscriptionPlanEntity e) {
        return new SubscriptionPlan(e.getId(), e.getName(), e.getDescription(),
            e.getMaxFleets(), e.getMaxVehicles(), e.getMaxDrivers(),
            e.getMonthlyPrice(), e.getAnnualPrice(), e.getCurrency(),
            e.getFeatures(), e.isActive(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
