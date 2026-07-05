package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.model.SubscriptionPlan;
import com.yowyob.fleet.domain.ports.in.ManageSubscriptionPlanUseCase;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.SubscriptionPlanEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.FleetManagerR2dbcRepository;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionPlanService implements ManageSubscriptionPlanUseCase {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionPlanService.class);

    private final SubscriptionPlanR2dbcRepository planRepo;
    private final UserLocalR2dbcRepository userRepo;
    private final FleetManagerR2dbcRepository managerRepo;
    private final DatabaseClient db;

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
        return db.sql("UPDATE fleet.fleet_managers SET plan_id = :planId, subscription_status = 'ACTIVE' WHERE user_id = :managerId")
            .bind("planId", planId).bind("managerId", managerId)
            .fetch().rowsUpdated().then();
    }

    // ── Workflow approbation B3 ───────────────────────────────────────────────

    @Override
    public Flux<Object> listPendingSubscriptions() {
        return db.sql("SELECT u.id, u.username, u.email, u.first_name, u.last_name, u.approval_status, fm.company_name " +
                      "FROM fleet.users u LEFT JOIN fleet.fleet_managers fm ON fm.user_id = u.id " +
                      "WHERE u.approval_status = 'PENDING'")
            .fetch().all()
            .map(row -> (Object) row);
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
                return userRepo.save(u);
            }).then();
    }

    private SubscriptionPlan toDomain(SubscriptionPlanEntity e) {
        return new SubscriptionPlan(e.getId(), e.getName(), e.getDescription(),
            e.getMaxFleets(), e.getMaxVehicles(), e.getMaxDrivers(),
            e.getMonthlyPrice(), e.getAnnualPrice(), e.getCurrency(),
            e.getFeatures(), e.isActive(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
