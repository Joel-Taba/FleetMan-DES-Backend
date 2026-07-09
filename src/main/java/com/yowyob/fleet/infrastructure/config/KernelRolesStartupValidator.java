package com.yowyob.fleet.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Vérifie au démarrage que les role IDs Kernel sont configurés pour assignRole.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "application.auth.mode", havingValue = "kernel")
public class KernelRolesStartupValidator {

    @Value("${application.kernel.roles.fleet-driver-id:}")
    private String fleetDriverRoleId;

    @Value("${application.kernel.roles.fleet-manager-id:}")
    private String fleetManagerRoleId;

    @EventListener(ApplicationReadyEvent.class)
    public void validateRoleIds() {
        if (isBlank(fleetDriverRoleId)) {
            log.warn("""
                    ⚠️ [KERNEL ROLES] KERNEL_ROLE_FLEET_DRIVER_ID non configuré.
                    assignRole sera ignoré pour les chauffeurs.
                    Récupérez l'UUID du rôle FLEET_DRIVER dans le Kernel et exportez :
                    export KERNEL_ROLE_FLEET_DRIVER_ID=<uuid>
                    """);
        } else {
            log.info("✅ [KERNEL ROLES] FLEET_DRIVER roleId configuré");
        }
        if (isBlank(fleetManagerRoleId)) {
            log.warn("""
                    ⚠️ [KERNEL ROLES] KERNEL_ROLE_FLEET_MANAGER_ID non configuré.
                    assignRole sera ignoré pour les managers.
                    export KERNEL_ROLE_FLEET_MANAGER_ID=<uuid>
                    """);
        } else {
            log.info("✅ [KERNEL ROLES] FLEET_MANAGER roleId configuré");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
