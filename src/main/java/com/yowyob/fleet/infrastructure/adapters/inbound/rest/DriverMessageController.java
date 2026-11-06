package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.application.service.DriverMessageService;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/** Messagerie gestionnaire ↔ chauffeur (consignes, informations, questions). */
@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
@Tag(name = "Messagerie", description = "Échanges entre le gestionnaire d'une flotte et ses chauffeurs")
public class DriverMessageController {

    public record MessageRequest(String body) {}

    private final DriverMessageService messages;

    private static UUID userId(Authentication auth) {
        return ((AuthPort.UserDetail) auth.getPrincipal()).id();
    }

    @GetMapping("/conversations")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Conversations avec les chauffeurs de mes flottes")
    public Flux<DriverMessageService.Conversation> conversations(Authentication auth) {
        return messages.conversations(userId(auth));
    }

    @GetMapping("/drivers/{driverId}")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_DRIVER')")
    @Operation(summary = "Fil d'un chauffeur (marque comme lus les messages reçus)")
    public Flux<DriverMessageService.Message> thread(@PathVariable UUID driverId, Authentication auth) {
        return messages.thread(userId(auth), driverId);
    }

    @PostMapping("/drivers/{driverId}")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Écrire à un chauffeur")
    public Mono<DriverMessageService.Message> sendToDriver(@PathVariable UUID driverId, @RequestBody MessageRequest req,
                                                           Authentication auth) {
        return messages.sendToDriver(userId(auth), driverId, req.body());
    }

    @PostMapping("/fleets/{fleetId}")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Écrire à tous les chauffeurs actifs d'une flotte")
    public Mono<Map<String, Long>> broadcast(@PathVariable UUID fleetId, @RequestBody MessageRequest req, Authentication auth) {
        return messages.broadcast(userId(auth), fleetId, req.body()).map(n -> Map.of("recipients", n));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('FLEET_DRIVER')")
    @Operation(summary = "Ma conversation avec mon gestionnaire (marque comme lus ses messages)")
    public Flux<DriverMessageService.Message> mine(Authentication auth) {
        return messages.threadOfSelf(userId(auth));
    }

    @PostMapping("/mine")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('FLEET_DRIVER')")
    @Operation(summary = "Écrire à mon gestionnaire")
    public Mono<DriverMessageService.Message> sendFromDriver(@RequestBody MessageRequest req, Authentication auth) {
        return messages.sendFromDriver(userId(auth), req.body());
    }

    @GetMapping("/mine/unread-count")
    @PreAuthorize("hasRole('FLEET_DRIVER')")
    @Operation(summary = "Messages du gestionnaire non lus")
    public Mono<Map<String, Long>> unread(Authentication auth) {
        return messages.unreadForDriver(userId(auth)).map(n -> Map.of("unread", n));
    }

}
