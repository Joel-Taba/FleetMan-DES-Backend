package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.DocumentException;
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
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class LocalFileStorageService {

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".pdf", ".jpg", ".jpeg", ".png", ".webp"
    );

    private final Path uploadRoot;
    private final long maxBytes;

    public LocalFileStorageService(
            @Value("${fleetman.uploads.dir:uploads}") String uploadDir,
            @Value("${fleetman.uploads.max-bytes:10485760}") long maxBytes
    ) throws IOException {
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
        Files.createDirectories(uploadRoot);
    }

    public Mono<StoredFile> store(FilePart filePart, String category) {
        String originalName = filePart.filename();
        validateFileName(originalName);
        String mime = resolveMimeType(filePart, originalName);
        validateMimeType(mime, originalName);

        String ext = extensionOf(originalName);
        String storedName = category + "-" + UUID.randomUUID() + ext;
        Path target = uploadRoot.resolve(storedName);

        return DataBufferUtils.write(filePart.content(), target, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
                .then(Mono.fromCallable(() -> {
                    long size = Files.size(target);
                    if (size > maxBytes) {
                        Files.deleteIfExists(target);
                        throw DocumentException.fileTooLarge(maxBytes);
                    }
                    return new StoredFile(
                            storedName,
                            "/api/v1/files/" + storedName,
                            originalName,
                            mime,
                            size
                    );
                }));
    }

    public Path resolve(String filename) {
        Path resolved = uploadRoot.resolve(filename).normalize();
        if (!resolved.startsWith(uploadRoot)) {
            throw new SecurityException("Accès fichier interdit");
        }
        return resolved;
    }

    private void validateFileName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            throw DocumentException.invalidFileType("nom de fichier manquant");
        }
        String ext = extensionOf(originalName).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw DocumentException.invalidFileType(ext);
        }
    }

    private void validateMimeType(String mime, String originalName) {
        if (mime == null || !ALLOWED_MIME_TYPES.contains(mime.toLowerCase(Locale.ROOT))) {
            throw DocumentException.invalidFileType(mime != null ? mime : originalName);
        }
    }

    private String resolveMimeType(FilePart filePart, String originalName) {
        if (filePart.headers().getContentType() != null) {
            return filePart.headers().getContentType().toString();
        }
        String ext = extensionOf(originalName).toLowerCase(Locale.ROOT);
        return switch (ext) {
            case ".pdf" -> "application/pdf";
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".png" -> "image/png";
            case ".webp" -> "image/webp";
            default -> "application/octet-stream";
        };
    }

    private String extensionOf(String originalName) {
        return originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.'))
                : "";
    }

    public record StoredFile(
            String storedName,
            String publicUrl,
            String originalName,
            String mimeType,
            long sizeBytes
    ) {}
}
