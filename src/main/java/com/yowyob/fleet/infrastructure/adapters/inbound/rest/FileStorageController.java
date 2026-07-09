package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.domain.exception.AuthException;
import com.yowyob.fleet.domain.ports.out.ExternalFilePort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.nio.file.Files;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class FileStorageController {

    private static final Set<String> PUBLIC_UPLOAD_CATEGORIES = Set.of("subscription");
    private static final Set<String> STAFF_ROLES = Set.of(
            "ROLE_FLEET_MANAGER", "ROLE_FLEET_ADMIN", "ROLE_FLEET_SUPER_ADMIN");

    private final ExternalFilePort filePort;
    private final com.yowyob.fleet.application.service.LocalFileStorageService localStorageService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Uploader un fichier (PDF ou image)",
            description = "Public pour category=subscription|document (signup). Sinon rôles staff requis.")
    public Mono<UploadResponse> upload(
            @RequestPart("file") FilePart file,
            @RequestParam(defaultValue = "document") String category,
            Authentication auth
    ) {
        String normalizedCategory = category == null || category.isBlank() ? "document" : category.trim();
        if (!isUploadAllowed(auth, normalizedCategory)) {
            return Mono.error(new AuthException(
                    "Authentification requise pour uploader dans cette catégorie.",
                    HttpStatus.UNAUTHORIZED,
                    "AUTH_001"));
        }
        return filePort.upload(file, normalizedCategory, extractToken(auth))
                .map(f -> new UploadResponse(
                        f.fileUrl(),
                        f.originalName(),
                        f.mimeType(),
                        f.sizeBytes(),
                        f.kernelFileId()
                ));
    }

    private static boolean isUploadAllowed(Authentication auth, String category) {
        if (auth != null && auth.isAuthenticated() && hasStaffRole(auth)) {
            return true;
        }
        // Signup public : uniquement documents de souscription
        return PUBLIC_UPLOAD_CATEGORIES.contains(category);
    }

    private static boolean hasStaffRole(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(STAFF_ROLES::contains);
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
