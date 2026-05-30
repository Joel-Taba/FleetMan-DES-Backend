package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.domain.ports.in.AuthUseCase;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.ChangePasswordRequest;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.UpdateProfileRequest;
import com.yowyob.fleet.infrastructure.config.OpenApiConfig;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/account")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_ACCOUNT, description = "Gestion du profil personnel (Identité)")
@SecurityRequirement(name = "bearerAuth")
public class AccountController {

    private final AuthUseCase authUseCase;

    @GetMapping
    @Operation(summary = "Voir mon profil", description = "Récupère les infos Auth + Données métier (Manager/Driver).")
    public Mono<AuthPort.UserDetail> getMyProfile(
            @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) String token
    ) {
        return authUseCase.me(token);
    }

    @PutMapping
    @Operation(summary = "Mettre à jour mon identité", description = "Modifie nom, prénom, téléphone et email. (Infos métier exclues)")
    public Mono<AuthPort.UserDetail> updateProfile(
            @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        return authUseCase.me(token)
                .flatMap(user -> {
                    AuthUseCase.UpdateProfileCommand cmd = new AuthUseCase.UpdateProfileCommand(
                            request.firstName(), request.lastName(), request.phone(), request.email()
                    );
                    return authUseCase.updateProfile(user.id(), token, cmd);
                });
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Changer mon mot de passe")
    public Mono<Void> changePassword(
            @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        return authUseCase.me(token)
                .flatMap(user -> authUseCase.changePassword(
                        user.id(), token, request.currentPassword(), request.newPassword()
                ));
    }

    @PostMapping(value = "/picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Changer ma photo de profil")
    public Mono<Void> updatePicture(
            @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @RequestPart("file") Part filePart 
    ) {
        return authUseCase.me(token)
                .flatMap(user -> {
                    // FIX: Extraction sécurisée du nom de fichier
                    String filename = (filePart instanceof FilePart file) ? file.filename() : "avatar.jpg";
                    
                    return DataBufferUtils.join(filePart.content())
                        .map(dataBuffer -> {
                            byte[] bytes = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(bytes);
                            DataBufferUtils.release(dataBuffer);
                            return new AuthUseCase.FileContent(
                                    filename,
                                    filePart.headers().getContentType() != null ? filePart.headers().getContentType().toString() : "image/jpeg",
                                    bytes
                            );
                        })
                        .flatMap(fileContent -> authUseCase.updateProfilePicture(user.id(), token, fileContent));
                });
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprimer mon compte", description = "Supprime le compte Auth et les données locales associées.")
    public Mono<Void> deleteAccount(
            @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) String token
    ) {
        return authUseCase.me(token)
                .flatMap(user -> authUseCase.deleteAccount(user.id(), token));
    }
}