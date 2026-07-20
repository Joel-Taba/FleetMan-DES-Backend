package com.yowyob.fleet.infrastructure.config;

import com.yowyob.fleet.domain.ports.out.ExternalActorPort;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.KernelActorAdapter;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.NoOpActorAdapter;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.client.KernelAdminApiClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class ActorConfig {

    @Bean
    @ConditionalOnProperty(name = "application.auth.mode", havingValue = "kernel")
    public KernelRoleRegistry kernelRoleRegistry(
            KernelAdminApiClient adminClient,
            KernelTokenHolder tokenHolder,
            @Value("${application.kernel.tenant-id}") String tenantId,
            @Value("${application.kernel.organization-id:}") String organizationId,
            @Value("${application.kernel.roles.fleet-driver-id:}") String fleetDriverRoleId,
            @Value("${application.kernel.roles.fleet-manager-id:}") String fleetManagerRoleId,
            @Value("${application.kernel.roles.fleet-admin-id:}") String fleetAdminRoleId,
            @Value("${application.kernel.roles.fleet-super-admin-id:}") String fleetSuperAdminRoleId) {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("FLEET_DRIVER", fleetDriverRoleId);
        overrides.put("FLEET_MANAGER", fleetManagerRoleId);
        overrides.put("FLEET_ADMIN", fleetAdminRoleId);
        overrides.put("FLEET_SUPER_ADMIN", fleetSuperAdminRoleId);
        return new KernelRoleRegistry(adminClient, tokenHolder, tenantId, organizationId, overrides);
    }

    @Bean
    @ConditionalOnProperty(name = "application.auth.mode", havingValue = "kernel")
    public ExternalActorPort kernelActorPort(
            KernelAdminApiClient adminClient,
            KernelTokenHolder tokenHolder,
            KernelCallSupport kernelCallSupport,
            KernelRoleRegistry kernelRoleRegistry,
            @Value("${application.kernel.tenant-id}") String tenantId,
            @Value("${application.kernel.organization-id:}") String organizationId) {
        return new KernelActorAdapter(
                adminClient, tokenHolder, kernelCallSupport, kernelRoleRegistry, tenantId, organizationId);
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
