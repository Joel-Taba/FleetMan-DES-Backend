package com.yowyob.fleet.application.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

@Slf4j
@Service
public class LocalFileStorageService {

    private final Path uploadRoot;

    public LocalFileStorageService(
            @Value("${fleetman.uploads.dir:uploads}") String uploadDir) throws IOException {
        Path root;
        try {
            root = Path.of(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(root);
        } catch (Exception e) {
            log.warn("Failed to create upload directory at {}, falling back to system temporary directory", uploadDir,
                    e);
            root = Path.of(System.getProperty("java.io.tmpdir")).resolve("uploads").toAbsolutePath().normalize();
            Files.createDirectories(root);
        }
        this.uploadRoot = root;
        log.info("Initialized LocalFileStorageService with upload root: {}", this.uploadRoot);
    }

    public Mono<StoredFile> store(FilePart filePart, String category) {
        String originalName = filePart.filename();
        String ext = originalName.contains(".") ? originalName.substring(originalName.lastIndexOf('.')) : "";
        String storedName = category + "-" + UUID.randomUUID() + ext;
        Path target = uploadRoot.resolve(storedName);

        return DataBufferUtils
                .write(filePart.content(), target, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
                .then(Mono.fromCallable(() -> {
                    String mime = filePart.headers().getContentType() != null
                            ? filePart.headers().getContentType().toString()
                            : "application/octet-stream";
                    long size = Files.size(target);
                    return new StoredFile(
                            storedName,
                            "/api/v1/files/" + storedName,
                            originalName,
                            mime,
                            size);
                }));
    }

    public Path resolve(String filename) {
        Path resolved = uploadRoot.resolve(filename).normalize();
        if (!resolved.startsWith(uploadRoot)) {
            throw new SecurityException("Accès fichier interdit");
        }
        return resolved;
    }

    public record StoredFile(
            String storedName,
            String publicUrl,
            String originalName,
            String mimeType,
            long sizeBytes) {
    }
}
