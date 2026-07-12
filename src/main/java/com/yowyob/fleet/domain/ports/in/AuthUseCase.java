package com.yowyob.fleet.domain.ports.in;

import com.yowyob.fleet.domain.ports.out.AuthPort;
import reactor.core.publisher.Mono;
import java.util.UUID;
import java.util.List;

public interface AuthUseCase {
    Mono<AuthPort.AuthResponse> login(String identifier, String password);

    Mono<AuthPort.AuthResponse> selectContext(String selectionToken, String contextId, UUID organizationId);

    Mono<AuthPort.AuthResponse> register(RegisterCommand command);

    Mono<AuthPort.AuthResponse> refreshToken(String refreshToken);

    Mono<AuthPort.UserDetail> me(String token);

    Mono<AuthPort.UserDetail> updateProfile(UUID userId, String token, UpdateProfileCommand command);

    Mono<Void> changePassword(UUID userId, String token, String currentPwd, String newPwd);

    Mono<Void> deleteAccount(UUID userId, String token);

    Mono<Void> updateProfilePicture(UUID userId, String token, FileContent file);

    record RegisterCommand(
            String username, String password, String email, String phone,
            String firstName, String lastName, List<String> roles,
            FileContent photo) {
    }

    record UpdateProfileCommand(
            String firstName, String lastName, String phone, String email) {
    }

    record FileContent(String filename, String contentType, byte[] data) {
    }
}