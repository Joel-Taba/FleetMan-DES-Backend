package com.yowyob.fleet.infrastructure.config;

import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.FakeAuthAdapter;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.KernelAuthAdapter;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.RemoteAuthAdapter;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.client.AuthApiClient;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.client.KernelAdminApiClient;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.client.KernelAuthApiClient;
import com.yowyob.fleet.infrastructure.config.KernelTokenHolder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class AuthConfig {

    /** Mode fake : comptes en mémoire (développement sans Kernel) */
    @Bean
    @ConditionalOnProperty(name = "application.auth.mode", havingValue = "fake")
    public AuthPort fakeAuthPort() {
        return new FakeAuthAdapter();
    }

    /** Mode kernel : authentification réelle via Kernel RT-Comops */
    @Bean
    @ConditionalOnProperty(name = "application.auth.mode", havingValue = "kernel")
    public AuthPort kernelAuthPort(
            KernelAuthApiClient kernelAuthApiClient,
            KernelAdminApiClient kernelAdminApiClient,
            KernelTokenHolder kernelTokenHolder,
            @Qualifier("kernelWebClient") WebClient kernelWebClient) {
        return new KernelAuthAdapter(kernelAuthApiClient, kernelAdminApiClient, kernelTokenHolder, kernelWebClient);
    }

    /** Mode remote : ancien service d'authentification Pynfi/TraMaSys */
    @Bean
    @ConditionalOnProperty(name = "application.auth.mode", havingValue = "remote", matchIfMissing = false)
    public AuthPort remoteAuthPort(AuthApiClient authApiClient, WebClient.Builder webClientBuilder) {
        return new RemoteAuthAdapter(authApiClient, webClientBuilder);
    }
}