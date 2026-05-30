package com.yowyob.fleet.infrastructure.adapters.outbound.external.client;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;
import org.springframework.web.service.annotation.PatchExchange;

import reactor.core.publisher.Flux; 
import reactor.core.publisher.Mono;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@HttpExchange("/api")
public interface AuthApiClient {
    
    @PostExchange("/auth/login")
    Mono<TraMaSysResponse> authenticate(@RequestBody LoginRequest request);

    @PostExchange("/auth/register")
    Mono<TraMaSysResponse> register(@RequestBody RegisterRequest request);
    
    @GetExchange("/auth/me")
    Mono<UserDetailResponse> getCurrentUser(@RequestHeader("Authorization") String bearerToken);

    @PostExchange("/auth/refresh")
    Mono<TraMaSysResponse> refreshToken(@RequestBody RefreshRequest request);

    @GetExchange("/users")
    Flux<UserDetailResponse> getAllUsers(@RequestHeader("Authorization") String bearerToken);

    @GetExchange("/users/{id}")
    Mono<UserDetailResponse> getUserById(@PathVariable UUID id, @RequestHeader("Authorization") String bearerToken);

    @GetExchange("/users/service/{service}")
    Flux<UserDetailResponse> getUsersByService(@PathVariable String service, @RequestHeader("Authorization") String bearerToken);

    @PutExchange("/users/{id}")
    Mono<UserDetailResponse> updateUser(@PathVariable UUID id, @RequestBody UpdateUserRequest request);

    @PatchExchange("/users/{id}")
    Mono<UserDetailResponse> patchUser(@PathVariable UUID id, @RequestBody Map<String, Object> updates, @RequestHeader("Authorization") String bearerToken);

    @PutExchange("/users/{id}/password")
    Mono<Void> changePassword(@PathVariable UUID id, @RequestBody ChangePasswordRequest request);

    @DeleteExchange("/users/{id}") 
    Mono<Void> deleteUser(@PathVariable UUID id, @RequestHeader("Authorization") String bearerToken);

    // DTOs
    record LoginRequest(String identifier, String password) {}
    record RefreshRequest(String refreshToken) {}
    
    record RegisterRequest(
        String username, String password, String email, String phone,
        String firstName, String lastName, String service, List<String> roles
    ) {}

    record UpdateUserRequest(String firstName, String lastName, String phone, String email) {}
    record ChangePasswordRequest(String currentPassword, String newPassword) {}
    record TraMaSysResponse(String accessToken, String refreshToken, UserDetailResponse user) {}
    
    record UserDetailResponse(
        UUID id, String username, String email, String phone,
        String firstName, String lastName, String service,
        List<String> roles, List<String> permissions,
        String photoUri 
    ) {}    
}