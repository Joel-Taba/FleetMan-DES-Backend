package com.yowyob.fleet.infrastructure.adapters.outbound.external;

import com.yowyob.fleet.domain.ports.out.TiiBnTickMissionPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Simulateur / Adaptateur de test pour le service TiiBnTick / Cœur central (G1).
 * Activé par défaut ou lorsque l'intégration distante n'est pas configurée.
 * Conforme au cahier des charges ENSPY 2026-2027 §3.2.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "yowyob.tiibntick.mock", havingValue = "true", matchIfMissing = true)
public class FakeTiiBnTickAdapter implements TiiBnTickMissionPort {

    @Override
    public Mono<Void> notifyMissionStarted(UUID deliveryId, UUID missionId, UUID driverId, UUID vehicleId) {
        log.info("[SIMULATEUR G1 TiiBnTick] Notification de démarrage reçue : deliveryId={}, missionId={}, driver={}, vehicle={}",
                deliveryId, missionId, driverId, vehicleId);
        return Mono.empty();
    }

    @Override
    public Mono<Void> notifyMissionCompleted(UUID deliveryId, UUID missionId, UUID proofId) {
        log.info("[SIMULATEUR G1 TiiBnTick] Notification de livraison réussie reçue : deliveryId={}, missionId={}, proofId={}. " +
                 "Prêt pour validation et déblocage de test du règlement.", deliveryId, missionId, proofId);
        return Mono.empty();
    }

    @Override
    public Mono<Void> notifyMissionIncident(UUID deliveryId, UUID missionId, String incidentType, String details) {
        log.warn("[SIMULATEUR G1 TiiBnTick] Incident notifié : deliveryId={}, missionId={}, type={}, details={}",
                deliveryId, missionId, incidentType, details);
        return Mono.empty();
    }
}

