package com.yowyob.fleet.infrastructure.adapters.outbound.external.client;

import com.yowyob.fleet.infrastructure.adapters.outbound.external.client.KernelAuthApiClient.ApiResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/** Client organization-core Kernel. */
@HttpExchange
public interface KernelOrganizationApiClient {

    @GetExchange("/api/organizations/{organizationId}")
    Mono<ApiResponse<OrganizationResponse>> getOrganization(
            @RequestHeader("Authorization") String bearerToken,
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-Organization-Id") String organizationIdHeader,
            @PathVariable("organizationId") UUID organizationId);

    @PostExchange("/api/organizations")
    Mono<ApiResponse<OrganizationResponse>> createOrganization(
            @RequestHeader("Authorization") String bearerToken,
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestBody CreateOrganizationRequest request);

    @PostExchange("/api/organizations/{organizationId}/approve")
    Mono<ApiResponse<OrganizationResponse>> approveOrganization(
            @RequestHeader("Authorization") String bearerToken,
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-Organization-Id") String organizationIdHeader,
            @PathVariable("organizationId") UUID organizationId,
            @RequestBody GovernanceActionRequest request);

    @PostExchange("/api/organizations/{organizationId}/services")
    Mono<ApiResponse<Object>> subscribeService(
            @RequestHeader("Authorization") String bearerToken,
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-Organization-Id") String organizationIdHeader,
            @PathVariable("organizationId") UUID organizationId,
            @RequestBody SubscribeServiceRequest request);

    record OrganizationResponse(
            UUID id,
            UUID tenantId,
            UUID businessActorId,
            String governanceStatus,
            String code,
            String displayName,
            String legalName,
            String status,
            boolean isActive
    ) {}

    record CreateOrganizationRequest(
            UUID businessActorId,
            String code,
            String service,
            String shortName,
            String longName
    ) {}

    record GovernanceActionRequest(String reason) {}

    record SubscribeServiceRequest(
            String serviceCode,
            int requestQuotaLimit,
            int requestQuotaWindowSeconds
    ) {}

    @GetExchange("/api/organizations/{organizationId}/agencies")
    Mono<KernelAuthApiClient.ApiResponse<List<AgencyResponse>>> listAgencies(
            @RequestHeader("Authorization") String bearerToken,
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-Organization-Id") String organizationIdHeader,
            @PathVariable("organizationId") UUID organizationId);

    @PostExchange("/api/organizations/{organizationId}/agencies")
    Mono<KernelAuthApiClient.ApiResponse<AgencyResponse>> createAgency(
            @RequestHeader("Authorization") String bearerToken,
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-Organization-Id") String organizationIdHeader,
            @PathVariable("organizationId") UUID organizationId,
            @RequestBody CreateAgencyRequest request);

    record AgencyResponse(UUID id, UUID organizationId, String code, String name, String governanceStatus) {}

    record CreateAgencyRequest(String code, String name) {}
}
