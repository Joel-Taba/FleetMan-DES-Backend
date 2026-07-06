package com.yowyob.fleet.domain.ports.out;

import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Port sortant vers organization-core du Kernel RT-Comops.
 * Une flotte FleetMan = une organisation Kernel.
 */
public interface ExternalOrganizationPort {

    boolean isEnabled();

    Mono<OrganizationInfo> getOrganization(UUID organizationId, String bearerToken);

    Mono<OrganizationInfo> createOrganization(CreateOrganizationCommand command, String bearerToken);

    Mono<Void> approveOrganization(UUID organizationId, String reason, String bearerToken);

    Mono<Void> subscribeService(UUID organizationId, String serviceCode, String bearerToken);

    record OrganizationInfo(
            UUID id,
            UUID tenantId,
            String displayName,
            String governanceStatus,
            boolean active
    ) {}

    record CreateOrganizationCommand(
            UUID businessActorId,
            String code,
            String shortName,
            String longName,
            String service
    ) {}
}
