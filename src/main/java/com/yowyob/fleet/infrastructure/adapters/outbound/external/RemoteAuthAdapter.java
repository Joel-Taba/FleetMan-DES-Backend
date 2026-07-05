package com.yowyob.fleet.infrastructure.adapters.outbound.external;

import com.yowyob.fleet.domain.exception.AuthException;
import com.yowyob.fleet.domain.ports.in.AuthUseCase;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.client.AuthApiClient;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
public class RemoteAuthAdapter implements AuthPort {

    private static final Logger log = LoggerFactory.getLogger(RemoteAuthAdapter.class);

    private final AuthApiClient authApiClient;
    private final WebClient.Builder webClientBuilder;

    @Value("${application.auth.url}")
    private String authServiceUrl;

    private static final String SERVICE_NAME = "FLEET_MANAGEMENT";

    @Override
    public Mono<AuthResponse> login(String identifier, String password) {
        return authApiClient.authenticate(new AuthApiClient.LoginRequest(identifier, password))
                .map(this::mapToAuthResponse)
                .onErrorResume(this::mapExternalError);
    }

    @Override
    public Mono<AuthResponse> refresh(String refreshToken) {
        return authApiClient.refreshToken(new AuthApiClient.RefreshRequest(refreshToken))
                .map(this::mapToAuthResponse)
                .onErrorResume(this::mapExternalError);
    }


    @Override
    public Mono<UserDetail> getUserProfile(String token) {
        return authApiClient.getCurrentUser(ensureBearer(token))
                .map(this::mapToDomainUserDetail)
                .onErrorResume(this::mapExternalError);
    }

    @Override
    public Mono<UserDetail> getUserById(UUID userId, String token) {
        return authApiClient.getUserById(userId, ensureBearer(token))
                .map(this::mapToDomainUserDetail)
                .onErrorResume(this::mapExternalError);
    }

    @Override
    public Flux<UserDetail> getAllUsers(String token) {
        return authApiClient.getAllUsers(ensureBearer(token))
                .map(this::mapToDomainUserDetail)
                .onErrorResume(this::mapExternalErrorFlux);
    }

    @Override
    public Flux<UserDetail> getUsersByService(String serviceName, String token) {
        return authApiClient.getUsersByService(serviceName, ensureBearer(token))
                .map(this::mapToDomainUserDetail)
                .onErrorResume(e -> Flux.empty()); // On accepte un flux vide si le service est injoignable
    }

    @Override
    public Mono<UserDetail> updateUserProfile(UUID userId, String token, AuthUseCase.UpdateProfileCommand command) {
        AuthApiClient.UpdateUserRequest req = new AuthApiClient.UpdateUserRequest(
            command.firstName(), command.lastName(), command.phone(), command.email()
        );
        return webClientBuilder.build()
                .put()
                .uri(authServiceUrl + "/api/users/" + userId)
                .header("Authorization", ensureBearer(token))
                .bodyValue(req)
                .retrieve()
                .bodyToMono(AuthApiClient.UserDetailResponse.class)
                .map(this::mapToDomainUserDetail)
                .onErrorResume(this::mapExternalError);
    }

    @Override
    public Mono<Void> changePassword(UUID userId, String token, String currentPwd, String newPwd) {
        AuthApiClient.ChangePasswordRequest req = new AuthApiClient.ChangePasswordRequest(currentPwd, newPwd);
        return webClientBuilder.build()
                .put()
                .uri(authServiceUrl + "/api/users/" + userId + "/password")
                .header("Authorization", ensureBearer(token))
                .bodyValue(req)
                .retrieve()
                .bodyToMono(Void.class)
                .onErrorResume(this::mapExternalError);
    }

    @Override
    public Mono<Void> moveUserToService(UUID userId, String newServiceName, String token) {
        Map<String, Object> body = new HashMap<>();
        body.put("service", newServiceName);

        return webClientBuilder.build()
                .patch()
                .uri(authServiceUrl + "/api/users/" + userId)
                .header("Authorization", ensureBearer(token))
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .timeout(java.time.Duration.ofSeconds(3))
                .then()
                .onErrorResume(e -> {
                    log.warn("⚠️ Soft Delete distant échoué pour {}, verrou local prioritaire.", userId);
                    return Mono.empty();
                });
    }

    @Override
    public Mono<Void> deleteRemoteAccount(UUID userId, String token) {
        return authApiClient.deleteUser(userId, ensureBearer(token))
                .onErrorResume(this::mapExternalError);
    }


    @Override
    public Mono<Boolean> roleExists(String roleName) {
        return Mono.just(true);
    }

    @Override
    public Mono<Void> createRole(String roleName) {
        return Mono.empty();
    }

    // --- HELPERS MAPPING ---

    private String ensureBearer(String token) {
        if (token == null) return "";
        return token.startsWith("Bearer ") ? token : "Bearer " + token;
    }

    private AuthResponse mapToAuthResponse(AuthApiClient.TraMaSysResponse res) {
        return new AuthResponse(res.accessToken(), res.refreshToken(), mapToDomainUserDetail(res.user()));
    }

    private UserDetail mapToDomainUserDetail(AuthApiClient.UserDetailResponse res) {
        if (res == null) return null;
        return new UserDetail(
            res.id(), res.username(), res.email(), res.phone(),
            res.firstName(), res.lastName(), res.service(),
            res.roles(), res.permissions(), res.photoUri(),
            null, null, null, true ,null
        );
    }

    /**
     * Transforme les erreurs techniques WebClient en AuthException métier (Mono).
     */
    private <T> Mono<T> mapExternalError(Throwable e) {
        if (e instanceof WebClientResponseException ex) {
            log.error("❌ Erreur Auth Distante [{}]: {}", ex.getStatusCode(), ex.getResponseBodyAsString());

            return switch (ex.getStatusCode().value()) {
                case 401 -> Mono.error(AuthException.tokenExpired());
                case 409 -> Mono.error(AuthException.userAlreadyExists());
                case 403 -> Mono.error(AuthException.accountLocked());
                case 404 -> Mono.error(new AuthException("Ressource introuvable sur le service d'identité.", HttpStatus.NOT_FOUND, "AUTH_404"));
                default -> Mono.error(AuthException.generic("Erreur Service Identité : " + ex.getStatusText(), (HttpStatus) ex.getStatusCode()));
            };
        }
        return Mono.error(e);
    }



   /**
     * Transforme les erreurs techniques WebClient en AuthException métier (Flux).
     */
    private <T> Flux<T> mapExternalErrorFlux(Throwable e) {
        // .thenMany permet de convertir le signal (ici l'erreur) en Flux<T>
        return this.<T>mapExternalError(e).thenMany(Flux.empty());
    }



    @Override
    public Mono<AuthResponse> registerInRemote(AuthUseCase.RegisterCommand command) {
        // 1. On prépare le DTO
        AuthApiClient.RegisterRequest registerRequest = new AuthApiClient.RegisterRequest(
            command.username(), command.password(), command.email(),
            command.phone(), command.firstName(), command.lastName(),
            SERVICE_NAME, command.roles()
        );

        // 2. On DOIT utiliser un MultipartBodyBuilder car Pynfi attend une partie "data"
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("data", registerRequest, MediaType.APPLICATION_JSON);

        // On utilise webClientBuilder directement ici pour avoir le contrôle total sur le Multipart
        return webClientBuilder.build()
                .post()
                .uri(authServiceUrl + "/api/auth/register")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(builder.build())
                .retrieve()
                .bodyToMono(AuthApiClient.TraMaSysResponse.class)
                .map(this::mapToAuthResponse)
                .onErrorResume(this::mapExternalError);
    }
    @Override
    public Mono<Void> updateProfilePicture(UUID userId, String token, AuthUseCase.FileContent file) {
        // Construction ultra-robuste du Multipart pour Pynfi
        org.springframework.core.io.ByteArrayResource resource = new org.springframework.core.io.ByteArrayResource(file.data()) {
            @Override public String getFilename() { return file.filename(); }
        };

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", resource, MediaType.parseMediaType(file.contentType()));

        return webClientBuilder.build()
                .post()
                .uri(authServiceUrl + "/api/users/" + userId + "/picture")
                .header("Authorization", ensureBearer(token))
                .bodyValue(builder.build())
                .retrieve()
                .toBodilessEntity()
                .then()
                .onErrorResume(e -> {
                    log.error("⚠️ Échec upload photo chez Pynfi pour {}: {}", userId, e.getMessage());
                    return Mono.error(e); // L'erreur sera gérée dans le service
                });
    }
}
