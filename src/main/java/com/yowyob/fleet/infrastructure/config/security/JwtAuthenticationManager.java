package com.yowyob.fleet.infrastructure.config.security;

import com.yowyob.fleet.domain.exception.AuthException;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.UserLocalR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.DriverR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.VehicleLocalR2dbcRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationManager implements ReactiveAuthenticationManager {

    private final AuthPort authPort;
    private final UserLocalR2dbcRepository userRepo;
    private final DriverR2dbcRepository driverRepo;
    private final VehicleLocalR2dbcRepository vehicleLocalRepo;

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        String token = (String) authentication.getCredentials();

        return authPort.getUserProfile(token)
                .flatMap(this::resolveAndRebind)
                .map(userDetail -> {
                    var authorities = userDetail.roles().stream()
                            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                            .collect(Collectors.toList());
                    return new UsernamePasswordAuthenticationToken(
                            userDetail,
                            token,
                            authorities);
                })
                .cast(Authentication.class)
                .onErrorResume(e -> {
                    log.warn("🔐 Accès refusé : {}", e.getMessage());
                    return Mono.error(e);
                });
    }

    private Mono<AuthPort.UserDetail> resolveAndRebind(AuthPort.UserDetail userDetail) {
        return userRepo.findById(userDetail.id())
                .switchIfEmpty(Mono.defer(() -> userRepo.findByKernelId(userDetail.id())))
                .flatMap(localUser -> {
                    if (localUser.getDeletedAt() != null) {
                        return Mono.error(AuthException.accountDeleted());
                    }
                    if (!localUser.isActive()) {
                        log.info("🔓 [AUTO-ACTIVATE] Réactivation automatique de l'utilisateur local id={}",
                                localUser.getId());
                        localUser.setActive(true);
                        localUser.setApprovalStatus("APPROVED");
                        localUser.setNew(false);
                        return userRepo.save(localUser)
                                .map(saved -> rebindToLocalId(userDetail, saved));
                    }
                    log.debug("✅ [UUID REBIND] Kernel id={} → local id={}", userDetail.id(), localUser.getId());
                    return Mono.just(rebindToLocalId(userDetail, localUser));
                })
                .flatMap(rebound -> {
                    if (rebound.roles().contains("FLEET_DRIVER")) {
                        return vehicleLocalRepo.findByCurrentDriverId(rebound.id())
                                .map(v -> v.getId().toString())
                                .next()
                                .flatMap(vId -> Mono.just(new AuthPort.UserDetail(
                                        rebound.id(), rebound.username(), rebound.email(), rebound.phone(),
                                        rebound.firstName(), rebound.lastName(), rebound.service(),
                                        rebound.roles(), rebound.permissions(), rebound.photoUrl(),
                                        rebound.companyName(), rebound.licenceNumber(),
                                        vId,
                                        rebound.isActive(), rebound.lastLoginAt())))
                                .switchIfEmpty(Mono.defer(() -> {
                                    return driverRepo.findById(rebound.id())
                                            .map(driver -> {
                                                if (driver.getAssignedVehicleId() != null) {
                                                    return new AuthPort.UserDetail(
                                                            rebound.id(), rebound.username(), rebound.email(),
                                                            rebound.phone(),
                                                            rebound.firstName(), rebound.lastName(), rebound.service(),
                                                            rebound.roles(), rebound.permissions(), rebound.photoUrl(),
                                                            rebound.companyName(), rebound.licenceNumber(),
                                                            driver.getAssignedVehicleId().toString(),
                                                            rebound.isActive(), rebound.lastLoginAt());
                                                }
                                                return rebound;
                                            })
                                            .defaultIfEmpty(rebound);
                                }));
                    }
                    return Mono.just(rebound);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("ℹ️ [UUID REBIND] Aucun compte local pour Kernel id={}, accepté tel quel (premier login)",
                            userDetail.id());
                    return Mono.just(userDetail);
                }));
    }

    private AuthPort.UserDetail rebindToLocalId(AuthPort.UserDetail kernelUser,
            com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.UserLocalEntity localUser) {
        return new AuthPort.UserDetail(
                localUser.getId(),
                kernelUser.username(),
                kernelUser.email(),
                kernelUser.phone(),
                kernelUser.firstName(),
                kernelUser.lastName(),
                kernelUser.service(),
                kernelUser.roles(),
                kernelUser.permissions(),
                kernelUser.photoUrl(),
                kernelUser.companyName(),
                kernelUser.licenceNumber(),
                kernelUser.vehicleId(),
                localUser.isActive(),
                kernelUser.lastLoginAt());
    }
}