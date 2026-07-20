package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.application.service.SyncPullService;
import com.yowyob.fleet.application.service.SyncPushService;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.ApiResponse;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.sync.PushMutationsRequest;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.sync.PushMutationsResponse;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.sync.SyncChangesResponse;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.sync.SyncStatusResponse;
import com.yowyob.fleet.infrastructure.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/sync")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = OpenApiConfig.TAG_SYNC)
public class SyncController {

    private final SyncPullService syncPullService;
    private final SyncPushService syncPushService;

    @GetMapping("/changes")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN', 'FLEET_DRIVER')")
    @Operation(summary = "Pull des changements offline (delta ou snapshot)")
    public Mono<ApiResponse<SyncChangesResponse>> pullChanges(
            @RequestParam String scope,
            @RequestParam(required = false) String since,
            @RequestParam(defaultValue = "false") boolean full,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            Authentication auth
    ) {
        return syncPullService.pull(auth, scope, since, full, authorization)
                .map(ApiResponse::ok);
    }

    @PostMapping("/mutations")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN', 'FLEET_DRIVER')")
    @Operation(summary = "Push d'un lot de mutations offline")
    public Mono<ApiResponse<PushMutationsResponse>> pushMutations(
            @RequestBody PushMutationsRequest request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            Authentication auth
    ) {
        return syncPushService.push(auth, request, authorization)
                .map(ApiResponse::ok);
    }

    @GetMapping("/status")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN', 'FLEET_DRIVER')")
    @Operation(summary = "État de synchronisation serveur")
    public Mono<ApiResponse<SyncStatusResponse>> status() {
        return Mono.just(ApiResponse.ok(new SyncStatusResponse(
                Instant.now(),
                Instant.now().toString(),
                "1.0"
        )));
    }
}
