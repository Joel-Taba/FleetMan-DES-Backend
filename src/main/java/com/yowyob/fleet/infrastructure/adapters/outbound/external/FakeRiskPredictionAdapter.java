package com.yowyob.fleet.infrastructure.adapters.outbound.external;

import com.yowyob.fleet.domain.ports.out.RiskPredictionPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Adaptateur de test / Règle de repli déterministe pour le service Cœur IA (G6).
 * Conforme au cahier des charges ENSPY §3.4 & §3.8 :
 * "En cas de panne ou de données insuffisantes, retournez un état explicite et appliquez la règle de repli."
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "yowyob.ai.mock", havingValue = "true", matchIfMissing = true)
public class FakeRiskPredictionAdapter implements RiskPredictionPort {

    @Override
    public Mono<DelayRiskEvaluation> evaluateMissionDelayRisk(UUID missionId) {
        log.info("[SIMULATEUR G6 Cœur IA] Application de la règle heuristique de repli pour la mission {}", missionId);

        // Règle de repli déterministe (fallback rule)
        DelayRiskEvaluation evaluation = new DelayRiskEvaluation(
                missionId,
                0.15,
                "LOW",
                "fallback-deterministic-v1.0",
                true,
                "Évaluation heuristique basée sur les horaires planifiés et l'absence d'incident critique connu."
        );

        return Mono.just(evaluation);
    }
}

