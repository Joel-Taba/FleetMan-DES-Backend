package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.domain.ports.out.ExternalFilePort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.nio.file.Files;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class FileStorageController {

    private final ExternalFilePort filePort;
    private final com.yowyob.fleet.application.service.LocalFileStorageService localStorageService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('FLEET_MANAGER','FLEET_ADMIN','FLEET_SUPER_ADMIN')")
    @Operation(summary = "Uploader un fichier (PDF ou image)",
            description = "Mode kernel : délègue à file-core Kernel. Mode local : stockage disque.")
    public Mono<UploadResponse> upload(
            @RequestPart("file") FilePart file,
            @RequestParam(defaultValue = "document") String category,
            Authentication auth
    ) {
        return filePort.upload(file, category, extractToken(auth))
                .map(f -> new UploadResponse(
                        f.fileUrl(),
                        f.originalName(),
                        f.mimeType(),
                        f.sizeBytes(),
                        f.kernelFileId()
                ));
    }

    @GetMapping("/{filename}")
    @Operation(summary = "Télécharger / prévisualiser un fichier",
            description = "UUID → proxy Kernel file-core. Nom fichier → stockage local.")
    public Mono<ResponseEntity<?>> download(@PathVariable String filename, Authentication auth) {
        if (isUuid(filename) && filePort.isKernelMode()) {
            return filePort.download(UUID.fromString(filename), extractToken(auth))
                    .map(result -> ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION,
                                    "inline; filename=\"" + result.fileName() + "\"")
                            .contentType(MediaType.parseMediaType(result.contentType()))
                            .body(new ByteArrayResource(result.content())));
        }
        return Mono.fromCallable(() -> {
            var path = localStorageService.resolve(filename);
            if (!Files.exists(path)) {
                return ResponseEntity.notFound().build();
            }
            String contentType = Files.probeContentType(path);
            if (contentType == null) contentType = "application/octet-stream";
            var resource = new FileSystemResource(path);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(resource);
        });
    }

    private static boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String extractToken(Authentication auth) {
        if (auth == null || auth.getCredentials() == null) {
            return null;
        }
        return auth.getCredentials().toString();
    }

    public record UploadResponse(
            String fileUrl,
            String originalName,
            String mimeType,
            long sizeBytes,
            UUID kernelFileId
    ) {}
}
