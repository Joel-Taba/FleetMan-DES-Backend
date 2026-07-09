package com.yowyob.fleet.infrastructure.config;

import com.yowyob.fleet.infrastructure.config.security.BearerTokenServerAuthenticationConverter;
import com.yowyob.fleet.infrastructure.config.security.JwtAuthenticationManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
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

import java.util.List;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private final JwtAuthenticationManager authenticationManager;
    private final BearerTokenServerAuthenticationConverter authenticationConverter;

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {

        AuthenticationWebFilter jwtFilter = new AuthenticationWebFilter(authenticationManager);
        jwtFilter.setServerAuthenticationConverter(authenticationConverter);

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

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}