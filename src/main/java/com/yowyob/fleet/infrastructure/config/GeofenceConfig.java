package com.yowyob.fleet.infrastructure.config;

import com.yowyob.fleet.domain.ports.out.ExternalGeofencePort;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.FakeGeofenceAdapter;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.GeofenceApiAdapter;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.client.GeofenceApiClient;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.client.GeofenceAuthClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GeofenceConfig {

    @Bean
    @ConditionalOnProperty(name = "application.external.geofence-mode", havingValue = "fake")
    public ExternalGeofencePort fakeGeofencePort() {
        return new FakeGeofenceAdapter();
    }

    @Bean
    @ConditionalOnProperty(
            name = "application.external.geofence-mode",
            havingValue = "remote",
            matchIfMissing = true
    )
    public ExternalGeofencePort realGeofencePort(
            GeofenceApiClient apiClient,
            GeofenceAuthClient authClient
    ) {
        return new GeofenceApiAdapter(apiClient, authClient);
    }
}
