package com.yowyob.fleet.infrastructure.config;

import com.yowyob.fleet.domain.exception.DomainException;
import com.yowyob.fleet.infrastructure.config.security.BearerTokenServerAuthenticationConverter;
import com.yowyob.fleet.infrastructure.config.security.IdempotencyWebFilter;
import com.yowyob.fleet.infrastructure.config.security.JwtAuthenticationManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.util.matcher.AndServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.NegatedServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private final JwtAuthenticationManager authenticationManager;
    private final BearerTokenServerAuthenticationConverter authenticationConverter;
    private final IdempotencyWebFilter idempotencyWebFilter;

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {

        AuthenticationWebFilter jwtFilter = new AuthenticationWebFilter(authenticationManager);
        jwtFilter.setServerAuthenticationConverter(authenticationConverter);
        jwtFilter.setAuthenticationFailureHandler((webFilterExchange, exception) -> {
            Throwable cause = exception;
            while (cause.getCause() != null && cause.getCause() != cause) {
                cause = cause.getCause();
            }
            HttpStatus status = HttpStatus.UNAUTHORIZED;
            String code = "AUTH_001";
            String message = cause.getMessage() != null ? cause.getMessage() : "Authentification refusée";
            if (cause instanceof DomainException domainException) {
                status = domainException.getStatus();
                code = domainException.getBusinessCode();
                message = domainException.getMessage();
            }
            var response = webFilterExchange.getExchange().getResponse();
            response.setStatusCode(status);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            String escaped = message.replace("\\", "\\\\").replace("\"", "\\\"");
            String json = "{\"detail\":\"" + escaped + "\",\"code\":\"" + code + "\",\"status\":" + status.value() + "}";
            DataBuffer buffer = response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
            return response.writeWith(Mono.just(buffer));
        });

        jwtFilter.setRequiresAuthenticationMatcher(
                new AndServerWebExchangeMatcher(
                        ServerWebExchangeMatchers.pathMatchers("/api/v1/**"),
                        new NegatedServerWebExchangeMatcher(
                                // GET fichiers publics anonymes ; POST upload doit pouvoir lire le Bearer
                                ServerWebExchangeMatchers.pathMatchers(
                                        org.springframework.http.HttpMethod.GET, "/api/v1/files/**")
                        )
                )
        );

        return http
                // 1. ACTIVATION DU CORS ICI
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                // Le viewer PDF intégré (iframe) du front sert /api/v1/files/**, servi
                // depuis une origine distincte (autre port en dev, sous-domaine en prod).
                // X-Frame-Options DENY (défaut Spring Security) bloque cet embed même en
                // SAMEORIGIN puisque front et back n'ont jamais la même origine ici — cette
                // API ne sert que du JSON/fichiers, pas de pages HTML sensibles au clickjacking.
                .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.disable()))
                .authenticationManager(authenticationManager)

                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((exchange, e) ->
                                Mono.fromRunnable(() -> exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED)))
                        .accessDeniedHandler((exchange, e) ->
                                Mono.fromRunnable(() -> exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN)))
                )

                .authorizeExchange(exchanges -> exchanges
                        // A. ROUTES PUBLIQUES
                        .pathMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/webjars/**",
                                "/actuator/**",
                                "/api/v1/health/**",
                                "/api/v1/auth/**",
                                "/api/v1/public/**"
                        ).permitAll()
                        .pathMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/files/**").permitAll()
                        // Upload inscription publique (signup) — catégorie restreinte dans le contrôleur
                        .pathMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/files/upload").permitAll()
                        // B. OPTIONS (Indispensable pour le pre-flight CORS)
                        .pathMatchers(org.springframework.http.HttpMethod.OPTIONS).permitAll()
                        .anyExchange().authenticated()
                )
                .addFilterAt(jwtFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .addFilterAfter(idempotencyWebFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    // 2. DÉFINITION DE LA CONFIGURATION CORS
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Autoriser toutes les origines (Pour le dev local sur réseau mobile/wifi)
        configuration.setAllowedOriginPatterns(List.of("*")); 
        
        // Autoriser toutes les méthodes
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        
        // Autoriser tous les headers (notamment Authorization)
        configuration.setAllowedHeaders(List.of("*"));
        
        // Autoriser l'envoi de cookies/credentials (si besoin)
        configuration.setAllowCredentials(true);

        // Sans maxAge, le navigateur ne met JAMAIS en cache la réponse au pre-flight OPTIONS
        // et en renvoie un avant CHAQUE requête POST/PUT/DELETE/PATCH — sur la même connexion
        // keep-alive, immédiatement suivi de la requête réelle. Sous Reactor Netty, cet
        // enchaînement quasi simultané (OPTIONS puis méthode réelle) provoque parfois une
        // désynchronisation de lecture du corps de la VRAIE requête ("No request body"),
        // qui crashe ensuite en réponse déjà validée → connexion fermée. Mettre en cache le
        // pre-flight (1h) élimine la quasi-totalité de ces doublons OPTIONS+requête.
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}