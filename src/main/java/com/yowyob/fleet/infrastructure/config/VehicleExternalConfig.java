package com.yowyob.fleet.infrastructure.config;

import com.yowyob.fleet.domain.ports.out.ExternalVehiclePort;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.FakeVehicleAdapter;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.VehicleApiAdapter;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.client.VehicleApiClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class VehicleExternalConfig {

    @Bean
    @ConditionalOnProperty(name = "application.external.vehicle-mode", havingValue = "fake")
    public ExternalVehiclePort fakeVehiclePort() {
        return new FakeVehicleAdapter();
    }

    @Bean
    @ConditionalOnProperty(
            name = "application.external.vehicle-mode",
            havingValue = "remote",
            matchIfMissing = true
    )
    public ExternalVehiclePort realVehiclePort(
            VehicleApiClient apiClient,
            WebClient.Builder webClientBuilder
    ) {
        return new VehicleApiAdapter(apiClient, webClientBuilder);
    }
}
