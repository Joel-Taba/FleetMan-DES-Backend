package com.yowyob.fleet.infrastructure.adapters.inbound.messaging;

import com.yowyob.fleet.domain.ports.in.ManageMaintenancePlanUseCase;
import com.yowyob.fleet.domain.ports.out.FleetRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job planifié : Vérification quotidienne des plans de maintenance préventive.
 *
 * S'exécute chaque matin à 6h00 et :
 * 1. Pour chaque manager, évalue tous les plans actifs de tous leurs véhicules
 * 2. Crée les nouvelles alertes quand un véhicule entre en zone de préalerte
 * 3. Met à jour les alertes existantes (UPCOMING → DUE → OVERDUE)
 * 4. Enregistre un log d'avertissement pour les alertes OVERDUE
 *
 * NOTE : En environnement multi-pods (Kubernetes), ajouter ShedLock.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PreventiveMaintenanceCheckJob {

    private final ManageMaintenancePlanUseCase useCase;
    private final FleetRepositoryPort fleetPort;

    /**
     * Exécution quotidienne à 6h00.
     */
    @Scheduled(cron = "0 0 6 * * *")
    public void checkPreventiveMaintenance() {
        log.info("=== Démarrage du job de maintenance préventive ===");

        // Récupère tous les managers distincts via les flottes actives
        fleetPort.findAll()
                .map(fleet -> fleet.managerId())
                .distinct()
                .flatMap(managerId -> useCase.evaluateAllPlans(managerId)
                        .doOnNext(alert -> {
                            if (alert.getStatus() != null) {
                                switch (alert.getStatus()) {
                                    case OVERDUE -> log.warn(
                                            "🔴 OVERDUE — Véhicule {} : {} (dépassé de {} km / {} jours)",
                                            alert.getVehicleRegistration(),
                                            alert.getMaintenanceType(),
                                            alert.getKmRemaining() != null
                                                    ? Math.abs(alert.getKmRemaining().intValue()) : "N/A",
                                            alert.getDaysRemaining() != null
                                                    ? Math.abs(alert.getDaysRemaining()) : "N/A"
                                    );
                                    case DUE -> log.warn(
                                            "🟠 DUE — Véhicule {} : {} à effectuer",
                                            alert.getVehicleRegistration(),
                                            alert.getMaintenanceType()
                                    );
                                    case UPCOMING -> log.info(
                                            "🟡 UPCOMING — Véhicule {} : {} dans {} jours / {} km",
                                            alert.getVehicleRegistration(),
                                            alert.getMaintenanceType(),
                                            alert.getDaysRemaining(),
                                            alert.getKmRemaining() != null
                                                    ? alert.getKmRemaining().intValue() : "N/A"
                                    );
                                    default -> {}
                                }
                            }
                            // TODO Module 8 — Alertes : envoyer notification si OVERDUE ou DUE
                        })
                        .doOnError(e -> log.error("Erreur évaluation manager {}: {}", managerId, e.getMessage()))
                        .onErrorResume(e -> reactor.core.publisher.Flux.empty())
                )
                .doOnComplete(() -> log.info("=== Job maintenance préventive terminé ==="))
                .subscribe();
    }
}
