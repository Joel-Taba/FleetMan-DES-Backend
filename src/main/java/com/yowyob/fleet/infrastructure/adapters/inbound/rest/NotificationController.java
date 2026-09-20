package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.domain.model.Notification;
import com.yowyob.fleet.domain.ports.out.NotificationHistoryRepositoryPort;
import com.yowyob.fleet.infrastructure.config.OpenApiConfig;
import com.yowyob.fleet.infrastructure.config.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = OpenApiConfig.TAG_ACCOUNT, description = "In-app notifications history")
public class NotificationController {

    private final NotificationHistoryRepositoryPort historyPort;

    // @GetMapping(value = "/stream/alerts", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    // @Operation(summary = "Flux d'alertes en temps réel (SSE)", description = "Le client garde la connexion ouverte et reçoit les alertes en direct.")
    // public Flux<GeofenceEventEntity> streamAlerts() {
    //     // Note: Pour que ça marche, il faut que ton Adapter Kafka publie aussi dans un Sinks.Many<GeofenceEventEntity>
    //     // C'est le niveau au-dessus, dis-moi si tu veux l'implémenter.
    //     return Flux.empty(); 
    // }

    @GetMapping
    @Operation(summary = "Get my notifications", description = "Paginated list of notifications for the connected user.")
    public Mono<NotificationHistoryRepositoryPort.PagedResult<Notification>> getMyNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication auth) {
        return historyPort.getUserNotifications(AuthPrincipal.userId(auth), page, size);
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Marquer une notification comme lue")
    public Mono<Notification> markAsRead(
            @PathVariable java.util.UUID id,
            Authentication auth) {
        return historyPort.markAsRead(id, AuthPrincipal.userId(auth));
    }

    @PatchMapping("/read-all")
    @Operation(summary = "Marquer toutes mes notifications comme lues")
    public Mono<java.util.Map<String, Long>> markAllAsRead(Authentication auth) {
        return historyPort.markAllAsRead(AuthPrincipal.userId(auth))
                .map(updated -> java.util.Map.of("updated", updated));
    }
}