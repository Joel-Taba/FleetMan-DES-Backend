package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.domain.ports.in.ManageAlertRuleUseCase;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.AlertEventResponse;
import com.yowyob.fleet.infrastructure.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/alerts/events")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_ALERT_EVENTS)
@SecurityRequirement(name = "bearerAuth")
public class AlertEventController {

    private final ManageAlertRuleUseCase useCase;

    private UUID getUserId(Authentication auth) {
        return ((AuthPort.UserDetail) auth.getPrincipal()).id();
    }

    @GetMapping
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Toutes mes notifications",
               description = "Retourne les 100 dernières notifications (lues et non lues) du manager connecté, triées par date décroissante.")
    public Flux<AlertEventResponse> listAll(Authentication auth) {
        return useCase.getAllEvents(getUserId(auth)).map(AlertEventResponse::from);
    }

    @GetMapping("/unread")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Notifications non lues",
               description = "Retourne uniquement les notifications non lues du manager. Utilisé pour afficher le contenu du panneau de notifications dans le header.")
    public Flux<AlertEventResponse> getUnread(Authentication auth) {
        return useCase.getUnreadEvents(getUserId(auth)).map(AlertEventResponse::from);
    }

    @GetMapping("/count-unread")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Nombre de notifications non lues",
               description = "Retourne le compteur de notifications non lues. Utilisé pour le badge rouge dans le header de l'application.")
    public Mono<UnreadCountDto> countUnread(Authentication auth) {
        return useCase.countUnread(getUserId(auth)).map(UnreadCountDto::new);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Détail d'une notification")
    public Mono<AlertEventResponse> getById(
            @Parameter(description = "ID de la notification") @PathVariable UUID id) {
        return useCase.getEventById(id).map(AlertEventResponse::from);
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Marquer comme lue",
               description = "Marque une notification spécifique comme lue (UNREAD → READ).")
    public Mono<AlertEventResponse> markAsRead(
            @Parameter(description = "ID de la notification") @PathVariable UUID id) {
        return useCase.markAsRead(id).map(AlertEventResponse::from);
    }

    @PatchMapping("/read-all")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Tout marquer comme lu",
               description = "Marque toutes les notifications non lues du manager comme lues. Retourne le nombre de notifications marquées.")
    public Mono<MarkedCountDto> markAllAsRead(Authentication auth) {
        return useCase.markAllAsRead(getUserId(auth)).map(MarkedCountDto::new);
    }

    @PatchMapping("/{id}/dismiss")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Ignorer une notification",
               description = "Masque une notification sans la supprimer (UNREAD → DISMISSED).")
    public Mono<AlertEventResponse> dismiss(
            @Parameter(description = "ID de la notification") @PathVariable UUID id) {
        return useCase.dismiss(id).map(AlertEventResponse::from);
    }

    // ── DTOs internes ─────────────────────────────────────────────────────────

    record UnreadCountDto(long count) {}
    record MarkedCountDto(long markedCount) {}
}
