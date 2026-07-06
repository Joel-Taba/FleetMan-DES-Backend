package com.yowyob.fleet.infrastructure.adapters.outbound.external.client;

import com.yowyob.fleet.infrastructure.adapters.outbound.external.client.KernelAuthApiClient.ApiResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Client resource-core Kernel — véhicules = ressources matérielles. */
@HttpExchange
public interface KernelResourceApiClient {

    @PostExchange("/api/resources")
    Mono<ApiResponse<MaterialResourceResponse>> registerResource(
            @RequestHeader("Authorization") String bearerToken,
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-Organization-Id") String organizationIdHeader,
            @RequestBody RegisterMaterialResourceRequest request);

    @GetExchange("/api/resources/{resourceId}")
    Mono<ApiResponse<MaterialResourceResponse>> getResource(
            @RequestHeader("Authorization") String bearerToken,
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-Organization-Id") String organizationIdHeader,
            @PathVariable("resourceId") UUID resourceId);

    @PostExchange("/api/resources/{resourceId}/assignments")
    Mono<ApiResponse<MaterialResourceResponse>> assignResource(
            @RequestHeader("Authorization") String bearerToken,
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-Organization-Id") String organizationIdHeader,
            @PathVariable("resourceId") UUID resourceId,
            @RequestBody AssignMaterialResourceRequest request);

    @PostExchange("/api/resources/{resourceId}/unassign")
    Mono<ApiResponse<MaterialResourceResponse>> unassignResource(
            @RequestHeader("Authorization") String bearerToken,
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-Organization-Id") String organizationIdHeader,
            @PathVariable("resourceId") UUID resourceId);

    record MaterialResourceResponse(
            UUID id,
            UUID tenantId,
            UUID organizationId,
            UUID agencyId,
            String resourceCode,
            String name,
            String category,
            String serialNumber,
            String status
    ) {}

    record RegisterMaterialResourceRequest(
            UUID organizationId,
            UUID agencyId,
            String resourceCode,
            String name,
            String category,
            String serialNumber
    ) {}

    record AssignMaterialResourceRequest(
            String assigneeType,
            UUID assigneeId
    ) {}
}
