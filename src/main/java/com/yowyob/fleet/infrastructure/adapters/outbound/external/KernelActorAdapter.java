package com.yowyob.fleet.infrastructure.adapters.outbound.external;

import com.yowyob.fleet.domain.ports.out.ExternalActorPort;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.client.KernelAdminApiClient;
import com.yowyob.fleet.infrastructure.adapters.outbound.kernel.exception.KernelException;
import com.yowyob.fleet.infrastructure.config.KernelCallSupport;
import com.yowyob.fleet.infrastructure.config.KernelRoleRegistry;
import com.yowyob.fleet.infrastructure.config.KernelTokenHolder;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Anti-Corruption Layer vers actor-core / administration du Kernel.
 * Crée un acteur, le rattache à l'organisation et assigne le rôle si configuré.
 */
@Slf4j
public class KernelActorAdapter implements ExternalActorPort {

    private final KernelAdminApiClient adminClient;
    private final KernelTokenHolder tokenHolder;
    private final KernelCallSupport kernelCallSupport;
    private final KernelRoleRegistry roleRegistry;
    private final String tenantId;
    private final String defaultOrganizationId;

    public KernelActorAdapter(
            KernelAdminApiClient adminClient,
            KernelTokenHolder tokenHolder,
            KernelCallSupport kernelCallSupport,
            KernelRoleRegistry roleRegistry,
            String tenantId,
            String defaultOrganizationId) {
        this.adminClient = adminClient;
        this.tokenHolder = tokenHolder;
        this.kernelCallSupport = kernelCallSupport;
        this.roleRegistry = roleRegistry;
        this.tenantId = tenantId;
        this.defaultOrganizationId = defaultOrganizationId;
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

        String actorType = "FLEET_DRIVER".equals(fleetRole) ? "DRIVER" : "EMPLOYEE";
        var createReq = new KernelAdminApiClient.CreateActorRequest(
                organizationId,
                firstName != null ? firstName : "",
                lastName != null ? lastName : "",
                email,
                actorType
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
                            var linkReq = new KernelAdminApiClient.LinkOrganizationActorRequest(actorId, actorType);
                            return adminClient.linkOrganizationActor(
                                            bearer(token),
                                            tenantId,
                                            organizationId.toString(),
                                            organizationId,
                                            linkReq)
                                    .then(assignRoleForOrganization(token, kernelUserId, organizationId, fleetRole))
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

    @Override
    public Mono<Void> assignPlatformRole(UUID kernelUserId, String fleetRole) {
        if (kernelUserId == null || fleetRole == null) {
            return Mono.empty();
        }
        return tokenHolder.getValidAccessToken()
                .flatMap(token -> doAssignRole(token, kernelUserId, fleetRole))
                .onErrorResume(e -> {
                    log.warn("⚠️ [KERNEL ACTOR] assignPlatformRole({}) ignoré pour {} : {}",
                            fleetRole, kernelUserId, e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * Assigne le rôle FleetMan à portée TENANT (et non ORGANIZATION) : c'est le scope réel
     * déclaré côté Kernel pour tous les rôles FleetMan (vérifié via GET /api/administration/roles).
     * Un scope ORGANIZATION provoquait un échec silencieux de l'assignation.
     */
    private Mono<Void> assignRoleForOrganization(
            String token, UUID kernelUserId, UUID organizationId, String fleetRole) {
        return doAssignRole(token, kernelUserId, fleetRole);
    }

    private Mono<Void> doAssignRole(String token, UUID kernelUserId, String fleetRole) {
        return roleRegistry.resolve(fleetRole)
                .switchIfEmpty(Mono.fromRunnable(() ->
                        log.debug("ℹ️ [KERNEL ACTOR] Aucun roleId Kernel résolu pour {}, assignRole ignoré", fleetRole)))
                .flatMap(roleId -> {
                    var assignReq = new KernelAdminApiClient.AssignRoleRequest(
                            roleId, "TENANT", UUID.fromString(tenantId), "TENANT");
                    return adminClient.assignRole(
                                    bearer(token),
                                    tenantId,
                                    defaultOrganizationId,
                                    kernelUserId,
                                    assignReq)
                            .doOnSuccess(r -> log.info(
                                    "✅ [KERNEL ACTOR] Rôle {} ({}) assigné à l'utilisateur {}",
                                    fleetRole, roleId, kernelUserId))
                            .then();
                })
                .onErrorResume(e -> {
                    log.warn("⚠️ [KERNEL ACTOR] assignRole {} ignoré pour {} : {}", fleetRole, kernelUserId, e.getMessage());
                    return Mono.empty();
                });
    }

    private static String bearer(String token) {
        return token.startsWith("Bearer ") ? token : "Bearer " + token;
    }
}
