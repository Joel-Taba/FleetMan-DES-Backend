package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.AdminException;
import com.yowyob.fleet.domain.ports.in.AuthUseCase;
import com.yowyob.fleet.domain.ports.in.ManageAdminUseCase;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.domain.ports.out.ExternalActorPort;
import com.yowyob.fleet.domain.ports.out.FleetManagerPersistencePort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.FleetManagerEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.UserLocalEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.FleetManagerR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.FleetR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.PlatformAdminR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.UserLocalR2dbcRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.ManagerAccountResponse;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService implements ManageAdminUseCase {

    private final UserLocalR2dbcRepository userRepo;
    private final FleetManagerR2dbcRepository managerRepo;
    private final AuthPort authPort;
    private final ExternalActorPort externalActorPort;
    private final FleetManagerPersistencePort managerPersistencePort;
    private final FleetR2dbcRepository fleetRepo;
    private final PlatformAdminR2dbcRepository platformAdminRepo;
    private final com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.CompanyR2dbcRepository companyRepo;
    private final org.springframework.r2dbc.core.DatabaseClient db;

    @Override
    public Flux<AuthPort.UserDetail> listFleetManagers(UUID requesterId, boolean isSuperAdmin) {
        Flux<FleetManagerEntity> source = isSuperAdmin
                ? managerRepo.findAll()
                : resolveLocalUserId(requesterId)
                        .flatMapMany(managerRepo::findByCreatedByAdminId);

        return source
                .flatMap(mgr -> userRepo.findById(mgr.getUserId())
                        .filter(user -> user.getDeletedAt() == null)
                        .map(user -> toManagerDetail(user, mgr)))
                .sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(
                        safeName(a), safeName(b)));
    }

    @Override
    public Mono<AuthPort.UserDetail> getManagerDetails(UUID managerId, UUID requesterId, boolean isSuperAdmin) {
        return loadOwnedManager(managerId, requesterId, isSuperAdmin)
                .map(pair -> toManagerDetail(pair.user(), pair.manager()));
    }

    /**
     * Clôture (au lieu d'une suppression qui masquait le compte et laissait courir son
     * abonnement) : seulement sans flotte ; le compte est désactivé, l'historique conservé.
     */
    @Override
    public Mono<Void> deleteManager(UUID managerId, UUID requesterId, boolean isSuperAdmin) {
        return loadOwnedManager(managerId, requesterId, isSuperAdmin)
                .flatMap(pair -> fleetRepo.countActiveByManagerId(pair.user().getId())
                        .flatMap(count -> count > 0
                                ? Mono.<Void>error(AdminException.managerStillHasFleets(count))
                                : setAccountStatus(pair, "CLOSED", null)))
                .doOnSuccess(u -> log.info("🔒 Gestionnaire {} clôturé", managerId));
    }

    @Override
    public Mono<Void> suspendManager(UUID managerId, String reason, UUID requesterId, boolean isSuperAdmin) {
        if (reason == null || reason.isBlank()) {
            return Mono.error(AdminException.suspensionReasonRequired());
        }
        return loadOwnedManager(managerId, requesterId, isSuperAdmin)
                .flatMap(pair -> setAccountStatus(pair, "SUSPENDED", reason.trim()))
                .doOnSuccess(u -> log.info("⏸️ Gestionnaire {} suspendu", managerId));
    }

    @Override
    public Mono<Void> reactivateManager(UUID managerId, UUID requesterId, boolean isSuperAdmin) {
        return loadOwnedManager(managerId, requesterId, isSuperAdmin)
                .flatMap(pair -> setAccountStatus(pair, "ACTIVE", null));
    }

    /** Ancien interrupteur actif / inactif : suspend ou réactive. */
    @Override
    public Mono<Void> toggleManagerStatus(UUID managerId, UUID requesterId, boolean isSuperAdmin) {
        return loadOwnedManager(managerId, requesterId, isSuperAdmin)
                .flatMap(pair -> "ACTIVE".equals(accountStatusOf(pair.manager()))
                        ? setAccountStatus(pair, "SUSPENDED", "Suspendu par l'administrateur")
                        : setAccountStatus(pair, "ACTIVE", null));
    }

    private Mono<Void> setAccountStatus(OwnedManager pair, String status, String reason) {
        if ("CLOSED".equals(accountStatusOf(pair.manager()))) {
            return Mono.error(AdminException.managerClosed());
        }
        UserLocalEntity user = pair.user();
        user.setActive("ACTIVE".equals(status));
        user.setNewRecord(false);
        var update = db.sql("""
                        UPDATE fleet.fleet_managers
                        SET account_status = :status, suspension_reason = :reason, status_changed_at = NOW(),
                            closed_at = CASE WHEN :status = 'CLOSED' THEN NOW() ELSE closed_at END
                        WHERE user_id = :id
                        """)
                .bind("status", status)
                .bind("id", user.getId());
        update = reason != null ? update.bind("reason", reason) : update.bindNull("reason", String.class);
        return userRepo.save(user).then(update.fetch().rowsUpdated()).then();
    }

    private static String accountStatusOf(FleetManagerEntity manager) {
        return manager.getAccountStatus() != null ? manager.getAccountStatus() : "ACTIVE";
    }

    @Override
    public Flux<ManagerAccountResponse> listManagerAccounts(UUID requesterId, boolean isSuperAdmin) {
        Flux<FleetManagerEntity> source = isSuperAdmin
                ? managerRepo.findAll()
                : resolveLocalUserId(requesterId).flatMapMany(managerRepo::findByCreatedByAdminId);
        return source
                .flatMap(mgr -> Mono.zip(
                        userRepo.findById(mgr.getUserId()).filter(user -> user.getDeletedAt() == null),
                        fleetRepo.countActiveByManagerId(mgr.getUserId()).defaultIfEmpty(0L),
                        companyRepo.findById(mgr.getCreatedByAdminId())
                                .map(c -> c.getName() != null ? c.getName() : "")
                                .defaultIfEmpty(""))
                        .map(t -> new ManagerAccountResponse(
                                t.getT1().getId(), t.getT1().getUsername(), t.getT1().getEmail(), t.getT1().getPhone(),
                                t.getT1().getFirstName(), t.getT1().getLastName(), List.of("FLEET_MANAGER"),
                                t.getT1().getPhotoUrl(), t.getT3(), t.getT1().isActive(), t.getT1().getLastLoginAt(),
                                accountStatusOf(mgr), mgr.getSuspensionReason(), mgr.getStatusChangedAt(), t.getT2())))
                .sort(java.util.Comparator
                        .comparing((ManagerAccountResponse m) -> "CLOSED".equals(m.accountStatus()))
                        .thenComparing(m -> ((m.firstName() != null ? m.firstName() : "") + " "
                                + (m.lastName() != null ? m.lastName() : "")).trim(), String.CASE_INSENSITIVE_ORDER));
    }

    @Override
    public Mono<AuthPort.UserDetail> createManager(
            AuthUseCase.RegisterCommand command, String companyName, UUID createdByAdminId) {
        return requireExistingPlatformAdmin(createdByAdminId)
                .flatMap(localAdminId -> authPort.registerInRemote(command)
                        .flatMap(res -> {
                            UUID userId = res.user().id();

                            Mono<Void> roleFlow = externalActorPort.assignPlatformRole(userId, "FLEET_MANAGER")
                                    .onErrorResume(e -> {
                                        log.warn("⚠️ Assignation rôle FLEET_MANAGER ignorée pour {} : {}", userId, e.getMessage());
                                        return Mono.empty();
                                    });

                            return roleFlow
                                    .then(syncNewManagerIdentity(res.user()))
                                    .flatMap(detail -> managerPersistencePort
                                            .createProfile(userId, companyName, localAdminId)
                                            .thenReturn(detail));
                        }));
    }

    private Mono<UUID> requireExistingPlatformAdmin(UUID adminId) {
        if (adminId == null) {
            return Mono.error(AdminException.createdByMustBeAdmin());
        }
        return resolveLocalUserId(adminId)
                .flatMap(localId -> platformAdminRepo.existsById(localId)
                        .flatMap(exists -> exists
                                ? Mono.just(localId)
                                : Mono.error(AdminException.createdByMustBeAdmin())));
    }

    private Mono<UUID> resolveLocalUserId(UUID id) {
        if (id == null) {
            return Mono.empty();
        }
        return userRepo.findById(id)
                .switchIfEmpty(Mono.defer(() -> userRepo.findByKernelId(id)))
                .map(UserLocalEntity::getId)
                .defaultIfEmpty(id);
    }

    private Mono<OwnedManager> loadOwnedManager(UUID managerId, UUID requesterId, boolean isSuperAdmin) {
        return userRepo.findById(managerId)
                .switchIfEmpty(Mono.defer(() -> userRepo.findByKernelId(managerId)))
                .filter(user -> user.getDeletedAt() == null)
                .switchIfEmpty(Mono.error(AdminException.managerNotFound()))
                .flatMap(user -> managerRepo.findById(user.getId())
                        .switchIfEmpty(Mono.error(AdminException.managerNotFound()))
                        .flatMap(mgr -> {
                            if (isSuperAdmin) {
                                return Mono.just(new OwnedManager(user, mgr));
                            }
                            return resolveLocalUserId(requesterId)
                                    .filter(localAdminId -> localAdminId.equals(mgr.getCreatedByAdminId()))
                                    .switchIfEmpty(Mono.error(AdminException.managerNotFound()))
                                    .thenReturn(new OwnedManager(user, mgr));
                        }));
    }

    private record OwnedManager(UserLocalEntity user, FleetManagerEntity manager) {}

    private Mono<AuthPort.UserDetail> syncNewManagerIdentity(AuthPort.UserDetail remote) {
        return userRepo.findById(remote.id())
                .switchIfEmpty(Mono.defer(() -> {
                    UserLocalEntity n = UserLocalEntity.builder()
                            .id(remote.id()).username(remote.username()).email(remote.email())
                            .phone(remote.phone())
                            .firstName(remote.firstName()).lastName(remote.lastName())
                            .isActive(true).build();
                    n.setNewRecord(true);
                    return userRepo.save(n);
                }))
                .thenReturn(remote);
    }

    private static String safeName(AuthPort.UserDetail u) {
        String name = ((u.firstName() != null ? u.firstName() : "")
                + " " + (u.lastName() != null ? u.lastName() : "")).trim();
        return name.isBlank() ? (u.username() != null ? u.username() : "") : name;
    }

    private AuthPort.UserDetail toManagerDetail(UserLocalEntity user, FleetManagerEntity mgr) {
        return new AuthPort.UserDetail(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPhone(),
                user.getFirstName(),
                user.getLastName(),
                "FLEET_MANAGEMENT",
                List.of("FLEET_MANAGER"),
                List.of(),
                user.getPhotoUrl(),
                mgr.getCompanyName(),
                null,
                null,
                user.isActive(),
                user.getLastLoginAt()
        );
    }
}
