package com.yowyob.fleet.domain.ports.in;

import com.yowyob.fleet.domain.ports.out.AuthPort;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface ManageSuperAdminUseCase {
    Mono<AuthPort.AuthResponse> createAdmin(AuthUseCase.RegisterCommand command);
    Flux<AuthPort.UserDetail> listAdmins(String token);
    Mono<AuthPort.UserDetail> getAdminDetails(UUID adminId, String token);
    Mono<Void> toggleAdminStatus(UUID adminId, UUID requesterId);
}