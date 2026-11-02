package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.domain.ports.in.AuthUseCase;
import com.yowyob.fleet.domain.ports.in.ManageAdminUseCase;
import com.yowyob.fleet.domain.ports.in.ManageFleetUseCase;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.FleetResponse;
import com.yowyob.fleet.infrastructure.config.OpenApiConfig;
import com.yowyob.fleet.infrastructure.config.security.AuthPrincipal;
import com.yowyob.fleet.infrastructure.mappers.FleetMapper;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/management")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_ADMIN_MANAGERS)
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
public class AdminManagerController {

    private final ManageAdminUseCase adminUseCase;
    private final ManageFleetUseCase fleetUseCase;
    private final FleetMapper fleetMapper;

    private UUID getUserId(Authentication auth) {
        return AuthPrincipal.userId(auth);
    }

    private boolean isSuper(Authentication a) {
        return a.getAuthorities().stream().anyMatch(ga -> ga.getAuthority().equals("ROLE_FLEET_SUPER_ADMIN"));
    }

    @GetMapping("/managers")
    @Operation(summary = "Lister les gestionnaires", description = "Admin : les gestionnaires de son entreprise, avec l'état de leur compte. Super-admin : tous.")
    public Flux<com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.ManagerAccountResponse> list(Authentication auth) {
        return adminUseCase.listManagerAccounts(getUserId(auth), isSuper(auth));
    }

    @GetMapping("/managers/{id}")
    @Operation(summary = "Détails d'un Fleet Manager")
    public Mono<AuthPort.UserDetail> getOne(@PathVariable UUID id, Authentication auth) {
        return adminUseCase.getManagerDetails(id, getUserId(auth), isSuper(auth));
    }

    public record SuspendManagerRequest(@NotBlank String reason) {}

    @PostMapping("/managers/{id}/suspend")
    @Operation(summary = "Suspendre un gestionnaire",
            description = "Motif obligatoire. Connexion bloquée ; ses flottes sont gelées (aucun nouveau trajet ni "
                    + "affectation, les trajets en cours peuvent se terminer) jusqu'à réactivation ou transfert.")
    public Mono<Void> suspend(@PathVariable UUID id, @Valid @RequestBody SuspendManagerRequest req, Authentication auth) {
        return adminUseCase.suspendManager(id, req.reason(), getUserId(auth), isSuper(auth));
    }

    @PostMapping("/managers/{id}/reactivate")
    @Operation(summary = "Réactiver un gestionnaire suspendu")
    public Mono<Void> reactivate(@PathVariable UUID id, Authentication auth) {
        return adminUseCase.reactivateManager(id, getUserId(auth), isSuper(auth));
    }

    @PatchMapping("/managers/{id}/toggle")
    @Operation(summary = "Suspendre / réactiver (compatibilité)", description = "Préférer /suspend (avec motif) et /reactivate.")
    public Mono<Void> toggle(@PathVariable UUID id, Authentication auth) {
        return adminUseCase.toggleManagerStatus(id, getUserId(auth), isSuper(auth));
    }

    @DeleteMapping("/managers/{id}")
    @Operation(summary = "Clôturer un gestionnaire", description = "Uniquement s'il n'a plus de flotte (transférez-les d'abord) ; compte désactivé, historique conservé.")
    public Mono<Void> delete(@PathVariable UUID id, Authentication auth) {
        return adminUseCase.deleteManager(id, getUserId(auth), isSuper(auth));
    }

    public record CreateManagerRequest(
        @NotBlank String username,
        @NotBlank
        @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{10,}$",
            message = "Le mot de passe doit contenir au moins 10 caractères, une majuscule, une minuscule, un chiffre et un symbole."
        )
        String password,
        @Email @NotBlank String email,
        @NotBlank
        @Pattern(
            regexp = "^\\+?[0-9]{8,15}$",
            message = "Le numéro de téléphone ne doit contenir que des chiffres (avec un + optionnel en préfixe)."
        )
        String phone,
        String firstName,
        String lastName,
        @Schema(description = "Ignoré : le gestionnaire appartient à l'entreprise de l'administrateur.")
        String companyName
    ) {
        @AssertTrue(message = "Le prénom ou le nom doit être renseigné.")
        public boolean isNameProvided() {
            return (firstName != null && !firstName.isBlank())
                || (lastName != null && !lastName.isBlank());
        }
    }

    @PostMapping("/managers")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer un gestionnaire de flotte", description = "Compte FLEET_MANAGER actif immédiatement. created_by_admin_id = l'admin connecté, qui doit exister dans platform_admins.")
    public Mono<AuthPort.UserDetail> createManager(@Valid @RequestBody CreateManagerRequest req, Authentication auth) {
        var command = new AuthUseCase.RegisterCommand(
                req.username(), req.password(), req.email(), req.phone(),
                req.firstName(), req.lastName(), List.of("FLEET_MANAGER"), null
        );
        return adminUseCase.createManager(command, req.companyName(), getUserId(auth));
    }

    @GetMapping("/managers/{id}/fleets")
    @Operation(summary = "Flottes confiées à un gestionnaire de l'entreprise")
    public Flux<FleetResponse> managerFleets(@PathVariable UUID id, Authentication auth) {
        return adminUseCase.getManagerDetails(id, getUserId(auth), isSuper(auth))
                .thenMany(fleetUseCase.getFleets(id, false))
                .map(fleetMapper::toResponse);
    }

    public record AssignFleetsRequest(@NotEmpty List<UUID> fleetIds) {}

    @PostMapping("/managers/{id}/fleets")
    @Operation(summary = "Transférer des flottes à ce gestionnaire",
            description = "Flottes de l'entreprise, gestionnaire actif, aucun trajet ni affectation ouverts. "
                    + "Véhicules, chauffeurs et plannings suivent ; dépenses et budgets passés restent à l'ancien gestionnaire.")
    public Mono<Void> assignFleets(@PathVariable UUID id, @Valid @RequestBody AssignFleetsRequest req, Authentication auth) {
        return fleetUseCase.assignFleetsToManager(req.fleetIds(), id, getUserId(auth));
    }
}