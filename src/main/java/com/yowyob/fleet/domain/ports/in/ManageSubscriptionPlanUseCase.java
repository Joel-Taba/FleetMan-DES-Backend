package com.yowyob.fleet.domain.ports.in;

import com.yowyob.fleet.domain.model.SubscriptionPlan;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

public interface ManageSubscriptionPlanUseCase {

    record CreatePlanCommand(String name, String description,
                             int maxFleets, int maxVehicles, int maxDrivers,
                             BigDecimal monthlyPrice, BigDecimal annualPrice,
                             String currency, String features) {}

    record UpdatePlanCommand(String name, String description,
                             int maxFleets, int maxVehicles, int maxDrivers,
                             BigDecimal monthlyPrice, BigDecimal annualPrice,
                             String features) {}

    record ApproveSubscriptionCommand(UUID managerId, UUID approvedBy, UUID planId) {}
    record RejectSubscriptionCommand(UUID managerId, UUID rejectedBy, String reason) {}

    Mono<SubscriptionPlan> createPlan(CreatePlanCommand command);
    Flux<SubscriptionPlan> listPlans();
    Mono<SubscriptionPlan> getPlan(UUID id);
    Mono<SubscriptionPlan> updatePlan(UUID id, UpdatePlanCommand command);
    Mono<Void> deactivatePlan(UUID id);
    Mono<Void> assignPlanToManager(UUID managerId, UUID planId);

    // Workflow approbation
    Flux<Object> listPendingSubscriptions();
    Mono<Void> approveSubscription(ApproveSubscriptionCommand command);
    Mono<Void> rejectSubscription(RejectSubscriptionCommand command);
}
