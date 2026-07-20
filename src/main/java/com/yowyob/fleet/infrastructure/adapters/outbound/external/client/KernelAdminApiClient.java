package com.yowyob.fleet.infrastructure.adapters.outbound.external.client;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Opérations Kernel d'administration / provisioning (owner token requis).
 */
@HttpExchange
public interface KernelAdminApiClient {

    @PostExchange("/api/auth/register")
    Mono<KernelAuthApiClient.ApiResponse<KernelAuthApiClient.UserAccountResponse>> registerUser(
            @RequestHeader("Authorization") String bearerToken,
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-Organization-Id") String organizationId,
            @RequestBody KernelAuthApiClient.RegisterUserRequest request);

    @PostExchange("/api/administration/users/{userId}/roles")
    Mono<KernelAuthApiClient.ApiResponse<RoleAssignmentResponse>> assignRole(
            @RequestHeader("Authorization") String bearerToken,
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-Organization-Id") String organizationId,
            @PathVariable("userId") UUID userId,
            @RequestBody AssignRoleRequest request);

    @PostExchange("/api/actors")
    Mono<KernelAuthApiClient.ApiResponse<ActorResponse>> createActor(
            @RequestHeader("Authorization") String bearerToken,
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-Organization-Id") String organizationId,
            @RequestBody CreateActorRequest request);

    @PostExchange("/api/organizations/{organizationId}/actors")
    Mono<KernelAuthApiClient.ApiResponse<OrganizationActorResponse>> linkOrganizationActor(
            @RequestHeader("Authorization") String bearerToken,
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-Organization-Id") String organizationId,
            @PathVariable("organizationId") UUID organizationIdPath,
            @RequestBody LinkOrganizationActorRequest request);

    @GetExchange("/api/organizations/{organizationId}/actors")
    Mono<KernelAuthApiClient.ApiResponse<List<OrganizationActorResponse>>> listOrganizationActors(
            @RequestHeader("Authorization") String bearerToken,
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-Organization-Id") String organizationId,
            @PathVariable("organizationId") UUID organizationIdPath);

    /** Liste des rôles configurés côté Kernel (roles-core) pour le tenant courant. */
    @GetExchange("/api/administration/roles")
    Mono<KernelAuthApiClient.ApiResponse<List<RoleDto>>> listRoles(
            @RequestHeader("Authorization") String bearerToken,
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-Organization-Id") String organizationId);

    record AssignRoleRequest(UUID roleId, String scopeType, UUID scopeId, String scope) {}

    /** Rôle Kernel (roles-core). {@code code} est l'identifiant stable (ex. FLEET_ADMIN). */
    record RoleDto(UUID id, String code, String name, String scopeType) {}

    record CreateActorRequest(
            UUID organizationId,
            String firstName,
            String lastName,
            String email,
            String type
    ) {}

    record LinkOrganizationActorRequest(UUID actorId, String type) {}

    record ActorResponse(UUID id, UUID organizationId, String email, String type) {}

    record OrganizationActorResponse(UUID id, UUID organizationId, UUID actorId, String type) {}

    record RoleAssignmentResponse(UUID id, UUID userId, UUID roleId, String scopeType, String scope) {}
}
