package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.domain.ports.in.AuthUseCase;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.LoginRequest;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.RegisterRequest;
import com.yowyob.fleet.infrastructure.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody; 
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_AUTH) 
public class AuthController {

    private final AuthUseCase authUseCase;

    @PostMapping("/login")
    @Operation(summary = "Connexion utilisateur", description = "Retourne les tokens et déclenche la synchronisation du profil local.")
    public Mono<AuthPort.AuthResponse> login(@org.springframework.web.bind.annotation.RequestBody LoginRequest request) {
        return authUseCase.login(request.identifier(), request.password());
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rafraîchir le token", description = "Utilisez le refreshToken pour obtenir un nouvel accessToken.")
    public Mono<AuthPort.AuthResponse> refresh(@org.springframework.web.bind.annotation.RequestBody TokenRefreshRequest request) {
        return authUseCase.refreshToken(request.refreshToken());
    }

    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "Inscription Utilisateur",
        description = "Création de compte avec photo optionnelle.",
        requestBody = @RequestBody(
            content = @Content(
                mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                schema = @Schema(implementation = RegisterSchema.class), // On utilise une classe fictive pour la doc
                encoding = @Encoding(name = "user", contentType = "application/json")
            )
        )
    )
    public Mono<AuthPort.AuthResponse> register(
            @RequestPart("user") RegisterRequest dto,
            @RequestPart(value = "file", required = false) Part filePart 
    ) {
        return processFilePart(filePart)
                .map(photo -> new AuthUseCase.RegisterCommand(
                        dto.username(), dto.password(), dto.email(), dto.phone(),
                        dto.firstName(), dto.lastName(), dto.roles(), photo
                ))
                .switchIfEmpty(Mono.just(new AuthUseCase.RegisterCommand(
                        dto.username(), dto.password(), dto.email(), dto.phone(),
                        dto.firstName(), dto.lastName(), dto.roles(), null
                )))
                .flatMap(authUseCase::register);
    }

    private Mono<AuthUseCase.FileContent> processFilePart(Part fp) {
        if (fp == null) return Mono.empty();
        String filename = (fp instanceof FilePart file) ? file.filename() : "profile_picture";
        return DataBufferUtils.join(fp.content())
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    return new AuthUseCase.FileContent(
                            filename,
                            fp.headers().getContentType() != null ? fp.headers().getContentType().toString() : "image/jpeg",
                            bytes
                    );
                });
    }

    public record TokenRefreshRequest(String refreshToken) {}

    // Classe interne pour aider Swagger à générer le bon formulaire
    @Schema(name = "RegisterRequestMultipart")
    private static class RegisterSchema {
        @Schema(description = "Données de l'utilisateur (JSON)", requiredMode = Schema.RequiredMode.REQUIRED)
        public RegisterRequest user;
        
        @Schema(description = "Photo de profil (Fichier)", type = "string", format = "binary")
        public String file;
    }
}