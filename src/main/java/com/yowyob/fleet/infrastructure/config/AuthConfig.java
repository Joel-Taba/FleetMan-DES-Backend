package com.yowyob.fleet.infrastructure.config;

import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.FakeAuthAdapter;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.RemoteAuthAdapter;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.client.AuthApiClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class AuthConfig {

    @Bean
    @ConditionalOnProperty(name = "application.auth.mode", havingValue = "fake")
    public AuthPort fakeAuthPort() {
        return new FakeAuthAdapter();
    }

    @Bean
    @ConditionalOnProperty(name = "application.auth.mode", havingValue = "remote", matchIfMissing = true)
    public AuthPort remoteAuthPort(AuthApiClient authApiClient, WebClient.Builder webClientBuilder) {
        // Correction : On passe le builder nécessaire pour le Multipart
        return new RemoteAuthAdapter(authApiClient, webClientBuilder);
    }
}