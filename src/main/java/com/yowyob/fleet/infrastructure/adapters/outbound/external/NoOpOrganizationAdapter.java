package com.yowyob.fleet.infrastructure.adapters.outbound.external;

import com.yowyob.fleet.domain.ports.out.ExternalOrganizationPort;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Désactivé en mode fake-auth — pas de synchronisation Kernel. */
public class NoOpOrganizationAdapter implements ExternalOrganizationPort {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public Mono<OrganizationInfo> getOrganization(UUID organizationId, String bearerToken) {
        return Mono.empty();
    }

    @Override
    public Mono<OrganizationInfo> createOrganization(CreateOrganizationCommand command, String bearerToken) {
        return Mono.error(new UnsupportedOperationException("organization-core non disponible en mode fake"));
    }

    @Override
    public Mono<Void> approveOrganization(UUID organizationId, String reason, String bearerToken) {
        return Mono.empty();
    }

    @Override
    public Mono<Void> subscribeService(UUID organizationId, String serviceCode, String bearerToken) {
        return Mono.empty();
    }
}
