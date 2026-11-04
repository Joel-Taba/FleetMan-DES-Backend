package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.application.service.SubscriptionAdminService;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.SubscriptionAdminDtos;
import com.yowyob.fleet.infrastructure.config.OpenApiConfig;
import com.yowyob.fleet.infrastructure.config.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/** Portefeuille d'abonnements du super-administrateur : entreprises, revenus, règlements. */
@RestController
@RequestMapping("/api/v1/admin/super/billing")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_SUPER_ADMIN)
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('FLEET_SUPER_ADMIN')")
public class SuperAdminBillingController {

    private final SubscriptionAdminService service;

    @GetMapping("/overview")
    @Operation(summary = "Revenus récurrents, états des abonnements, échéances proches")
    public Mono<SubscriptionAdminDtos.Overview> overview() {
        return service.overview();
    }

    @GetMapping("/companies")
    @Operation(summary = "Entreprises clientes et leur abonnement")
    public Flux<SubscriptionAdminDtos.CompanyRow> companies() {
        return service.companies();
    }

    @GetMapping("/companies/{id}")
    @Operation(summary = "Fiche d'une entreprise : abonnement, usage, commandes, journal")
    public Mono<SubscriptionAdminDtos.CompanyDetail> company(@PathVariable UUID id) {
        return service.company(id);
    }

    @PostMapping("/companies/{id}/plan")
    @Operation(summary = "Attribuer un plan", description = "Sans paiement ; motif obligatoire ; la période repart d'aujourd'hui.")
    public Mono<SubscriptionAdminDtos.CompanyDetail> changePlan(
            @PathVariable UUID id,
            @RequestBody SubscriptionAdminDtos.ChangePlanRequest request,
            Authentication auth
    ) {
        return service.changePlan(id, AuthPrincipal.userId(auth), request);
    }

    @PostMapping("/companies/{id}/extend")
    @Operation(summary = "Prolonger l'échéance")
    public Mono<SubscriptionAdminDtos.CompanyDetail> extend(
            @PathVariable UUID id,
            @RequestBody SubscriptionAdminDtos.ExtendRequest request,
            Authentication auth
    ) {
        return service.extend(id, AuthPrincipal.userId(auth), request);
    }

    @PostMapping("/companies/{id}/suspend")
    @Operation(summary = "Suspendre l'abonnement")
    public Mono<SubscriptionAdminDtos.CompanyDetail> suspend(
            @PathVariable UUID id,
            @RequestBody SubscriptionAdminDtos.ReasonRequest request,
            Authentication auth
    ) {
        return service.suspend(id, AuthPrincipal.userId(auth), request.reason());
    }

    @PostMapping("/companies/{id}/reactivate")
    @Operation(summary = "Lever la suspension")
    public Mono<SubscriptionAdminDtos.CompanyDetail> reactivate(
            @PathVariable UUID id,
            @RequestBody SubscriptionAdminDtos.ReasonRequest request,
            Authentication auth
    ) {
        return service.reactivate(id, AuthPrincipal.userId(auth), request.reason());
    }

    @PostMapping("/companies/{id}/remind")
    @Operation(summary = "Relancer l'administrateur de l'entreprise")
    public Mono<SubscriptionAdminDtos.CompanyDetail> remind(@PathVariable UUID id, Authentication auth) {
        return service.remind(id, AuthPrincipal.userId(auth));
    }

    @PostMapping("/reminders/run")
    @Operation(summary = "Envoyer les rappels d'échéance dus aujourd'hui")
    public Mono<Map<String, Long>> runReminders() {
        return service.sendDueReminders().map(sent -> Map.of("sent", sent));
    }

    @GetMapping("/orders")
    @Operation(summary = "Toutes les commandes d'abonnement")
    public Flux<SubscriptionAdminDtos.OrderRow> orders() {
        return service.orders(null);
    }

    @PostMapping("/orders/{id}/record-payment")
    @Operation(summary = "Enregistrer un règlement reçu hors ligne")
    public Mono<SubscriptionAdminDtos.OrderRow> recordPayment(
            @PathVariable UUID id,
            @RequestBody SubscriptionAdminDtos.RecordPaymentRequest request,
            Authentication auth
    ) {
        return service.recordPayment(id, AuthPrincipal.userId(auth), request);
    }

    @PostMapping("/orders/{id}/cancel")
    @Operation(summary = "Annuler une commande en attente")
    public Mono<SubscriptionAdminDtos.OrderRow> cancelOrder(
            @PathVariable UUID id,
            @RequestBody SubscriptionAdminDtos.ReasonRequest request,
            Authentication auth
    ) {
        return service.cancelOrder(id, AuthPrincipal.userId(auth), request.reason());
    }
}
