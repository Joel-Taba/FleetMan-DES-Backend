package com.yowyob.fleet.infrastructure.adapters.outbound.external;

import com.yowyob.fleet.domain.ports.out.ExternalActorPort;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.client.KernelAdminApiClient;
import com.yowyob.fleet.infrastructure.adapters.outbound.kernel.exception.KernelException;
import com.yowyob.fleet.infrastructure.config.KernelCallSupport;
import com.yowyob.fleet.infrastructure.config.KernelTokenHolder;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Anti-Corruption Layer vers actor-core du Kernel.
 * Crée un acteur, le rattache à l'organisation et assigne le rôle si configuré.
 */
@Slf4j
public class KernelActorAdapter implements ExternalActorPort {

    private final KernelAdminApiClient adminClient;
    private final KernelTokenHolder tokenHolder;
    private final KernelCallSupport kernelCallSupport;
    private final String tenantId;

    private final String fleetDriverRoleId;
    private final String fleetManagerRoleId;

    public KernelActorAdapter(
            KernelAdminApiClient adminClient,
            KernelTokenHolder tokenHolder,
            KernelCallSupport kernelCallSupport,
            String tenantId,
            String fleetDriverRoleId,
            String fleetManagerRoleId) {
        this.adminClient = adminClient;
        this.tokenHolder = tokenHolder;
        this.kernelCallSupport = kernelCallSupport;
        this.tenantId = tenantId;
        this.fleetDriverRoleId = fleetDriverRoleId;
        this.fleetManagerRoleId = fleetManagerRoleId;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public Mono<UUID> provisionDriverActor(
            UUID kernelUserId,
            String email,
            String firstName,
            String lastName,
            UUID organizationId,
            String fleetRole) {

        if (organizationId == null) {
            log.warn("⚠️ [KERNEL ACTOR] organisationId absent, provisioning acteur ignoré pour {}", email);
            return Mono.empty();
        }

        var createReq = new KernelAdminApiClient.CreateActorRequest(
                organizationId,
                firstName != null ? firstName : "",
                lastName != null ? lastName : "",
                email,
                "DRIVER"
        );

        return kernelCallSupport.run("kernel-actor",
                tokenHolder.getValidAccessToken()
                .flatMap(token -> adminClient.createActor(
                                bearer(token),
                                tenantId,
                                organizationId.toString(),
                                createReq)
                        .flatMap(resp -> {
                            if (!resp.success() || resp.data() == null) {
                                return Mono.error(KernelException.of(
                                        "ACTOR_CREATION_FAILED",
                                        resp.message() != null ? resp.message() : "Création acteur échouée"));
                            }
                            UUID actorId = resp.data().id();
                            var linkReq = new KernelAdminApiClient.LinkOrganizationActorRequest(actorId, "DRIVER");
                            return adminClient.linkOrganizationActor(
                                            bearer(token),
                                            tenantId,
                                            organizationId.toString(),
                                            organizationId,
                                            linkReq)
                                    .then(assignRoleIfConfigured(token, kernelUserId, organizationId, fleetRole))
                                    .thenReturn(actorId);
                        }))
                .doOnSuccess(id -> log.info("✅ [KERNEL ACTOR] Acteur {} créé et lié à l'org {} pour {}",
                        id, organizationId, email))
                .onErrorResume(KernelException.class, e -> {
                    log.error("❌ [KERNEL ACTOR] {}", e.getMessage());
                    return Mono.empty();
                })
                .onErrorResume(e -> {
                    log.warn("⚠️ [KERNEL ACTOR] Provisioning ignoré pour {} : {}", email, e.getMessage());
                    return Mono.empty();
                }));
    }

    private Mono<Void> assignRoleIfConfigured(
            String token, UUID kernelUserId, UUID organizationId, String fleetRole) {

        String roleIdStr = resolveRoleId(fleetRole);
        if (roleIdStr == null || roleIdStr.isBlank()) {
            log.debug("ℹ️ [KERNEL ACTOR] Aucun roleId Kernel configuré pour {}, assignRole ignoré", fleetRole);
            return Mono.empty();
        }

        UUID roleId;
        try {
            roleId = UUID.fromString(roleIdStr);
        } catch (IllegalArgumentException e) {
            log.warn("⚠️ [KERNEL ACTOR] roleId invalide pour {} : {}", fleetRole, roleIdStr);
            return Mono.empty();
        }

        var assignReq = new KernelAdminApiClient.AssignRoleRequest(
                roleId, "ORGANIZATION", organizationId, organizationId.toString());

        return adminClient.assignRole(
                        bearer(token),
                        tenantId,
                        organizationId.toString(),
                        kernelUserId,
                        assignReq)
                .doOnSuccess(r -> log.info("✅ [KERNEL ACTOR] Rôle {} assigné à l'utilisateur {}", fleetRole, kernelUserId))
                .then()
                .onErrorResume(e -> {
                    log.warn("⚠️ [KERNEL ACTOR] assignRole ignoré : {}", e.getMessage());
                    return Mono.empty();
                });
    }

    private String resolveRoleId(String fleetRole) {
        if (fleetRole == null) return null;
        return switch (fleetRole) {
            case "FLEET_DRIVER" -> fleetDriverRoleId;
            case "FLEET_MANAGER" -> fleetManagerRoleId;
            default -> null;
        };
    }

    private static String bearer(String token) {
        return token.startsWith("Bearer ") ? token : "Bearer " + token;
    }
}
