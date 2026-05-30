package com.yowyob.fleet.domain.ports.in;

import com.yowyob.fleet.domain.ports.out.AuthPort;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface ManageAdminUseCase {
    Flux<AuthPort.UserDetail> listFleetManagers(String token);
    Mono<AuthPort.UserDetail> getManagerDetails(UUID managerId, String token, boolean isSuperAdmin);
    Mono<Void> toggleManagerStatus(UUID managerId, UUID requesterId, boolean isSuperAdmin);
}