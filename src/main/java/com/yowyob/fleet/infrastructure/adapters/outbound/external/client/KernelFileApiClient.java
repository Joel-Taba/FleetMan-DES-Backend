package com.yowyob.fleet.infrastructure.adapters.outbound.external.client;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Client file-core Kernel — pas de souscription FILE dédiée requise sur cette instance.
 */
@HttpExchange
public interface KernelFileApiClient {

    @PostExchange(value = "/api/files", contentType = "multipart/form-data")
    Mono<KernelAuthApiClient.ApiResponse<FileMetadataResponse>> uploadFile(
            @RequestHeader("Authorization") String bearerToken,
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-Organization-Id") String organizationId,
            @RequestPart("file") org.springframework.core.io.Resource file);

    @GetExchange("/api/files/{fileId}")
    Mono<KernelAuthApiClient.ApiResponse<FileMetadataResponse>> getFileMetadata(
            @RequestHeader("Authorization") String bearerToken,
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-Organization-Id") String organizationId,
            @PathVariable("fileId") UUID fileId);

    record FileMetadataResponse(UUID id, String fileName, String contentType, Long sizeBytes, String status) {}
}
