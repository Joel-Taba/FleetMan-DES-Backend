package com.yowyob.fleet.infrastructure.adapters.inbound.messaging;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.DriverR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.FleetR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.UserLocalR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.VehicleLocalR2dbcRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Consommateur des événements métier du Kernel RT-Comops (topic iwm.events.business).
 * Met à jour les projections locales FleetMan.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "application.kafka.kernel-events.enabled", havingValue = "true")
public class KernelEventConsumer {

    private final FleetR2dbcRepository fleetRepository;
    private final VehicleLocalR2dbcRepository vehicleRepository;
    private final DriverR2dbcRepository driverRepository;
    private final UserLocalR2dbcRepository userRepository;

    @KafkaListener(
            topics = "${application.kafka.topics.kernel-events:iwm.events.business}",
            groupId = "${spring.kafka.consumer.group-id:fleet-management-group}")
    public void onKernelEvent(KernelBusinessEvent event) {
        if (event == null || event.type() == null) {
            return;
        }
        log.debug("📥 [KERNEL EVENT] type={}, entityId={}", event.type(), event.entityId());
        switch (event.type()) {
            case "ORGANIZATION_SUSPENDED" -> handleOrganizationSuspended(event.entityId());
            case "RESOURCE_STATUS_CHANGED" -> handleResourceStatusChanged(event.entityId(), event.payload());
            case "ACTOR_DEACTIVATED" -> handleActorDeactivated(event.entityId());
            default -> log.trace("Événement Kernel ignoré : {}", event.type());
        }
    }

    private void handleOrganizationSuspended(String orgIdStr) {
        try {
            UUID orgId = UUID.fromString(orgIdStr);
            fleetRepository.findByKernelOrganizationId(orgId)
                    .flatMap(fleet -> userRepository.findById(fleet.getManagerId())
                            .flatMap(user -> {
                                user.setActive(false);
                                return userRepository.save(user);
                            }))
                    .doOnSuccess(u -> log.info("⛔ Flotte suspendue (org Kernel {}) — manager désactivé", orgId))
                    .subscribe();
        } catch (Exception e) {
            log.warn("ORGANIZATION_SUSPENDED ignoré : {}", e.getMessage());
        }
    }

    private void handleResourceStatusChanged(String resourceIdStr, Map<String, Object> payload) {
        if (payload == null) return;
        String newStatus = payload.get("status") != null ? payload.get("status").toString() : null;
        if (newStatus == null) return;
        try {
            UUID resourceId = UUID.fromString(resourceIdStr);
            vehicleRepository.findByKernelResourceId(resourceId)
                    .flatMap(vehicle -> {
                        vehicle.setStatus(newStatus);
                        return vehicleRepository.save(vehicle);
                    })
                    .doOnSuccess(v -> log.info("🔄 Statut véhicule {} mis à jour → {}", resourceId, newStatus))
                    .subscribe();
        } catch (Exception e) {
            log.warn("RESOURCE_STATUS_CHANGED ignoré : {}", e.getMessage());
        }
    }

    private void handleActorDeactivated(String actorIdStr) {
        try {
            UUID actorId = UUID.fromString(actorIdStr);
            driverRepository.findByKernelActorId(actorId)
                    .flatMap(driver -> userRepository.findById(driver.getUserId())
                            .flatMap(user -> {
                                user.setActive(false);
                                return userRepository.save(user);
                            }))
                    .doOnSuccess(u -> log.info("⛔ Acteur Kernel {} désactivé — compte conducteur suspendu", actorId))
                    .subscribe();
        } catch (Exception e) {
            log.warn("ACTOR_DEACTIVATED ignoré : {}", e.getMessage());
        }
    }

    public record KernelBusinessEvent(String type, String entityId, Map<String, Object> payload) {}
}
