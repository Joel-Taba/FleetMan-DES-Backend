package com.yowyob.fleet.infrastructure.config;

import com.yowyob.fleet.domain.ports.out.ExternalNotificationPort;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.KernelNotificationAdapter;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.client.KernelNotificationApiClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NotificationKernelConfig {

    @Bean
    public ExternalNotificationPort externalNotificationPort(
            KernelNotificationApiClient kernelNotificationApiClient,
            KernelTokenHolder kernelTokenHolder,
            KernelCallSupport kernelCallSupport,
            @Value("${application.kernel.tenant-id}") String tenantId,
            @Value("${application.kernel.organization-id}") String organizationId) {
        return new KernelNotificationAdapter(
                kernelNotificationApiClient,
                kernelTokenHolder,
                kernelCallSupport,
                tenantId,
                organizationId);
    }
}
