package com.yowyob.fleet.domain.ports.out;

import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Port sortant pour le stockage de fichiers (local ou Kernel file-core). */
public interface ExternalFilePort {

    record UploadResult(
            UUID kernelFileId,
            String fileUrl,
            String originalName,
            String mimeType,
            long sizeBytes
    ) {}

    record DownloadResult(byte[] content, String contentType, String fileName) {}

    boolean isKernelMode();

    Mono<UploadResult> upload(FilePart file, String category, String bearerToken);

    Mono<DownloadResult> download(UUID fileId, String bearerToken);

    Mono<UploadResult> getMetadata(UUID fileId, String bearerToken);
}
