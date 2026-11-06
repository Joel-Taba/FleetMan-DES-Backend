package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.application.service.DriverOnboardingService;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Finalisation de l'inscription par le chauffeur connecté : profil et pièces obligatoires. */
@RestController
@RequestMapping("/api/v1/drivers/me/onboarding")
@RequiredArgsConstructor
@PreAuthorize("hasRole('FLEET_DRIVER')")
@Tag(name = "Inscription chauffeur", description = "Profil et pièces à fournir avant l'accès au travail")
public class DriverOnboardingController {

    private final DriverOnboardingService onboarding;

    private static UUID userId(Authentication auth) {
        return ((AuthPort.UserDetail) auth.getPrincipal()).id();
    }

    @GetMapping
    @Operation(summary = "État de mon inscription : profil manquant, pièces obligatoires")
    public Mono<DriverOnboardingService.Onboarding> status(Authentication auth) {
        return onboarding.status(userId(auth));
    }

    @PutMapping("/profile")
    @Operation(summary = "Compléter mon profil")
    public Mono<DriverOnboardingService.Onboarding> updateProfile(@RequestBody DriverOnboardingService.ProfileUpdate req,
                                                                  Authentication auth) {
        return onboarding.updateProfile(userId(auth), req);
    }

    @PostMapping("/complete")
    @Operation(summary = "Terminer mon inscription (profil complet, pièces obligatoires déposées)")
    public Mono<DriverOnboardingService.Onboarding> complete(Authentication auth) {
        return onboarding.complete(userId(auth));
    }

    @PostMapping("/skip")
    @Operation(summary = "Passer la finalisation pour l'instant (reste non engageable sans pièces approuvées)")
    public Mono<DriverOnboardingService.Onboarding> skip(Authentication auth) {
        return onboarding.skip(userId(auth));
    }
}
