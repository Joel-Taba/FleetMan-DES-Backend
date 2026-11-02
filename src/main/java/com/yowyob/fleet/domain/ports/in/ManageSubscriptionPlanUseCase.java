package com.yowyob.fleet.domain.ports.in;

import com.yowyob.fleet.domain.model.SubscriptionPlan;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

public interface ManageSubscriptionPlanUseCase {

    /** Limites : 0 = illimité. Les fonctionnalités (technicalFeatures) font foi pour l'accès. */
    record CreatePlanCommand(String name, String description,
                             int maxFleets, int maxVehicles, int maxDrivers,
                             BigDecimal monthlyPrice, BigDecimal annualPrice,
                             String currency, Integer sortOrder, Boolean recommended,
                             java.util.List<PlanFeatureCommand> technicalFeatures) {}

    record UpdatePlanCommand(String name, String description,
                             int maxFleets, int maxVehicles, int maxDrivers,
                             BigDecimal monthlyPrice, BigDecimal annualPrice,
                             String currency, Integer sortOrder, Boolean recommended,
                             java.util.List<PlanFeatureCommand> technicalFeatures) {}

    /** billingCycle null : cycle demandé à l'inscription, à défaut mensuel. */
    record ApproveSubscriptionCommand(UUID managerId, UUID approvedBy, UUID planId,
                                      com.yowyob.fleet.domain.model.BillingCycle billingCycle) {}
    record RejectSubscriptionCommand(UUID managerId, UUID rejectedBy, String reason, String subject, String message) {}

    record PlanFeatureCommand(String key, String label, boolean enabled) {}

    Mono<SubscriptionPlan> createPlan(CreatePlanCommand command);
    Flux<SubscriptionPlan> listPlans();
    Mono<SubscriptionPlan> getPlan(UUID id);
    Mono<SubscriptionPlan> updatePlan(UUID id, UpdatePlanCommand command);
    Mono<Void> deactivatePlan(UUID id);
    Mono<Void> assignPlanToManager(UUID managerId, UUID planId, com.yowyob.fleet.domain.model.BillingCycle billingCycle);

    // Workflow approbation
    Flux<com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.PendingSubscriptionDto> listPendingSubscriptions();
    Flux<com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.SubscriptionHistoryDto> listSubscriptionHistory();
    Mono<Void> approveSubscription(ApproveSubscriptionCommand command);
    Mono<Void> rejectSubscription(RejectSubscriptionCommand command);

    Flux<PlanFeatureCommand> getPlanFeatures(UUID planId);
    Mono<Void> replacePlanFeatures(UUID planId, java.util.List<PlanFeatureCommand> features);
    Flux<com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.ActiveSubscriptionDto> listActiveSubscriptions();
}
