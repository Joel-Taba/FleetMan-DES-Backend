package com.yowyob.fleet.infrastructure.config;

import com.yowyob.fleet.domain.model.FleetRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Vérifie au démarrage que les 4 rôles {@link FleetRole} sont
 * résolvables côté Kernel — soit via surcharge de configuration, soit dynamiquement via
 * GET /api/administration/roles (voir {@link KernelRoleRegistry}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "application.auth.mode", havingValue = "kernel")
public class KernelRolesStartupValidator {

    private static final String[] FLEET_ROLES = FleetRole.codes();

    private final KernelRoleRegistry roleRegistry;

    @EventListener(ApplicationReadyEvent.class)
    public void validateRoleIds() {
        for (String role : FLEET_ROLES) {
            roleRegistry.resolve(role)
                    .doOnSuccess(id -> log.info("✅ [KERNEL ROLES] {} → {}", role, id))
                    .switchIfEmpty(Mono.fromRunnable(() -> log.warn(
                            "⚠️ [KERNEL ROLES] {} introuvable (ni configuré, ni résolu via "
                                    + "GET /api/administration/roles). assignRole sera ignoré pour ce rôle.",
                            role)))
                    .onErrorResume(e -> {
                        log.warn("⚠️ [KERNEL ROLES] Résolution de {} impossible au démarrage : {}",
                                role, e.getMessage());
                        return Mono.empty();
                    })
                    .subscribe();
        }
    }
}
