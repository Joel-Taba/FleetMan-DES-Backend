package com.yowyob.fleet.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.fleet.application.service.LocalFileStorageService;
import com.yowyob.fleet.domain.ports.out.ExternalFilePort;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.KernelFileAdapter;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.LocalFileStorageAdapter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.UUID;

@Configuration
public class FileStorageConfig {

    @Bean
    @ConditionalOnProperty(name = "application.file.mode", havingValue = "local")
    public ExternalFilePort localFilePort(LocalFileStorageService localFileStorageService) {
        return new LocalFileStorageAdapter(localFileStorageService);
    }

    @Bean
    @ConditionalOnProperty(name = "application.file.mode", havingValue = "kernel")
    public ExternalFilePort kernelFilePort(
            @Qualifier("kernelWebClient") WebClient kernelWebClient,
            KernelTokenHolder kernelTokenHolder,
            ObjectMapper objectMapper,
            @Value("${application.kernel.tenant-id}") String tenantId,
            @Value("${application.kernel.organization-id}") String organizationId) {
        return new KernelFileAdapter(
                kernelWebClient,
                kernelTokenHolder,
                objectMapper,
                tenantId,
                UUID.fromString(organizationId));
    }
}
