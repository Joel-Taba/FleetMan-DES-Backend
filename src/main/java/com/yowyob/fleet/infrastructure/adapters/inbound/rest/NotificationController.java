package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.fleet.application.service.AccessScopeService;
import com.yowyob.fleet.domain.model.Notification;
import com.yowyob.fleet.domain.ports.out.NotificationHistoryRepositoryPort;
import com.yowyob.fleet.infrastructure.config.OpenApiConfig;
import com.yowyob.fleet.infrastructure.config.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Centre de notifications in-app de l'utilisateur connecté (chauffeur, gestionnaire, administrateur).
 * Toujours limité à ses propres notifications : l'identifiant du jeton est ramené à l'identifiant
 * local (fleet.users.id), celui sous lequel les notifications sont enregistrées.
 *
 * Le champ {@code data} est l'objet JSON écrit par AppNotifier : {@code {"type":"<EVENEMENT>","<entite>Id":"…"}},
 * lu par les centres web et mobile pour ouvrir l'écran concerné ; {@code event} en reprend le type.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = OpenApiConfig.TAG_ACCOUNT, description = "In-app notifications history")
public class NotificationController {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationHistoryRepositoryPort historyPort;
    private final AccessScopeService accessScope;

    @Schema(description = "Notification in-app")
    public record NotificationResponse(
            UUID id,
            UUID userId,
            String title,
            String message,
            @Schema(description = "Niveau : INFO, SUCCESS, WARNING") String type,
            @Schema(description = "Code d'événement (data.type), ex. FUEL_DECLARED") String event,
            @com.fasterxml.jackson.annotation.JsonProperty("read") boolean read,
            @com.fasterxml.jackson.annotation.JsonProperty("isRead") @Schema(description = "Alias de read (ancien format)") boolean isRead,
            LocalDateTime createdAt,
            @Schema(description = "Références : type + identifiants (incidentId, tripId, maintenanceId…)") Map<String, Object> data,
            @Schema(description = "data brut (ancien format)") String dataJson
    ) {
        static NotificationResponse from(Notification n) {
            Map<String, Object> data = parse(n.dataJson());
            Object event = data.get("type");
            return new NotificationResponse(n.id(), n.userId(), n.title(), n.message(), n.type(),
                    event != null ? String.valueOf(event) : null, n.isRead(), n.isRead(), n.createdAt(), data, n.dataJson());
        }
    }

    public record NotificationPageResponse(List<NotificationResponse> content, long totalElements, int totalPages,
                                           int currentPage, long unreadCount) {}

    static Map<String, Object> parse(String raw) {
        if (raw == null || raw.isBlank()) return Map.of();
        try {
            Map<String, Object> map = JSON.readValue(raw, new TypeReference<Map<String, Object>>() {});
            return map != null ? map : Map.of();
        } catch (Exception e) {
            return Map.of();
        }
    }

    private Mono<UUID> me(Authentication auth) {
        return accessScope.resolveLocalUserId(AuthPrincipal.userId(auth));
    }

    @GetMapping
    @Operation(summary = "Get my notifications",
            description = "Paginated list of notifications for the connected user, newest first, with the unread count.")
    public Mono<NotificationPageResponse> getMyNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication auth) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return me(auth).flatMap(userId -> Mono.zip(
                        historyPort.getUserNotifications(userId, safePage, safeSize),
                        historyPort.countUnread(userId))
                .map(t -> new NotificationPageResponse(
                        t.getT1().content().stream().map(NotificationResponse::from).toList(),
                        t.getT1().totalElements(), t.getT1().totalPages(), t.getT1().currentPage(), t.getT2())));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Nombre de notifications non lues", description = "Badge des cloches web et mobile.")
    public Mono<Map<String, Long>> unreadCount(Authentication auth) {
        return me(auth).flatMap(historyPort::countUnread).map(count -> Map.of("count", count));
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Marquer une notification comme lue")
    public Mono<NotificationResponse> markAsRead(@PathVariable UUID id, Authentication auth) {
        return me(auth).flatMap(userId -> historyPort.markAsRead(id, userId)).map(NotificationResponse::from);
    }

    @PatchMapping("/read-all")
    @Operation(summary = "Marquer toutes mes notifications comme lues")
    public Mono<Map<String, Long>> markAllAsRead(Authentication auth) {
        return me(auth).flatMap(historyPort::markAllAsRead).map(updated -> Map.of("updated", updated));
    }
}
