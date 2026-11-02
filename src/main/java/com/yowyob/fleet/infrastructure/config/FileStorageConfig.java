package com.yowyob.fleet.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.fleet.application.service.LocalFileStorageService;
import com.yowyob.fleet.domain.ports.out.DocumentHubPort;
import com.yowyob.fleet.domain.ports.out.ExternalFilePort;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.KernelDocumentHubAdapter;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.KernelFileAdapter;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.LocalFileStorageAdapter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.util.UUID;
import reactor.core.publisher.Mono;

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
            KernelCallSupport kernelCallSupport,
            ObjectMapper objectMapper,
            @Value("${application.kernel.tenant-id}") String tenantId,
            @Value("${application.kernel.organization-id}") String organizationId) {
        return new KernelFileAdapter(
                kernelWebClient,
                kernelTokenHolder,
                kernelCallSupport,
                objectMapper,
                tenantId,
                UUID.fromString(organizationId));
    }

    /** Hub documentaire du Kernel : inactif par défaut, FleetMan garde seul le dossier. */
    @Bean
    @ConditionalOnProperty(name = "application.document.hub", havingValue = "kernel")
    public DocumentHubPort kernelDocumentHub(
            @Qualifier("kernelWebClient") WebClient kernelWebClient,
            KernelTokenHolder kernelTokenHolder,
            @Value("${application.kernel.tenant-id}") String tenantId,
            @Value("${application.kernel.organization-id}") String organizationId) {
        return new KernelDocumentHubAdapter(kernelWebClient, kernelTokenHolder, tenantId, UUID.fromString(organizationId));
    }

    @Bean
    @ConditionalOnProperty(name = "application.document.hub", havingValue = "local", matchIfMissing = true)
    public DocumentHubPort localDocumentHub() {
        return new DocumentHubPort() {
            @Override
            public Mono<UUID> attach(String targetType, UUID targetId, UUID kernelFileId, String category, String label) {
                return Mono.empty();
            }

            @Override
            public Mono<Void> review(UUID linkId, String reviewStatus, LocalDate expiresAt, String notes) {
                return Mono.empty();
            }

            @Override
            public Mono<Void> upsertPolicy(String targetType, String category, boolean mandatory) {
                return Mono.empty();
            }
        };
    }
}
