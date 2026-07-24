package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.model.SubscriptionPlan;
import com.yowyob.fleet.domain.ports.in.ManageSubscriptionPlanUseCase;
import com.yowyob.fleet.domain.ports.out.ExternalActorPort;
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
    private final SubscriptionRegistrationService registrationService;
    private final ExternalActorPort externalActorPort;

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
                       fm.company_name, fm.requested_plan_id AS plan_id,
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
            .then(cmd.planId() != null ? assignPlanToManager(cmd.managerId(), cmd.planId()) : Mono.empty())
            // Le gestionnaire s'est inscrit via /public/register-manager, qui crée le compte
            // Kernel mais ne lui attribue aucun rôle (pas de scope organisation à ce stade).
            // Sans cette assignation, le compte approuvé ne peut pas se connecter avec les
            // permissions manager (JWT sans ROLE_FLEET_MANAGER). Best-effort : ne doit pas
            // faire échouer l'approbation en base si le Kernel est indisponible.
            .then(externalActorPort.assignPlatformRole(cmd.managerId(), "FLEET_MANAGER")
                    .onErrorResume(e -> {
                        log.warn("⚠️ Assignation rôle FLEET_MANAGER ignorée pour {} : {}",
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
