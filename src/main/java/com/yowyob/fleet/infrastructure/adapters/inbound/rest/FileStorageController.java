package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.application.service.LocalFileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.nio.file.Files;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class FileStorageController {

    private final LocalFileStorageService storageService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('FLEET_MANAGER','FLEET_ADMIN','FLEET_SUPER_ADMIN')")
    @Operation(summary = "Uploader un fichier (PDF ou image)")
    public Mono<UploadResponse> upload(
            @RequestPart("file") FilePart file,
            @RequestParam(defaultValue = "document") String category
    ) {
        return storageService.store(file, category).map(f -> new UploadResponse(
                f.publicUrl(),
                f.originalName(),
                f.mimeType(),
                f.sizeBytes()
        ));
    }

    @GetMapping("/{filename}")
    @Operation(summary = "Télécharger / prévisualiser un fichier uploadé")
    public Mono<ResponseEntity<FileSystemResource>> download(@PathVariable String filename) {
        return Mono.fromCallable(() -> {
            var path = storageService.resolve(filename);
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

    public record UploadResponse(String fileUrl, String originalName, String mimeType, long sizeBytes) {}
}
