package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.SubscriptionException;
import com.yowyob.fleet.domain.model.PlanFeature;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.ManagerSubscriptionResponse;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.ManagerSubscriptionResponse.PlanFeatureDto;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.PlanFeatureR2dbcRepository;
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
 * L'abonnement est porté par l'entreprise (fleet.companies) : l'identifiant reçu peut être
 * celui de l'admin, d'un gestionnaire ou d'un chauffeur, l'entreprise est résolue puis ses
 * flottes, véhicules (hors retirés) et chauffeurs (hors partis) sont comptés.
 */
@Service
@RequiredArgsConstructor
public class PlanLimitGuard {

    private final DatabaseClient db;
    private final PlanFeatureR2dbcRepository featureRepo;
    private final CompanyScopeService companyScope;

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
        return assertAccess(managerId, featureKey);
    }

    /**
     * Contrôle léger appliqué à chaque requête d'un gestionnaire (PlanAccessWebFilter) :
     * abonnement utilisable (ni suspendu, ni expiré au-delà de la période de grâce) et, si
     * {@code featureKey} est fourni, fonctionnalité incluse dans le plan. Un gestionnaire sans
     * plan (comptes historiques) ou sans profil gestionnaire n'est pas restreint.
     */
    public Mono<Void> assertAccess(UUID managerId, String featureKey) {
        return companyScope.resolveCompanyId(managerId).flatMap(companyId -> db.sql("""
                SELECT c.plan_id, c.subscription_status, c.subscription_end,
                       COALESCE(
                         (SELECT setting_value::int FROM fleet.app_settings WHERE setting_key = 'subscription_grace_days'),
                         COALESCE(sp.grace_days, 7)
                       ) AS grace_days
                FROM fleet.companies c
                LEFT JOIN fleet.subscription_plans sp ON sp.id = c.plan_id
                WHERE c.id = :companyId
                """)
                .bind("companyId", companyId)
                .fetch()
                .one())
                .flatMap(row -> {
                    String status = row.get("subscription_status") != null ? row.get("subscription_status").toString() : "ACTIVE";
                    LocalDate end = row.get("subscription_end") != null
                            ? LocalDate.parse(row.get("subscription_end").toString().substring(0, 10)) : null;
                    int graceDays = row.get("grace_days") != null ? ((Number) row.get("grace_days")).intValue() : 7;
                    if ("SUSPENDED".equals(status)) {
                        return Mono.error(SubscriptionException.suspended());
                    }
                    // Entreprise dont l'inscription n'est pas encore validée.
                    if ("PENDING".equals(status)) {
                        return Mono.error(SubscriptionException.expired());
                    }
                    boolean expired = "EXPIRED".equals(status)
                            || (end != null && ChronoUnit.DAYS.between(end, LocalDate.now()) > graceDays);
                    if (expired) {
                        return Mono.error(SubscriptionException.expired());
                    }
                    UUID planId = row.get("plan_id") instanceof UUID id ? id : null;
                    if (featureKey == null || planId == null) {
                        return Mono.empty();
                    }
                    return featureRepo.findByPlanId(planId)
                            .any(f -> f.getFeatureKey().equals(featureKey) && f.isEnabled())
                            .flatMap(enabled -> Boolean.TRUE.equals(enabled)
                                    ? Mono.<Void>empty()
                                    : Mono.error(SubscriptionException.featureDisabled(PlanFeature.labelOf(featureKey))));
                })
                .then();
    }

    /** Abonnement de l'entreprise de l'acteur (admin, gestionnaire ou chauffeur). */
    public Mono<ManagerSubscriptionResponse> getSubscription(UUID actorId) {
        return companyScope.resolveCompanyId(actorId)
                .flatMap(this::getCompanySubscription)
                .switchIfEmpty(Mono.defer(() -> Mono.just(new ManagerSubscriptionResponse(
                        actorId, null, "Libre", "ACTIVE", null, null, 7, 999, false, true,
                        0, 0, 0, 0, 0, 0, List.of(), null))));
    }

    public Mono<ManagerSubscriptionResponse> getCompanySubscription(UUID companyId) {
        UUID managerId = companyId;
        return db.sql("""
                SELECT c.id, c.plan_id, c.subscription_status,
                       c.subscription_start, c.subscription_end, c.billing_cycle,
                       sp.name AS plan_name, sp.max_fleets, sp.max_vehicles, sp.max_drivers,
                       COALESCE(
                         (SELECT setting_value::int FROM fleet.app_settings WHERE setting_key = 'subscription_grace_days'),
                         COALESCE(sp.grace_days, 7)
                       ) AS grace_days
                FROM fleet.companies c
                LEFT JOIN fleet.subscription_plans sp ON sp.id = c.plan_id
                WHERE c.id = :companyId
                """)
                .bind("companyId", companyId)
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
                    // 0 = illimité (aucun plan : illimité également).
                    int maxFleets = row.get("max_fleets") != null ? ((Number) row.get("max_fleets")).intValue() : 0;
                    int maxVehicles = row.get("max_vehicles") != null ? ((Number) row.get("max_vehicles")).intValue() : 0;
                    int maxDrivers = row.get("max_drivers") != null ? ((Number) row.get("max_drivers")).intValue() : 0;
                    String billingCycle = row.get("billing_cycle") != null ? row.get("billing_cycle").toString() : null;
                    String planName = row.get("plan_name") != null ? row.get("plan_name").toString() : "Libre";

                    long daysUntil = end != null ? ChronoUnit.DAYS.between(LocalDate.now(), end) : 999;
                    boolean inGrace = end != null && daysUntil < 0
                            && ChronoUnit.DAYS.between(end, LocalDate.now()) <= graceDays;
                    boolean accessAllowed = !"SUSPENDED".equals(status)
                            && !"EXPIRED".equals(status) && !"PENDING".equals(status)
                            && (end == null || daysUntil >= 0 || inGrace);

                    Mono<List<PlanFeatureDto>> featuresMono = planId == null
                            ? Mono.just(List.of())
                            : featureRepo.findByPlanId(planId)
                                    .map(f -> new PlanFeatureDto(f.getFeatureKey(), f.getFeatureLabel(), f.isEnabled()))
                                    .collectList();

                    return Mono.zip(
                            countCompany(COUNT_FLEETS, companyId),
                            countCompany(COUNT_VEHICLES, companyId),
                            countCompany(COUNT_DRIVERS, companyId),
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
                            counts.getT4(),
                            billingCycle
                    ));
                });
    }

    private static final String COUNT_FLEETS =
            "SELECT COUNT(*) AS n FROM fleet.fleets WHERE company_id = :c AND deleted_at IS NULL";
    private static final String COUNT_VEHICLES = """
            SELECT COUNT(*) AS n FROM fleet.vehicles v LEFT JOIN fleet.fleets f ON f.id = v.fleet_id
            WHERE v.deleted_at IS NULL AND v.status <> 'RETIRED'
              AND (f.company_id = :c OR (v.fleet_id IS NULL AND v.manager_id IN
                   (SELECT fm.user_id FROM fleet.fleet_managers fm WHERE fm.created_by_admin_id = :c)))
            """;
    private static final String COUNT_DRIVERS = """
            SELECT COUNT(*) AS n FROM fleet.drivers d JOIN fleet.fleets f ON f.id = d.fleet_id
            WHERE f.company_id = :c AND d.deleted_at IS NULL AND d.status <> 'LEFT'
            """;

    private Mono<Long> countCompany(String sql, UUID companyId) {
        return db.sql(sql).bind("c", companyId)
                .map((row, meta) -> ((Number) row.get("n")).longValue())
                .one()
                .defaultIfEmpty(0L);
    }
}
