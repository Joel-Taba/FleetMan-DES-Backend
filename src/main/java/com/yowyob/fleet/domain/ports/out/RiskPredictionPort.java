package com.yowyob.fleet.domain.ports.out;

import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Port sortant : interaction avec le Cœur IA (G6) pour l'estimation du risque de retard.
 * Conforme au cahier des charges §3.4 & §3.8 :
 * En cas d'indisponibilité ou d'erreur du service IA distant, une règle de repli déterministe est appliquée.
 */
public interface RiskPredictionPort {

    record DelayRiskEvaluation(
            UUID missionId,
            double delayRiskProbability,   // score entre 0.0 et 1.0
            String riskLevel,             // LOW, MEDIUM, HIGH, CRITICAL
            String modelVersion,          // ex: "v1.2-random-forest" ou "fallback-rule-v1"
            boolean fallbackApplied,      // true si calculé via la règle heuristique de secours
            String explanation            // Facteurs explicatifs du score
    ) {}

    /**
     * Évalue le risque de retard pour une mission donnée.
     * Consomme le service IA (G6) ou bascule sur la règle de repli locale en cas de panne réseau.
     */
    Mono<DelayRiskEvaluation> evaluateMissionDelayRisk(UUID missionId);
}

