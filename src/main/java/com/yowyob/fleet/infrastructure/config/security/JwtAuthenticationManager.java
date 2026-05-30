package com.yowyob.fleet.infrastructure.config.security;

import com.yowyob.fleet.domain.exception.AuthException;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.UserLocalR2dbcRepository;
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

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        String token = (String) authentication.getCredentials();

        return authPort.getUserProfile(token)
                .flatMap(userDetail -> 
                    // VÉRIFICATION LOCALE SOUVERAINE
                    userRepo.findById(userDetail.id())
                        .flatMap(localUser -> {
                            if (localUser.getDeletedAt() != null) {
                                return Mono.error(AuthException.accountDeleted());
                            }
                            if (!localUser.isActive()) {
                                return Mono.error(AuthException.accountLocked());
                            }
                            return Mono.just(userDetail);
                        })
                        .defaultIfEmpty(userDetail) // Si 1er appel après register, local peut être vide
                )
                .map(userDetail -> {
                    var authorities = userDetail.roles().stream()
                            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                            .collect(Collectors.toList());

                    return new UsernamePasswordAuthenticationToken(userDetail, token, authorities);
                })
                .cast(Authentication.class) 
                .onErrorResume(e -> {
                    log.warn("🔐 Accès refusé : {}", e.getMessage());
                    return Mono.error(e);
                });
    }
}