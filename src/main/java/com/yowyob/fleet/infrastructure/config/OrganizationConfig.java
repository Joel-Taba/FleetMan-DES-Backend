package com.yowyob.fleet.infrastructure.config;

import com.yowyob.fleet.domain.ports.out.ExternalOrganizationPort;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.KernelOrganizationAdapter;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.NoOpOrganizationAdapter;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.client.KernelOrganizationApiClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrganizationConfig {

    @Bean
    @ConditionalOnProperty(name = "application.auth.mode", havingValue = "kernel")
    public ExternalOrganizationPort kernelOrganizationPort(
            KernelOrganizationApiClient organizationClient,
            @Value("${application.kernel.tenant-id}") String tenantId) {
        return new KernelOrganizationAdapter(organizationClient, tenantId);
    }

    @Bean
    @ConditionalOnProperty(name = "application.auth.mode", havingValue = "fake")
    public ExternalOrganizationPort fakeOrganizationPort() {
        return new NoOpOrganizationAdapter();
    }

    @Bean
    @ConditionalOnProperty(name = "application.auth.mode", havingValue = "remote")
    public ExternalOrganizationPort remoteOrganizationPort() {
        return new NoOpOrganizationAdapter();
    }
}
