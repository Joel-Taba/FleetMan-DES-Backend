package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.SubscriptionException;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.ManagerSubscriptionResponse;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.ManagerSubscriptionResponse.PlanFeatureDto;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.DriverR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.FleetR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.PlanFeatureR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.VehicleLocalR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Vérifie les limites de plan et l'état d'abonnement avant les opérations sensibles.
 */
@Service
@RequiredArgsConstructor
public class PlanLimitGuard {

    private final DatabaseClient db;
    private final FleetR2dbcRepository fleetRepo;
    private final VehicleLocalR2dbcRepository vehicleRepo;
    private final DriverR2dbcRepository driverRepo;
    private final PlanFeatureR2dbcRepository featureRepo;

    public Mono<Void> assertActiveAccess(UUID managerId) {
        return getSubscription(managerId)
                .flatMap(sub -> {
                    if (!sub.accessAllowed()) {
                        if ("SUSPENDED".equals(sub.subscriptionStatus())) {
                            return Mono.error(SubscriptionException.suspended());
                        }
                        return Mono.error(SubscriptionException.expired());
                    }
                    return Mono.empty();
                });
    }

    public Mono<Void> assertCanCreateFleet(UUID managerId) {
        return assertActiveAccess(managerId)
                .then(getSubscription(managerId))
                .flatMap(sub -> {
                    if (sub.maxFleets() > 0 && sub.currentFleets() >= sub.maxFleets()) {
                        return Mono.error(SubscriptionException.fleetLimitReached(sub.maxFleets()));
                    }
                    return Mono.empty();
                });
    }

    public Mono<Void> assertCanCreateVehicle(UUID managerId) {
        return assertActiveAccess(managerId)
                .then(getSubscription(managerId))
                .flatMap(sub -> {
                    if (sub.maxVehicles() > 0 && sub.currentVehicles() >= sub.maxVehicles()) {
                        return Mono.error(SubscriptionException.vehicleLimitReached(sub.maxVehicles()));
                    }
                    return Mono.empty();
                });
    }

    public Mono<Void> assertCanCreateDriver(UUID managerId) {
        return assertActiveAccess(managerId)
                .then(getSubscription(managerId))
                .flatMap(sub -> {
                    if (sub.maxDrivers() > 0 && sub.currentDrivers() >= sub.maxDrivers()) {
                        return Mono.error(SubscriptionException.driverLimitReached(sub.maxDrivers()));
                    }
                    return Mono.empty();
                });
    }

    public Mono<Void> assertFeature(UUID managerId, String featureKey) {
        return assertActiveAccess(managerId)
                .then(getSubscription(managerId))
                .flatMap(sub -> {
                    boolean enabled = sub.features().stream()
                            .anyMatch(f -> f.key().equals(featureKey) && f.enabled());
                    if (!enabled) {
                        return Mono.error(SubscriptionException.featureDisabled(featureKey));
                    }
                    return Mono.empty();
                });
    }

    public Mono<ManagerSubscriptionResponse> getSubscription(UUID managerId) {
        return db.sql("""
                SELECT fm.user_id, fm.plan_id, fm.subscription_status,
                       fm.subscription_start, fm.subscription_end,
                       sp.name AS plan_name, sp.max_fleets, sp.max_vehicles, sp.max_drivers,
                       COALESCE(sp.grace_days, 7) AS grace_days
                FROM fleet.fleet_managers fm
                LEFT JOIN fleet.subscription_plans sp ON sp.id = fm.plan_id
                WHERE fm.user_id = :managerId
                """)
                .bind("managerId", managerId)
                .fetch()
                .one()
                .flatMap(row -> {
                    UUID planId = row.get("plan_id") != null ? (UUID) row.get("plan_id") : null;
                    String status = row.get("subscription_status") != null
                            ? row.get("subscription_status").toString() : "ACTIVE";
                    LocalDate start = row.get("subscription_start") != null
                            ? LocalDate.parse(row.get("subscription_start").toString().substring(0, 10)) : null;
                    LocalDate end = row.get("subscription_end") != null
                            ? LocalDate.parse(row.get("subscription_end").toString().substring(0, 10)) : null;
                    int graceDays = row.get("grace_days") != null
                            ? ((Number) row.get("grace_days")).intValue() : 7;
                    int maxFleets = row.get("max_fleets") != null ? ((Number) row.get("max_fleets")).intValue() : 999;
                    int maxVehicles = row.get("max_vehicles") != null ? ((Number) row.get("max_vehicles")).intValue() : 999;
                    int maxDrivers = row.get("max_drivers") != null ? ((Number) row.get("max_drivers")).intValue() : 999;
                    String planName = row.get("plan_name") != null ? row.get("plan_name").toString() : "Libre";

                    long daysUntil = end != null ? ChronoUnit.DAYS.between(LocalDate.now(), end) : 999;
                    boolean inGrace = end != null && daysUntil < 0
                            && ChronoUnit.DAYS.between(end, LocalDate.now()) <= graceDays;
                    boolean accessAllowed = !"SUSPENDED".equals(status)
                            && (!"EXPIRED".equals(status))
                            && (end == null || daysUntil >= 0 || inGrace);

                    Mono<List<PlanFeatureDto>> featuresMono = planId == null
                            ? Mono.just(List.of())
                            : featureRepo.findByPlanId(planId)
                                    .map(f -> new PlanFeatureDto(f.getFeatureKey(), f.getFeatureLabel(), f.isEnabled()))
                                    .collectList();

                    return Mono.zip(
                            fleetRepo.countByManagerId(managerId),
                            vehicleRepo.countByManagerId(managerId),
                            driverRepo.countByManagerId(managerId),
                            featuresMono
                    ).map(counts -> new ManagerSubscriptionResponse(
                            managerId,
                            planId,
                            planName,
                            status,
                            start,
                            end,
                            graceDays,
                            daysUntil,
                            inGrace,
                            accessAllowed,
                            maxFleets,
                            maxVehicles,
                            maxDrivers,
                            counts.getT1().intValue(),
                            counts.getT2().intValue(),
                            counts.getT3().intValue(),
                            counts.getT4()
                    ));
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // Pas de profil manager : accès permissif
                    return Mono.zip(
                            fleetRepo.countByManagerId(managerId),
                            vehicleRepo.countByManagerId(managerId),
                            Mono.just(0L),
                            Mono.just(List.<PlanFeatureDto>of())
                    ).map(c -> new ManagerSubscriptionResponse(
                            managerId, null, "Libre", "ACTIVE", null, null, 7, 999, false, true,
                            999, 999, 999,
                            c.getT1().intValue(), c.getT2().intValue(), 0,
                            c.getT4()
                    ));
                }));
    }
}
