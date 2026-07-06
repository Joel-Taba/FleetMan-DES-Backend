package com.yowyob.fleet.infrastructure.adapters.outbound.external;

import com.yowyob.fleet.application.service.LocalFileStorageService;
import com.yowyob.fleet.domain.ports.out.ExternalFilePort;
import lombok.RequiredArgsConstructor;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Stockage local disque — mode développement sans Kernel. */
@RequiredArgsConstructor
public class LocalFileStorageAdapter implements ExternalFilePort {

    private final LocalFileStorageService localStorage;

    @Override
    public boolean isKernelMode() {
        return false;
    }

    @Override
    public Mono<UploadResult> upload(FilePart file, String category, String bearerToken) {
        return localStorage.store(file, category)
                .map(stored -> new UploadResult(
                        null,
                        stored.publicUrl(),
                        stored.originalName(),
                        stored.mimeType(),
                        stored.sizeBytes()));
    }

    @Override
    public Mono<DownloadResult> download(UUID fileId, String bearerToken) {
        return Mono.error(new UnsupportedOperationException("Téléchargement local par UUID non supporté"));
    }

    @Override
    public Mono<UploadResult> getMetadata(UUID fileId, String bearerToken) {
        return Mono.empty();
    }
}
