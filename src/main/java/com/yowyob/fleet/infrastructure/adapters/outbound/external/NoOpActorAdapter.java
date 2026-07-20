package com.yowyob.fleet.infrastructure.adapters.outbound.external;

import com.yowyob.fleet.domain.ports.out.ExternalActorPort;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** No-op quand le mode auth n'est pas kernel. */
public class NoOpActorAdapter implements ExternalActorPort {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public Mono<UUID> provisionDriverActor(
            UUID kernelUserId, String email, String firstName, String lastName,
            UUID organizationId, String fleetRole) {
        return Mono.empty();
    }

    @Override
    public Mono<Void> assignPlatformRole(UUID kernelUserId, String fleetRole) {
        return Mono.empty();
    }
}
