package com.yowyob.fleet.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuration de l'infrastructure des jobs planifiés.
 *
 * Active le support de @Scheduled dans l'application.
 * Les jobs concrets sont définis dans leurs modules respectifs
 * (ex: DocumentExpiryCheckJob, KpiCalculationJob, etc.)
 *
 * IMPORTANT — Multi-instances (Kubernetes) :
 * En environnement multi-pods, chaque pod exécuterait les jobs en parallèle.
 * Pour éviter cela, il faudra ajouter ShedLock ou Spring Batch dans une
 * prochaine itération. Pour l'instant, un seul pod est supposé actif.
 *
 * Expressions cron utilisées dans le projet :
 *   "0 0 2 * * *"   - Chaque nuit a 2h00 (verification documents)
 *   "0 0 3 * * *"   - Chaque nuit a 3h00 (calcul KPIs quotidiens)
 *   "0 0 4 * * MON" - Chaque lundi a 4h00 (calcul KPIs hebdomadaires)
 *   "0 0 5 1 * *"   - Le 1er de chaque mois a 5h00 (KPIs mensuels)
 *   "0 0/30 * * * *"- Toutes les 30 minutes (verification alertes)
 */
@Configuration
@EnableScheduling
@Slf4j
public class ScheduledJobConfig {

    // La configuration est intentionnellement minimale.
    // @EnableScheduling suffit à activer le support des jobs.
    // Les beans @Scheduled sont auto-détectés par Spring.

}
