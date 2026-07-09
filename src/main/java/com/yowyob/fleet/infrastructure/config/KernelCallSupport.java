package com.yowyob.fleet.infrastructure.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Encapsule les appels vers le Kernel avec Circuit Breaker Resilience4j.
 * Mode dégradé : retourne le fallback sans propager l'erreur.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KernelCallSupport {

    private final ReactiveCircuitBreakerFactory<?, ?> circuitBreakerFactory;

    public <T> Mono<T> execute(String instanceName, Mono<T> call, Mono<T> fallback) {
        ReactiveCircuitBreaker cb = circuitBreakerFactory.create(instanceName);
        return cb.run(call, throwable -> {
            log.warn("⚡ [CB:{}] Circuit ouvert / erreur : {} — mode dégradé",
                    instanceName, throwable.getMessage());
            return fallback;
        });
    }

    /** Variante sans fallback explicite (retourne Mono vide). */
    public <T> Mono<T> run(String instanceName, Mono<T> call) {
        return execute(instanceName, call, Mono.empty());
    }
}
