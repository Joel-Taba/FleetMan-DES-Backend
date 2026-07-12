package com.yowyob.fleet.domain.ports.out;

import com.yowyob.fleet.domain.ports.in.AuthUseCase;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.List;
import java.util.UUID;

public interface AuthPort {
    Mono<AuthResponse> login(String identifier, String password);

    Mono<AuthResponse> selectContext(String selectionToken, String contextId, UUID organizationId);

    Mono<AuthResponse> refresh(String refreshToken);

    Mono<AuthResponse> registerInRemote(AuthUseCase.RegisterCommand command);

    Mono<UserDetail> getUserProfile(String token);

    Mono<UserDetail> getUserById(UUID userId, String token);

    Flux<UserDetail> getUsersByService(String serviceName, String token);

    Flux<UserDetail> getAllUsers(String token);

    Mono<UserDetail> updateUserProfile(UUID userId, String token, AuthUseCase.UpdateProfileCommand command);

    Mono<Void> changePassword(UUID userId, String token, String currentPwd, String newPwd);

    Mono<Void> deleteRemoteAccount(UUID userId, String token);

    Mono<Void> moveUserToService(UUID userId, String newServiceName, String token);

    Mono<Void> updateProfilePicture(UUID userId, String token, AuthUseCase.FileContent file);

    Mono<Boolean> roleExists(String roleName);

    Mono<Void> createRole(String roleName);

    record AuthResponse(String accessToken, String refreshToken, UserDetail user) {
    }

    record UserDetail(
            UUID id, String username, String email, String phone,
            String firstName, String lastName, String service,
            List<String> roles, List<String> permissions,
            String photoUrl, String companyName, String licenceNumber, String vehicleId, boolean isActive,
            java.time.Instant lastLoginAt,
            String companyPhone,
            String companyAddress,
            String companyCity,
            String companyLogoUrl) {

        public UserDetail(
                UUID id, String username, String email, String phone,
                String firstName, String lastName, String service,
                List<String> roles, List<String> permissions,
                String photoUrl, String companyName, String licenceNumber, String vehicleId, boolean isActive,
                java.time.Instant lastLoginAt) {
            this(id, username, email, phone, firstName, lastName, service, roles, permissions, photoUrl, companyName,
                    licenceNumber, vehicleId, isActive, lastLoginAt, null, null, null, null);
        }
    }

}