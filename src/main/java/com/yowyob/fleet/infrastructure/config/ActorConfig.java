package com.yowyob.fleet.infrastructure.config;

import com.yowyob.fleet.domain.ports.out.ExternalActorPort;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.KernelActorAdapter;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.NoOpActorAdapter;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.client.KernelAdminApiClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ActorConfig {

    @Bean
    @ConditionalOnProperty(name = "application.auth.mode", havingValue = "kernel")
    public ExternalActorPort kernelActorPort(
            KernelAdminApiClient adminClient,
            KernelTokenHolder tokenHolder,
            KernelCallSupport kernelCallSupport,
            @Value("${application.kernel.tenant-id}") String tenantId,
            @Value("${application.kernel.roles.fleet-driver-id:}") String fleetDriverRoleId,
            @Value("${application.kernel.roles.fleet-manager-id:}") String fleetManagerRoleId) {
        return new KernelActorAdapter(
                adminClient, tokenHolder, kernelCallSupport, tenantId, fleetDriverRoleId, fleetManagerRoleId);
    }

    @Bean
    @ConditionalOnProperty(name = "application.auth.mode", havingValue = "fake")
    public ExternalActorPort fakeActorPort() {
        return new NoOpActorAdapter();
    }

    @Bean
    @ConditionalOnProperty(name = "application.auth.mode", havingValue = "remote")
    public ExternalActorPort remoteActorPort() {
        return new NoOpActorAdapter();
    }
}
