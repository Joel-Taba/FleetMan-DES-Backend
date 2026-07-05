package com.yowyob.fleet.infrastructure.adapters.inbound.messaging;

import com.yowyob.fleet.domain.model.DriverDocument;
import com.yowyob.fleet.domain.model.VehicleDocument;
import com.yowyob.fleet.domain.ports.out.DocumentPersistencePort;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

/**
 * Job planifié : Vérification nocturne des documents légaux.
 *
 * S'exécute chaque nuit à 2h00 et :
 * 1. Met à jour le statut de tous les documents (VALID → EXPIRING_SOON → EXPIRED)
 * 2. Identifie les documents nécessitant une alerte (J-30, J-15, J-7, EXPIRED)
 * 3. Publie les notifications via le port SendNotificationPort (à brancher)
 *
 * NOTE : En environnement multi-pods (Kubernetes), ajouter ShedLock
 * pour éviter l'exécution parallèle sur plusieurs instances.
 */
@Component
@RequiredArgsConstructor
public class DocumentExpiryCheckJob {

    private static final Logger log = LoggerFactory.getLogger(DocumentExpiryCheckJob.class);

    private final DocumentPersistencePort documentPort;

    /**
     * Exécution chaque nuit à 2h00.
     * Expression cron : seconde minute heure jour mois jour-semaine
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void checkExpiringDocuments() {
        log.info("=== Démarrage du job de vérification des documents légaux ===");

        LocalDate threshold30 = LocalDate.now().plusDays(30);

        // 1. Mise à jour des statuts des documents véhicule
        Flux<VehicleDocument> vehicleDocsToUpdate = documentPort
                .findVehicleDocsExpiringBefore(threshold30);

        // 2. Mise à jour des statuts des documents conducteur
        Flux<DriverDocument> driverDocsToUpdate = documentPort
                .findDriverDocsExpiringBefore(threshold30);

        // Traitement réactif — fire & forget avec gestion d'erreur
        vehicleDocsToUpdate
                .flatMap(doc -> {
                    doc.refreshStatus();
                    return documentPort.saveVehicleDoc(doc)
                            .doOnSuccess(saved -> logAlert(
                                    "VEHICLE", saved.getVehicleId().toString(),
                                    saved.getDocType().name(),
                                    saved.daysUntilExpiry(),
                                    saved.getStatus().name()
                            ));
                })
                .doOnError(e -> log.error("Erreur mise à jour document véhicule", e))
                .subscribe();

        driverDocsToUpdate
                .flatMap(doc -> {
                    doc.refreshStatus();
                    return documentPort.saveDriverDoc(doc)
                            .doOnSuccess(saved -> logAlert(
                                    "DRIVER", saved.getDriverId().toString(),
                                    saved.getDocType().name(),
                                    saved.daysUntilExpiry(),
                                    saved.getStatus().name()
                            ));
                })
                .doOnError(e -> log.error("Erreur mise à jour document conducteur", e))
                .subscribe(
                        null,
                        e -> log.error("Echec job documents", e),
                        () -> log.info("=== Job documents terminé ===")
                );
    }

    /**
     * Détermine le type d'alerte selon le nombre de jours restants.
     * Retourne null si aucune alerte n'est nécessaire.
     */
    public static String resolveAlertType(long daysUntilExpiry) {
        if (daysUntilExpiry < 0)   return "EXPIRED";
        if (daysUntilExpiry <= 7)  return "J7";
        if (daysUntilExpiry <= 15) return "J15";
        if (daysUntilExpiry <= 30) return "J30";
        return null;
    }

    private void logAlert(String entityType, String entityId,
                          String docType, long days, String status) {
        String alertType = resolveAlertType(days);
        if (alertType != null) {
            log.warn("ALERTE DOCUMENT [{}] — {} {} : {} jours restants (statut: {})",
                    alertType, entityType, entityId, days, status);
            // TODO Sprint suivant : envoyer notification via SendNotificationPort
            // notificationPort.send(buildDocumentAlert(entityType, entityId, docType, alertType))
            //     .subscribe();
        }
    }
}
