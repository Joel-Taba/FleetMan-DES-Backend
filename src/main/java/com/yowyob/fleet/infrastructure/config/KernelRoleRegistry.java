package com.yowyob.fleet.infrastructure.config;

import com.yowyob.fleet.infrastructure.adapters.outbound.external.client.KernelAdminApiClient;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Résout les UUID des rôles FleetMan (roles-core Kernel) par leur code (ex. FLEET_ADMIN).
 *
 * Deux sources, dans l'ordre de priorité :
 * 1. Surcharge explicite via configuration (application.kernel.roles.*) — utile si le rôle
 *    doit être figé (ex. environnement de prod verrouillé).
 * 2. Résolution dynamique via {@code GET /api/administration/roles} (avec le token owner),
 *    mise en cache en mémoire — évite toute dépendance à un UUID codé en dur qui deviendrait
 *    invalide si le Kernel réattribue les rôles.
 *
 * Sans cette résolution dynamique, un rôle non configuré explicitement (ex. FLEET_ADMIN,
 * FLEET_SUPER_ADMIN) ne pouvait jamais être assigné, empêchant tout login effectif pour
 * les comptes créés depuis l'application (utilisateur créé côté Kernel mais sans rôle).
 */
@Slf4j
public class KernelRoleRegistry {

    private final KernelAdminApiClient adminClient;
    private final KernelTokenHolder tokenHolder;
    private final String tenantId;
    private final String organizationId;
    private final Map<String, String> configuredOverrides;

    private final Mono<Map<String, UUID>> rolesByCode;

    public KernelRoleRegistry(
            KernelAdminApiClient adminClient,
            KernelTokenHolder tokenHolder,
            String tenantId,
            String organizationId,
            Map<String, String> configuredOverrides) {
        this.adminClient = adminClient;
        this.tokenHolder = tokenHolder;
        this.tenantId = tenantId;
        this.organizationId = organizationId;
        this.configuredOverrides = configuredOverrides;
        this.rolesByCode = fetchRoles().cache(Duration.ofMinutes(30));
    }

    /** Résout l'UUID du rôle Kernel correspondant à un code FleetMan (ex. FLEET_ADMIN). */
    public Mono<UUID> resolve(String fleetRoleCode) {
        if (fleetRoleCode == null || fleetRoleCode.isBlank()) {
            return Mono.empty();
        }
        String override = configuredOverrides.get(fleetRoleCode);
        if (override != null && !override.isBlank()) {
            try {
                return Mono.just(UUID.fromString(override));
            } catch (IllegalArgumentException e) {
                log.warn("⚠️ [KERNEL ROLES] UUID configuré invalide pour {} : {}", fleetRoleCode, override);
            }
        }
        return rolesByCode.mapNotNull(m -> m.get(fleetRoleCode));
    }

    private Mono<Map<String, UUID>> fetchRoles() {
        return tokenHolder.getValidAccessToken()
                .flatMap(token -> adminClient.listRoles(bearer(token), tenantId, organizationId))
                .map(resp -> {
                    if (!resp.success() || resp.data() == null) {
                        log.warn("⚠️ [KERNEL ROLES] Résolution dynamique impossible : {}", resp.message());
                        return Map.<String, UUID>of();
                    }
                    Map<String, UUID> map = resp.data().stream()
                            .filter(r -> r.code() != null && r.id() != null)
                            .collect(Collectors.toMap(
                                    KernelAdminApiClient.RoleDto::code,
                                    KernelAdminApiClient.RoleDto::id,
                                    (a, b) -> a));
                    log.info("✅ [KERNEL ROLES] {} rôle(s) résolu(s) dynamiquement : {}", map.size(), map.keySet());
                    return map;
                })
                .onErrorResume(e -> {
                    log.warn("⚠️ [KERNEL ROLES] Échec résolution dynamique des rôles : {}", e.getMessage());
                    return Mono.just(Map.of());
                });
    }

    private static String bearer(String token) {
        return token.startsWith("Bearer ") ? token : "Bearer " + token;
    }
}
