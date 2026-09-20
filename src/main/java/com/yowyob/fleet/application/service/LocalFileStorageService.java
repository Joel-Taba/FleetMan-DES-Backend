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

    public Mono<StoredFile> storeBytes(byte[] data, String originalName, String mimeType, String category) {
        validateFileName(originalName);
        String rawMime = mimeType != null && !mimeType.isBlank()
                ? mimeType
                : resolveMimeFromName(originalName);
        int semicolon = rawMime.indexOf(';');
        String strippedMime = semicolon >= 0 ? rawMime.substring(0, semicolon).trim() : rawMime;
        final String mime = strippedMime.toLowerCase(Locale.ROOT).contains("octet-stream")
                ? resolveMimeFromName(originalName)
                : strippedMime;
        validateMimeType(mime, originalName);
        if (data == null || data.length == 0) {
            throw DocumentException.invalidFileType("fichier vide");
        }
        if (data.length > maxBytes) {
            throw DocumentException.fileTooLarge(maxBytes);
        }
        String ext = extensionOf(originalName);
        String storedName = category + "-" + UUID.randomUUID() + ext;
        Path target = uploadRoot.resolve(storedName);
        return Mono.fromCallable(() -> {
            Files.write(target, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return new StoredFile(
                    storedName,
                    "/api/v1/files/" + storedName,
                    originalName,
                    mime,
                    data.length
            );
        });
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
        if (mime == null) {
            throw DocumentException.invalidFileType(originalName);
        }
        String normalized = mime.toLowerCase(Locale.ROOT);
        int semicolon = normalized.indexOf(';');
        if (semicolon >= 0) {
            normalized = normalized.substring(0, semicolon).trim();
        }
        if (!ALLOWED_MIME_TYPES.contains(normalized)) {
            throw DocumentException.invalidFileType(mime);
        }
    }

    private String resolveMimeType(FilePart filePart, String originalName) {
        if (filePart.headers().getContentType() != null) {
            return filePart.headers().getContentType().toString();
        }
        return resolveMimeFromName(originalName);
    }

    private String resolveMimeFromName(String originalName) {
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
