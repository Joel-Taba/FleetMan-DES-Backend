package com.yowyob.fleet.infrastructure.config;

import com.yowyob.fleet.domain.ports.out.ExternalVehiclePort;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.FakeVehicleAdapter;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.KernelResourceAdapter;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.VehicleApiAdapter;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.client.KernelOrganizationApiClient;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.client.KernelResourceApiClient;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.client.VehicleApiClient;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.DriverR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.UserLocalR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.VehicleLocalR2dbcRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.UUID;

@Configuration
public class VehicleExternalConfig {

    @Bean
    @ConditionalOnProperty(name = "application.external.vehicle-mode", havingValue = "fake")
    public ExternalVehiclePort fakeVehiclePort() {
        return new FakeVehicleAdapter();
    }

    @Bean
    @ConditionalOnProperty(name = "application.external.vehicle-mode", havingValue = "kernel")
    public ExternalVehiclePort kernelVehiclePort(
            KernelResourceApiClient resourceClient,
            KernelOrganizationApiClient organizationClient,
            VehicleLocalR2dbcRepository vehicleRepository,
            DriverR2dbcRepository driverRepository,
            UserLocalR2dbcRepository userRepository,
            KernelTokenHolder kernelTokenHolder,
            KernelCallSupport kernelCallSupport,
            @Value("${application.kernel.tenant-id}") String tenantId,
            @Value("${application.kernel.organization-id}") String organizationId) {
        return new KernelResourceAdapter(
                resourceClient,
                organizationClient,
                vehicleRepository,
                driverRepository,
                userRepository,
                kernelTokenHolder,
                kernelCallSupport,
                tenantId,
                UUID.fromString(organizationId));
    }

    @Bean
    @ConditionalOnProperty(name = "application.external.vehicle-mode", havingValue = "remote")
    public ExternalVehiclePort remoteVehiclePort(
            VehicleApiClient apiClient,
            WebClient.Builder webClientBuilder) {
        return new VehicleApiAdapter(apiClient, webClientBuilder);
    }
}
