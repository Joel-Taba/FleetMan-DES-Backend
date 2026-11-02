package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.SubscriptionException;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.ManagerSubscriptionResponse;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.ManagerSubscriptionResponse.PlanFeatureDto;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.PlanFeatureR2dbcRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class PlanLimitGuardTest {

    @Mock DatabaseClient db;
    @Mock PlanFeatureR2dbcRepository featureRepo;
    @Mock CompanyScopeService companyScope;

    @Spy
    @InjectMocks
    PlanLimitGuard planLimitGuard;

    private UUID managerId;
    private ManagerSubscriptionResponse starterAtVehicleLimit;

    @BeforeEach
    void setUp() {
        managerId = UUID.randomUUID();
        starterAtVehicleLimit = new ManagerSubscriptionResponse(
                managerId,
                UUID.randomUUID(),
                "Starter",
                "ACTIVE",
                LocalDate.now().minusMonths(1),
                LocalDate.now().plusMonths(11),
                7,
                300,
                false,
                true,
                1,
                5,
                10,
                1,
                5,
                3,
                List.of(new PlanFeatureDto("TRIPS", "Trajets", true)),
                null);
    }

    @Test
    void assertCanCreateVehicle_blocksWhenLimitReached() {
        doReturn(Mono.just(starterAtVehicleLimit)).when(planLimitGuard).getSubscription(managerId);

        StepVerifier.create(planLimitGuard.assertCanCreateVehicle(managerId))
                .expectErrorSatisfies(err -> {
                    assert err instanceof SubscriptionException;
                    assert "SUB_004".equals(((SubscriptionException) err).getBusinessCode());
                })
                .verify();
    }

    @Test
    void assertCanCreateVehicle_allowsWhenBelowLimit() {
        var belowLimit = new ManagerSubscriptionResponse(
                managerId,
                starterAtVehicleLimit.planId(),
                "Starter",
                "ACTIVE",
                starterAtVehicleLimit.subscriptionStart(),
                starterAtVehicleLimit.subscriptionEnd(),
                7,
                300,
                false,
                true,
                1,
                5,
                10,
                1,
                4,
                3,
                starterAtVehicleLimit.features(),
                null);
        doReturn(Mono.just(belowLimit)).when(planLimitGuard).getSubscription(managerId);

        StepVerifier.create(planLimitGuard.assertCanCreateVehicle(managerId))
                .verifyComplete();
    }

    @Test
    void assertActiveAccess_blocksExpiredSubscription() {
        var expired = new ManagerSubscriptionResponse(
                managerId,
                starterAtVehicleLimit.planId(),
                "Starter",
                "EXPIRED",
                LocalDate.now().minusYears(2),
                LocalDate.now().minusMonths(2),
                7,
                -60,
                false,
                false,
                1,
                5,
                10,
                1,
                3,
                2,
                starterAtVehicleLimit.features(),
                null);
        doReturn(Mono.just(expired)).when(planLimitGuard).getSubscription(managerId);

        StepVerifier.create(planLimitGuard.assertActiveAccess(managerId))
                .expectErrorSatisfies(err -> {
                    assert err instanceof SubscriptionException;
                    assert "SUB_001".equals(((SubscriptionException) err).getBusinessCode());
                })
                .verify();
    }

    @Test
    @SuppressWarnings("unchecked")
    void assertFeature_blocksDisabledFeature_withReadableMessage() {
        UUID planId = starterAtVehicleLimit.planId();
        stubAccessRow(planId, "ACTIVE", LocalDate.now().plusMonths(3));
        var kpiDisabled = new com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.PlanFeatureEntity();
        kpiDisabled.setPlanId(planId);
        kpiDisabled.setFeatureKey("KPI_REPORTS");
        kpiDisabled.setEnabled(false);
        org.mockito.Mockito.when(featureRepo.findByPlanId(planId)).thenReturn(reactor.core.publisher.Flux.just(kpiDisabled));

        StepVerifier.create(planLimitGuard.assertFeature(managerId, "KPI_REPORTS"))
                .expectErrorSatisfies(err -> {
                    assert err instanceof SubscriptionException;
                    assert "SUB_006".equals(((SubscriptionException) err).getBusinessCode());
                    assert err.getMessage().contains("KPI & Rapports");
                })
                .verify();
    }

    @Test
    void assertAccess_blocksExpiredSubscription_beyondGracePeriod() {
        stubAccessRow(starterAtVehicleLimit.planId(), "ACTIVE", LocalDate.now().minusDays(30));

        StepVerifier.create(planLimitGuard.assertAccess(managerId, null))
                .expectErrorSatisfies(err -> {
                    assert "SUB_001".equals(((SubscriptionException) err).getBusinessCode());
                })
                .verify();
    }

    @Test
    void assertAccess_allowsCompanyWithoutPlan() {
        stubAccessRow(null, "ACTIVE", null);
        StepVerifier.create(planLimitGuard.assertAccess(managerId, "KPI_REPORTS")).verifyComplete();
    }

    @Test
    void assertAccess_blocksCompanyAwaitingApproval() {
        stubAccessRow(null, "PENDING", null);
        StepVerifier.create(planLimitGuard.assertAccess(managerId, null))
                .expectErrorSatisfies(err -> {
                    assert "SUB_001".equals(((SubscriptionException) err).getBusinessCode());
                })
                .verify();
    }

    @SuppressWarnings("unchecked")
    private void stubAccessRow(UUID planId, String status, LocalDate end) {
        // L'abonnement est celui de l'entreprise de l'acteur.
        org.mockito.Mockito.when(companyScope.resolveCompanyId(managerId)).thenReturn(Mono.just(managerId));
        java.util.Map<String, Object> row = new java.util.HashMap<>();
        row.put("plan_id", planId);
        row.put("subscription_status", status);
        row.put("subscription_end", end);
        row.put("grace_days", 7);
        var spec = org.mockito.Mockito.mock(DatabaseClient.GenericExecuteSpec.class);
        var fetch = org.mockito.Mockito.mock(org.springframework.r2dbc.core.FetchSpec.class);
        org.mockito.Mockito.when(db.sql(org.mockito.ArgumentMatchers.anyString())).thenReturn(spec);
        org.mockito.Mockito.when(spec.bind(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(spec);
        org.mockito.Mockito.when(spec.fetch()).thenReturn(fetch);
        org.mockito.Mockito.when(fetch.one()).thenReturn(Mono.just(row));
    }
}
