package com.yowyob.fleet.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.fleet.domain.ports.out.ExternalKycPort;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.KernelKycAdapter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class KycConfig {

    @Bean
    public ExternalKycPort externalKycPort(
            @Qualifier("kernelKycWebClient") WebClient kernelKycWebClient,
            KernelTokenHolder kernelTokenHolder,
            KernelCallSupport kernelCallSupport,
            ObjectMapper objectMapper,
            @Value("${application.kernel.tenant-id}") String tenantId) {
        return new KernelKycAdapter(
                kernelKycWebClient,
                kernelTokenHolder,
                kernelCallSupport,
                objectMapper,
                tenantId);
    }
}
