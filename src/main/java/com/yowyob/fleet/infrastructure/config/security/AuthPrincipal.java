package com.yowyob.fleet.infrastructure.config.security;

import com.yowyob.fleet.domain.model.FleetRole;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.UUID;

public final class AuthPrincipal {

    private AuthPrincipal() {}

    public static AuthPort.UserDetail user(Authentication auth) {
        return (AuthPort.UserDetail) auth.getPrincipal();
    }

    public static UUID userId(Authentication auth) {
        return user(auth).id();
    }

    public static boolean isFleetAdmin(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_" + FleetRole.FLEET_ADMIN.name())
                        || a.equals("ROLE_" + FleetRole.FLEET_SUPER_ADMIN.name())
                        || a.equals("ROLE_ADMIN")
                        || a.equals("ROLE_SUPER_ADMIN"));
    }

    public static boolean isFleetManager(Authentication auth) {
        return hasRole(auth, FleetRole.FLEET_MANAGER.name());
    }

    public static boolean isFleetDriver(Authentication auth) {
        return hasRole(auth, FleetRole.FLEET_DRIVER.name());
    }

    private static boolean hasRole(Authentication auth, String role) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_" + role) || a.equals(role));
    }
}
