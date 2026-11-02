package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.model.BillingCycle;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.CompanyEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.PlatformAdminEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.SubscriptionPlanEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.CompanyR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.PlatformAdminR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.SubscriptionPlanR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.UUID;

/** Création des entreprises (l'administrateur et son entreprise partagent le même id). */
@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyR2dbcRepository companyRepo;
    private final PlatformAdminR2dbcRepository platformAdminRepo;
    private final SubscriptionPlanR2dbcRepository planRepo;

    /** Inscription publique : entreprise en attente de validation, avec le plan demandé. */
    public Mono<CompanyEntity> createPending(UUID adminId, String name, UUID requestedPlanId, BillingCycle cycle) {
        return ensureAdmin(adminId).then(Mono.defer(() -> {
            CompanyEntity c = base(adminId, name);
            c.setSubscriptionStatus("PENDING");
            c.setRequestedPlanId(requestedPlanId);
            c.setRequestedBillingCycle((cycle != null ? cycle : BillingCycle.MONTHLY).name());
            return companyRepo.save(c);
        }));
    }

    /** Entreprise créée par le super-admin : active d'emblée sur l'offre gratuite. */
    public Mono<CompanyEntity> createOnFreePlan(UUID adminId, String name) {
        return ensureAdmin(adminId)
                .then(firstFreePlan().map(p -> java.util.Optional.of(p.getId())).defaultIfEmpty(java.util.Optional.empty()))
                .flatMap(planId -> companyRepo.existsById(adminId).flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        return companyRepo.findById(adminId);
                    }
                    CompanyEntity c = base(adminId, name);
                    c.setPlanId(planId.orElse(null));
                    c.setSubscriptionStatus("ACTIVE");
                    c.setSubscriptionStart(LocalDate.now());
                    return companyRepo.save(c);
                }));
    }

    private Mono<Void> ensureAdmin(UUID adminId) {
        return platformAdminRepo.existsById(adminId)
                .flatMap(exists -> Boolean.TRUE.equals(exists)
                        ? Mono.<Void>empty()
                        : platformAdminRepo.save(new PlatformAdminEntity(adminId)).then());
    }

    private Mono<SubscriptionPlanEntity> firstFreePlan() {
        return planRepo.findAll()
                .filter(SubscriptionPlanEntity::isActive)
                .filter(p -> p.getMonthlyPrice() == null || p.getMonthlyPrice().signum() == 0)
                .sort(Comparator.comparingInt(SubscriptionPlanEntity::getSortOrder))
                .next();
    }

    private static CompanyEntity base(UUID adminId, String name) {
        CompanyEntity c = new CompanyEntity();
        c.setId(adminId);
        c.setName(name == null || name.isBlank() ? "Entreprise" : name.trim());
        c.setCreatedAt(Instant.now());
        c.setUpdatedAt(Instant.now());
        c.setNew(true);
        return c;
    }
}
