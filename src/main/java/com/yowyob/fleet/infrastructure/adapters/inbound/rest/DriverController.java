package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.domain.model.Driver;
import com.yowyob.fleet.domain.ports.in.AuthUseCase;
import com.yowyob.fleet.domain.ports.in.ManageDriverUseCase;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.DriverRegistrationRequest;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.DriverResponse;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.ManagerDriverCreateRequest;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.ManagerDriverUpdateRequest;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.RecruitDriverRequest;
import com.yowyob.fleet.infrastructure.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_DRIVERS)
@SecurityRequirement(name = "bearerAuth")
public class DriverController {

    private final ManageDriverUseCase driverUseCase;
    private final com.yowyob.fleet.application.service.ResourceAccessService resourceAccess;
    private final com.yowyob.fleet.application.service.DriverAbsenceService absenceService;
    private final com.yowyob.fleet.application.service.DriverAccountService accountService;

    private UUID getUserId(Authentication auth) { return ((AuthPort.UserDetail) auth.getPrincipal()).id(); }
    private String getToken(String header) { return header.startsWith("Bearer ") ? header.substring(7) : header; }

    // Le gestionnaire ne crée plus de comptes chauffeur : réservé à l'administrateur de l'entreprise.
    @PostMapping("/drivers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Créer un chauffeur", description = "Réservé à l'administrateur de l'entreprise.")
    public Mono<DriverResponse> createDriver(
            @org.springframework.web.bind.annotation.RequestBody ManagerDriverCreateRequest request,
            Authentication auth
    ) {
        return driverUseCase.createDriverForManager(request, getUserId(auth));
    }

    @PutMapping("/drivers/{userId}")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Mettre à jour un chauffeur")
    public Mono<DriverResponse> updateDriver(
            @PathVariable UUID userId,
            @org.springframework.web.bind.annotation.RequestBody ManagerDriverUpdateRequest request,
            Authentication auth
    ) {
        // Les informations personnelles (identité, coordonnées, permis, photo) ne sont modifiables
        // que par le chauffeur lui-même (« Mon profil ») ; ici seuls le statut et la flotte restent.
        if (request.firstName() != null || request.lastName() != null || request.email() != null
                || request.phone() != null || request.licenceNumber() != null || request.photoUrl() != null) {
            return Mono.error(new com.yowyob.fleet.domain.exception.DriverException(
                    "Les informations personnelles d'un chauffeur ne sont modifiables que par le chauffeur lui-même.",
                    HttpStatus.FORBIDDEN, "DRV_025"));
        }
        return driverUseCase.updateDriverForManager(userId, request, getUserId(auth));
    }

    @DeleteMapping("/drivers/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Supprimer un chauffeur", description = "Réservé à une fiche créée par erreur (aucun trajet, affectation, dépense ni incident) ; sinon enregistrer son départ.")
    public Mono<Void> deleteDriver(@PathVariable UUID userId, Authentication auth) {
        return driverUseCase.deleteDriverForManager(userId, getUserId(auth));
    }

    @PostMapping(value = "/fleets/{fleetId}/drivers/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(
        summary = "Créer un nouveau Chauffeur",
        description = "Crée le compte Auth + Profil local + Photo de profil."
    )
    public Mono<Driver> register(
            @PathVariable UUID fleetId,
            @RequestPart("user") DriverRegistrationRequest request,
            @RequestPart(value = "file", required = false) Part filePart,
            Authentication auth
    )  {
        return processFile(filePart)
                .flatMap(photo -> driverUseCase.registerDriverWithPhoto(fleetId, request, getUserId(auth), photo))
                // On utilise defer pour s'assurer que l'appel sans photo n'est créé que si nécessaire
                .switchIfEmpty(Mono.defer(() -> driverUseCase.registerDriverWithPhoto(fleetId, request, getUserId(auth), null)));
    }

    @GetMapping("/drivers")
    @Operation(summary = "Lister les chauffeurs du service", description = "Filtres optionnels par flotte ou état d'assignation.")
    public Flux<DriverResponse> list(
            @RequestParam(required = false) UUID fleetId,
            @RequestParam(required = false) Boolean isAssigned,
            Authentication auth
    ) {
        return driverUseCase.getDriversEnriched(fleetId, isAssigned, getUserId(auth));
    }

    @GetMapping("/drivers/{userId}")
    @Operation(summary = "Profil détaillé d'un chauffeur")
    public Mono<DriverResponse> getById(@PathVariable UUID userId, Authentication auth) {
        return resourceAccess.assertDriverAccess(getUserId(auth), userId)
                .then(driverUseCase.getDriverEnriched(userId));
    }

    @GetMapping("/drivers/search")
    @Operation(summary = "Rechercher un chauffeur par email ou username")
    public Mono<Driver> search(@RequestParam String identifier) {
        return driverUseCase.searchDriver(identifier);
    }

    @PostMapping("/drivers/{userId}/assign-vehicle")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    public Mono<Void> assign(@PathVariable UUID userId, @RequestBody VehicleAssignRequest req, 
                             @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader, Authentication auth) {
        return driverUseCase.assignVehicle(userId, req.vehicleId(), getUserId(auth), getToken(authHeader));
    }

    @PostMapping("/drivers/{userId}/unassign-vehicle")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    public Mono<Void> unassign(@PathVariable UUID userId, Authentication auth) {
        return resourceAccess.assertDriverAccess(getUserId(auth), userId)
                .then(driverUseCase.unassignVehicle(userId, getUserId(auth)));
    }

    // ── Contrat ──────────────────────────────────────────────────────────────

    @PostMapping("/drivers/{userId}/leave")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Départ de l'entreprise",
            description = "Le chauffeur sort des effectifs (historique conservé, véhicule attitré libéré, place du plan rendue). "
                    + "Refusé s'il a des trajets ou affectations non terminés.")
    public Mono<DriverResponse> leave(@PathVariable UUID userId, @RequestBody(required = false) LeaveRequest req,
                                      Authentication auth) {
        return driverUseCase.leaveCompany(userId, req != null ? req.leftAt() : null,
                req != null ? req.reason() : null, getUserId(auth));
    }

    @PostMapping("/drivers/{userId}/reinstate")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Réintégrer un chauffeur parti", description = "Consomme à nouveau une place du plan.")
    public Mono<DriverResponse> reinstate(@PathVariable UUID userId, Authentication auth) {
        return driverUseCase.reinstate(userId, getUserId(auth));
    }

    // ── Absences datées ──────────────────────────────────────────────────────

    @GetMapping("/drivers/{userId}/absences")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN', 'FLEET_DRIVER')")
    @Operation(summary = "Absences d'un chauffeur (congés, arrêts maladie, suspensions)",
            description = "Le gestionnaire de la flotte, ou le chauffeur lui-même (lecture seule).")
    public Flux<com.yowyob.fleet.domain.model.DriverAbsence> absences(@PathVariable UUID userId, Authentication auth) {
        return absenceService.list(userId, getUserId(auth));
    }

    @PostMapping("/drivers/{userId}/absences")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Déclarer une absence",
            description = "type : LEAVE (congé), SICK_LEAVE (arrêt maladie) ou SUSPENSION (fin facultative, motif obligatoire). "
                    + "Refusée si des trajets ou affectations sont prévus sur ces jours.")
    public Mono<com.yowyob.fleet.domain.model.DriverAbsence> declareAbsence(
            @PathVariable UUID userId,
            @RequestBody com.yowyob.fleet.application.service.DriverAbsenceService.DeclareAbsenceCommand req,
            Authentication auth) {
        return absenceService.declare(userId, req, getUserId(auth));
    }

    @PostMapping("/drivers/{userId}/absences/{absenceId}/end")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Fixer ou avancer la fin d'une absence (reprise anticipée, levée de suspension)")
    public Mono<com.yowyob.fleet.domain.model.DriverAbsence> endAbsence(
            @PathVariable UUID userId, @PathVariable UUID absenceId, @RequestBody EndAbsenceRequest req,
            Authentication auth) {
        return absenceService.end(userId, absenceId, req.endDate(), getUserId(auth));
    }

    @PostMapping("/drivers/{userId}/absences/{absenceId}/cancel")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Annuler une absence saisie par erreur")
    public Mono<com.yowyob.fleet.domain.model.DriverAbsence> cancelAbsence(
            @PathVariable UUID userId, @PathVariable UUID absenceId, Authentication auth) {
        return absenceService.cancel(userId, absenceId, getUserId(auth));
    }

    public record LeaveRequest(java.time.LocalDate leftAt, String reason) {}

    public record EndAbsenceRequest(java.time.LocalDate endDate) {}

    private Mono<AuthUseCase.FileContent> processFile(Part fp) {
        if (fp == null) return Mono.empty();
        return DataBufferUtils.join(fp.content()).map(db -> {
            byte[] bytes = new byte[db.readableByteCount()];
            db.read(bytes);
            DataBufferUtils.release(db);
            return new AuthUseCase.FileContent(
                (fp instanceof FilePart f) ? f.filename() : "driver_pic",
                fp.headers().getContentType().toString(), bytes);
        });
    }

    public record VehicleAssignRequest(UUID vehicleId) {}

    public record BlockRequest(String reason) {}

    @GetMapping("/drivers/{userId}/account")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "État du compte d'un chauffeur (bloqué ou non, motif)")
    public Mono<com.yowyob.fleet.application.service.DriverAccountService.AccountState> accountState(
            @PathVariable UUID userId, Authentication auth) {
        return accountService.state(getUserId(auth), userId);
    }

    @PostMapping("/drivers/{userId}/block")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Bloquer le compte d'un chauffeur", description = "Connexion refusée, chauffeur non engageable, historique conservé. Motif obligatoire ; refusé s'il a un trajet ou un poste ouvert.")
    public Mono<com.yowyob.fleet.application.service.DriverAccountService.AccountState> block(
            @PathVariable UUID userId, @org.springframework.web.bind.annotation.RequestBody BlockRequest req, Authentication auth) {
        return accountService.block(getUserId(auth), userId, req.reason());
    }

    @PostMapping("/drivers/{userId}/unblock")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Débloquer le compte d'un chauffeur")
    public Mono<com.yowyob.fleet.application.service.DriverAccountService.AccountState> unblock(
            @PathVariable UUID userId, Authentication auth) {
        return accountService.unblock(getUserId(auth), userId);
    }
}