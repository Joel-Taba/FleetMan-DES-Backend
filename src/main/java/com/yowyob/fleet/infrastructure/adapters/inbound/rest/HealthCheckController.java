package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.domain.ports.out.ExternalGeofencePort;
import com.yowyob.fleet.domain.ports.out.StatisticsPort;
import com.yowyob.fleet.infrastructure.config.OpenApiConfig;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_MONITORING , description = "Endpoints de diagnostic et statistiques publiques")
public class HealthCheckController {

    private final DatabaseClient databaseClient;
    private final ReactiveRedisConnectionFactory redisConnectionFactory;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final StatisticsPort statisticsPort;
    private final AuthPort authPort;
    private final ExternalGeofencePort geofencePort;
    private final WebClient.Builder webClientBuilder;

    @Value("${application.geofence-system-user.username}") private String sysUser;
    @Value("${application.geofence-system-user.password}") private String sysPass;
    @Value("${application.auth.url}") private String authUrl;
    @Value("${application.external.vehicle-service-url}") private String vehicleUrl;
    @Value("${application.external.payment-service-url}") private String paymentUrl;
    @Value("${application.external.geofence-service-url}") private String geofenceUrl;

    private static final String STATS_CACHE_KEY = "fleet:stats:public";

    @GetMapping("/diagnostic")
    @Operation(summary = "Diagnostic Système profond (Multi-Service)", description = "Vérifie la connectivité réelle de tous les services tiers.")
    public Mono<Map<String, Object>> getDeepDiagnostic() {
        // Récupération parallèle des deux tokens système
        return Mono.zip(
            authPort.login(sysUser, sysPass).map(r -> "Bearer " + r.accessToken()).onErrorReturn(""),
            geofencePort.getSystemToken().onErrorReturn("")
        ).flatMap(tokens -> {
            String ecoToken = tokens.getT1();
            String geoToken = tokens.getT2();

            return Mono.zip(
                checkDB(),
                checkRedis(),
                checkRemote("/api/roles", authUrl, ecoToken, "Auth Service"),
                checkRemote("/vehicles", vehicleUrl, ecoToken, "Vehicle Service"),
                checkRemote("/api/v1/wallets", paymentUrl, ecoToken, "Payment Service"),
                checkRemote("/api/geofence/circles", geofenceUrl, geoToken, "Geofence Engine")
            ).map(t -> {
                Map<String, Object> report = new HashMap<>();
                report.put("timestamp", Instant.now());
                report.put("local_db", t.getT1());
                report.put("local_redis", t.getT2());
                report.put("auth_service", t.getT3());
                report.put("vehicle_service", t.getT4());
                report.put("payment_service", t.getT5());
                report.put("geofence_engine", t.getT6());
                return report;
            });
        });
    }

@GetMapping("/public-stats")
    @Operation(summary = "Statistiques publiques (Landing Page)", description = "Données agrégées avec cache de 6 heures.")
    @SuppressWarnings("unchecked") // Pour ignorer l'avertissement sur le cast de la Map
    public Mono<Map<String, Object>> getPublicStats() {
        return redisTemplate.opsForValue().get(STATS_CACHE_KEY)
            // On cast chaque élément du flux plutôt que le Mono lui-même
            .map(value -> (Map<String, Object>) value)
            // Important : Utiliser defer pour que le calcul ne se lance que si nécessaire
            .switchIfEmpty(Mono.defer(this::calculateAndCacheStats));
    }

    private Mono<Map<String, Object>> calculateAndCacheStats() {
        return Mono.zip(
            statisticsPort.countFleetManagers(),
            statisticsPort.countFleets(),
            statisticsPort.countVehicles(),
            statisticsPort.countDrivers()
        ).map(t -> {
            Map<String, Object> stats = new HashMap<>();
            stats.put("activeManagers", t.getT1());
            stats.put("totalFleets", t.getT2());
            stats.put("managedVehicles", t.getT3());
            stats.put("totalDrivers", t.getT4());
            stats.put("serviceStatus", "All systems operational");
            return stats;
        }).flatMap(stats -> 
            redisTemplate.opsForValue().set(STATS_CACHE_KEY, stats, Duration.ofHours(6))
                .thenReturn(stats)
        );
    }

    // --- Helpers de sonde ---

    private Mono<String> checkDB() {
        return databaseClient.sql("SELECT 1").fetch().first().map(r -> "UP").timeout(Duration.ofSeconds(5)).onErrorReturn("DOWN");
    }

    private Mono<String> checkRedis() {
        return redisConnectionFactory.getReactiveConnection().ping().map(r -> "UP").timeout(Duration.ofSeconds(5)).onErrorReturn("DOWN");
    }

    private Mono<String> checkRemote(String path, String baseUrl, String token, String name) {
        if (baseUrl == null || baseUrl.isBlank()) return Mono.just("NOT_CONFIGURED");
        
        return webClientBuilder.build().get()
                .uri(baseUrl + path)
                .header("Authorization", token)
                .exchangeToMono(response -> {
                    // Si le code est 2xx, 401 ou 403, le service est considéré comme "UP"
                    if (response.statusCode().is2xxSuccessful() || 
                        response.statusCode().value() == 401 || 
                        response.statusCode().value() == 403) {
                        return Mono.just("UP");
                    }
                    // Si c'est un 500 ou autre, on le marque DOWN avec le code
                    return Mono.just("DOWN (" + response.statusCode().value() + ")");
                })
                .timeout(Duration.ofSeconds(5))
                .onErrorResume(e -> {
                    log.warn("Health check failed for {}: {}", name, e.getMessage());
                    return Mono.just("DOWN");
                });
    }
}