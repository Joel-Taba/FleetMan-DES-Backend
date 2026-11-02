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
    /** Clôture du compte (historique conservé) : refusée tant qu'il a des flottes. */
    Mono<Void> deleteManager(UUID managerId, UUID requesterId, boolean isSuperAdmin);

    /** Gestionnaires de l'entreprise avec l'état de leur compte et leur nombre de flottes. */
    Flux<com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.ManagerAccountResponse> listManagerAccounts(
            UUID requesterId, boolean isSuperAdmin);

    /** Suspension (motif obligatoire) : connexion bloquée, flottes gelées jusqu'à réactivation ou transfert. */
    Mono<Void> suspendManager(UUID managerId, String reason, UUID requesterId, boolean isSuperAdmin);

    Mono<Void> reactivateManager(UUID managerId, UUID requesterId, boolean isSuperAdmin);
}