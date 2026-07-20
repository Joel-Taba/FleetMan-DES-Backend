package com.yowyob.fleet.infrastructure.adapters.inbound.health;

import com.yowyob.fleet.infrastructure.config.KernelTokenHolder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Vérifie la connectivité au Kernel RT-Comops (token owner + endpoint health).
 */
@Component("kernel")
@ConditionalOnProperty(name = "application.auth.mode", havingValue = "kernel")
public class KernelHealthIndicator implements ReactiveHealthIndicator {

    private final KernelTokenHolder tokenHolder;
    private final WebClient kernelWebClient;
    private final String kernelUrl;

    public KernelHealthIndicator(
            KernelTokenHolder tokenHolder,
            @Qualifier("kernelWebClient") WebClient kernelWebClient,
            @Value("${application.kernel.url}") String kernelUrl) {
        this.tokenHolder = tokenHolder;
        this.kernelWebClient = kernelWebClient;
        this.kernelUrl = kernelUrl;
    }

    @Override
    public Mono<Health> health() {
        return tokenHolder.getValidAccessToken()
                .timeout(Duration.ofSeconds(8))
                .flatMap(token -> kernelWebClient.get()
                        .uri("/actuator/health")
                        .header("Authorization", token.startsWith("Bearer ") ? token : "Bearer " + token)
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(Duration.ofSeconds(5))
                        .map(body -> Health.up()
                                .withDetail("kernelUrl", kernelUrl)
                                .withDetail("auth", "token_valid")
                                .withDetail("healthEndpoint", "reachable")
                                .build())
                        .onErrorResume(e -> {
                            String msg = e.getMessage() != null ? e.getMessage() : "";
                            // Le endpoint /actuator/health du Kernel peut renvoyer 500
                            // alors que l'auth (discover-contexts, select-context) fonctionne.
                            if (msg.contains("404") || msg.contains("500")) {
                                return Mono.just(Health.up()
                                        .withDetail("kernelUrl", kernelUrl)
                                        .withDetail("auth", "token_valid")
                                        .withDetail("healthEndpoint", "degraded: " + msg)
                                        .build());
                            }
                            return Mono.just(Health.down()
                                    .withDetail("kernelUrl", kernelUrl)
                                    .withDetail("auth", "token_valid")
                                    .withDetail("healthEndpoint", msg)
                                    .build());
                        }))
                .onErrorResume(e -> Mono.just(Health.down()
                        .withDetail("kernelUrl", kernelUrl)
                        .withDetail("auth", e.getMessage())
                        .build()));
    }
}
