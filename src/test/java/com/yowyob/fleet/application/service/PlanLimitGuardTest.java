package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.SubscriptionException;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.ManagerSubscriptionResponse;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.ManagerSubscriptionResponse.PlanFeatureDto;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.DriverR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.FleetR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.PlanFeatureR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.VehicleLocalR2dbcRepository;
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
    @Mock FleetR2dbcRepository fleetRepo;
    @Mock VehicleLocalR2dbcRepository vehicleRepo;
    @Mock DriverR2dbcRepository driverRepo;
    @Mock PlanFeatureR2dbcRepository featureRepo;

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
                List.of(new PlanFeatureDto("TRIPS", "Trajets", true))
        );
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
                starterAtVehicleLimit.features()
        );
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
                starterAtVehicleLimit.features()
        );
        doReturn(Mono.just(expired)).when(planLimitGuard).getSubscription(managerId);

        StepVerifier.create(planLimitGuard.assertActiveAccess(managerId))
                .expectErrorSatisfies(err -> {
                    assert err instanceof SubscriptionException;
                    assert "SUB_001".equals(((SubscriptionException) err).getBusinessCode());
                })
                .verify();
    }

    @Test
    void assertFeature_blocksDisabledFeature() {
        var noKpi = new ManagerSubscriptionResponse(
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
                2,
                2,
                List.of(new PlanFeatureDto("KPI_REPORTS", "KPI & Rapports", false))
        );
        doReturn(Mono.just(noKpi)).when(planLimitGuard).getSubscription(managerId);

        StepVerifier.create(planLimitGuard.assertFeature(managerId, "KPI_REPORTS"))
                .expectErrorSatisfies(err -> {
                    assert err instanceof SubscriptionException;
                    assert "SUB_006".equals(((SubscriptionException) err).getBusinessCode());
                })
                .verify();
    }
}
