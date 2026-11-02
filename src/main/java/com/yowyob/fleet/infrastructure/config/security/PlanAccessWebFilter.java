package com.yowyob.fleet.infrastructure.config.security;

import com.yowyob.fleet.application.service.PlanLimitGuard;
import com.yowyob.fleet.domain.exception.DomainException;
import com.yowyob.fleet.domain.model.PlanFeature;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Applique l'abonnement de l'entreprise côté serveur aux requêtes de ses administrateurs et
 * gestionnaires : abonnement utilisable (validé, ni suspendu, ni expiré au-delà de la grâce) et
 * fonctionnalité incluse dans le plan. Les routes nécessaires pour se connecter, consulter son
 * profil et renouveler l'abonnement restent accessibles. Le super-admin n'est pas concerné.
 */
@Component
@Order(10)
@RequiredArgsConstructor
public class PlanAccessWebFilter implements WebFilter {

    /** Routes ouvertes même sans abonnement utilisable (connexion, profil, renouvellement). */
    private static final List<String> ALWAYS_ALLOWED = List.of(
            "/api/v1/auth", "/api/v1/account", "/api/v1/public", "/api/v1/health",
            "/api/v1/fleet-managers/me", "/api/v1/files", "/api/v1/notifications",
            "/api/v1/organization/subscription", "/api/v1/organization/profile");

    /** Préfixe de route → fonctionnalité du plan exigée. */
    private static final Map<String, PlanFeature> FEATURE_BY_PREFIX = Map.ofEntries(
            Map.entry("/api/v1/trips", PlanFeature.TRIPS),
            Map.entry("/api/v1/schedules", PlanFeature.SCHEDULES),
            Map.entry("/api/v1/assignments", PlanFeature.ASSIGNMENTS),
            Map.entry("/api/v1/operations/", PlanFeature.OPERATIONS),
            Map.entry("/api/v1/preventive-maintenance/", PlanFeature.OPERATIONS),
            Map.entry("/api/v1/kpis", PlanFeature.KPI_REPORTS),
            Map.entry("/api/v1/geofence", PlanFeature.GEOFENCING),
            Map.entry("/api/v1/alerts/rules", PlanFeature.ALERTS),
            Map.entry("/api/v1/scoring", PlanFeature.SCORING));

    private static final Set<String> COMPANY_ROLES = Set.of("ROLE_FLEET_ADMIN", "ROLE_FLEET_MANAGER");

    private final PlanLimitGuard planLimitGuard;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith("/api/v1/") || ALWAYS_ALLOWED.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(this::isCompanyUser)
                .flatMap(auth -> planLimitGuard.assertAccess(managerId(auth), requiredFeature(path).map(Enum::name).orElse(null))
                        .thenReturn(true))
                .onErrorResume(DomainException.class, e -> reject(exchange, e).thenReturn(false))
                .defaultIfEmpty(true)
                .flatMap(allowed -> Boolean.TRUE.equals(allowed) ? chain.filter(exchange) : Mono.empty());
    }

    static Optional<PlanFeature> requiredFeature(String path) {
        if (path.contains("/documents") && !path.startsWith("/api/v1/admin")) {
            return Optional.of(PlanFeature.DOCUMENTS);
        }
        // Déclarer et suivre un incident relève de la sécurité : ouvert à toutes les offres.
        if (path.startsWith("/api/v1/operations/incidents")) {
            return Optional.empty();
        }
        return FEATURE_BY_PREFIX.entrySet().stream()
                .filter(e -> path.startsWith(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    private boolean isCompanyUser(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof AuthPort.UserDetail)) {
            return false;
        }
        var authorities = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        return authorities.stream().anyMatch(COMPANY_ROLES::contains)
                && !authorities.contains("ROLE_FLEET_SUPER_ADMIN");
    }

    private static java.util.UUID managerId(Authentication auth) {
        return ((AuthPort.UserDetail) auth.getPrincipal()).id();
    }

    private static Mono<Void> reject(ServerWebExchange exchange, DomainException e) {
        var response = exchange.getResponse();
        response.setStatusCode(e.getStatus());
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String detail = e.getMessage().replace("\\", "\\\\").replace("\"", "\\\"");
        String json = "{\"detail\":\"" + detail + "\",\"code\":\"" + e.getBusinessCode()
                + "\",\"status\":" + e.getStatus().value() + "}";
        DataBuffer buffer = response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
