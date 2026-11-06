package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.application.service.DriverRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/** Inscription d'un chauffeur depuis l'application, sans session (routes /api/v1/public/**). */
@RestController
@RequestMapping("/api/v1/public/driver-registrations")
@RequiredArgsConstructor
@Tag(name = "Inscription chauffeur", description = "Demande d'inscription et activation du compte par le chauffeur")
public class PublicDriverRegistrationController {

    private final DriverRegistrationService registrations;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Demander à rejoindre une flotte",
            description = "Code de la flotte (fourni par le gestionnaire), identité, n° de permis, téléphone ou e-mail. "
                    + "Le gestionnaire est notifié et accepte ou refuse la demande.")
    public Mono<DriverRegistrationService.Submitted> submit(@RequestBody DriverRegistrationService.RegistrationRequest req) {
        return registrations.submit(req);
    }

    @PostMapping("/activate")
    @Operation(summary = "Activer son compte avec le code reçu",
            description = "Téléphone ou e-mail de la demande, code d'activation et mot de passe choisi. "
                    + "Crée le compte et renvoie la session ainsi que l'identifiant de connexion.")
    public Mono<DriverRegistrationService.Activated> activate(@RequestBody DriverRegistrationService.ActivationRequest req) {
        return registrations.activate(req);
    }
}
