package com.yowyob.fleet.domain.ports.in;

import com.yowyob.fleet.domain.ports.out.AuthPort;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface ManageAdminUseCase {
    Flux<AuthPort.UserDetail> listFleetManagers(UUID requesterId, boolean isSuperAdmin);
    Mono<AuthPort.UserDetail> getManagerDetails(UUID managerId, UUID requesterId, boolean isSuperAdmin);
    Mono<Void> toggleManagerStatus(UUID managerId, UUID requesterId, boolean isSuperAdmin);
    /** Créé directement un compte FLEET_MANAGER actif, rattaché à l'admin créateur. */
    Mono<AuthPort.UserDetail> createManager(AuthUseCase.RegisterCommand command, String companyName, UUID createdByAdminId);
    /** Suppression logique (deleted_at) : bloquée si des flottes lui sont encore assignées. */
    Mono<Void> deleteManager(UUID managerId, UUID requesterId, boolean isSuperAdmin);
}