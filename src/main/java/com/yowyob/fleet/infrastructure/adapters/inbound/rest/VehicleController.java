package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.application.service.AccessScopeService;
import com.yowyob.fleet.domain.model.Vehicle;
import com.yowyob.fleet.domain.model.VehicleParameters;
import com.yowyob.fleet.domain.ports.in.ManageVehicleUseCase;
import com.yowyob.fleet.domain.ports.out.DriverPersistencePort;
import org.springframework.security.access.AccessDeniedException;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.MaintenanceUpdateRequest;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.VehicleGalleryUpdateRequest;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.VehicleRequest;
import com.yowyob.fleet.infrastructure.config.OpenApiConfig;
import com.yowyob.fleet.infrastructure.config.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.VehiclePatchRequest;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class VehicleController {

    private final ManageVehicleUseCase vehicleUseCase;
    private final DriverPersistencePort driverPersistencePort;
    private final AccessScopeService accessScope;
    private final com.yowyob.fleet.application.service.ResourceAccessService resourceAccess;
    private final ObjectMapper objectMapper;

    /**
     * Helper pour extraire le token JWT
     */
    private String extractToken(Authentication auth) {
        return "Bearer " + auth.getCredentials().toString();
    }

    private UUID getUserId(Authentication auth) {
        return AuthPrincipal.userId(auth);
    }

    private Mono<Void> requireVehicleAccess(UUID vehicleId, Authentication auth) {
        // Inclut les véhicules sans flotte du gestionnaire qui les a créés (création
        // « indépendante »), jusque-là inaccessibles à leur propre gestionnaire.
        return resourceAccess.assertVehicleAccess(getUserId(auth), vehicleId);
    }

    /** Un changement de flotte doit viser une flotte du périmètre. */
    private Mono<Void> requireTargetFleetAccess(Map<String, Object> updates, Authentication auth) {
        Object fleetId = updates.get("fleetId");
        if (fleetId == null) return Mono.empty();
        try {
            return accessScope.assertFleetAccess(getUserId(auth), UUID.fromString(fleetId.toString()));
        } catch (IllegalArgumentException e) {
            return Mono.error(new IllegalArgumentException("fleetId invalide : " + fleetId));
        }
    }

    // ========================================================================
    // --- TAG 09a. VEHICLES | GESTION DU PARC [ACTEUR: FLEET MANAGER] ---
    // ========================================================================

    @Tag(name = OpenApiConfig.TAG_VHC_PARC)
    @PostMapping("/vehicles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Créer un véhicule", description = "Enregistrement local. Manager : ses flottes. Admin : flottes/managers de l'organisation.")
    public Mono<Vehicle> create(@Valid @RequestBody VehicleRequest request, Authentication auth) {
        return vehicleUseCase.createIndependentVehicle(request, getUserId(auth), extractToken(auth));
    }

    
    // @Tag(name = OpenApiConfig.TAG_VHC_PARC)
    // @PostMapping("/fleets/{fleetId}/vehicles/{vehicleId}")
    // @ResponseStatus(HttpStatus.NO_CONTENT)
    // @PreAuthorize("hasRole('FLEET_MANAGER')")
    // @Operation(summary = "Assigner un véhicule à une flotte", description = "Assigne le véhicule et l'ajoute automatiquement à toutes les zones de geofencing de cette flotte.")
    // public Mono<Void> assignToFleet(
    //         @PathVariable UUID fleetId, 
    //         @PathVariable UUID vehicleId, 
    //         Authentication auth) {
    //     return vehicleUseCase.assignVehicleToFleet(fleetId, vehicleId, getUserId(auth));
    // }


    @Tag(name = OpenApiConfig.TAG_VHC_PARC)
    @GetMapping("/vehicles")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Lister les véhicules", description = "Manager : ses véhicules. Admin : tout le parc.")
    public Flux<Vehicle> getVehicles(
            @RequestParam(required = false) UUID fleetId,
            Authentication auth) {
        boolean isAdmin = AuthPrincipal.isFleetAdmin(auth);
        return vehicleUseCase.getVehicles(getUserId(auth), isAdmin, extractToken(auth), fleetId);
    }

    @Tag(name = OpenApiConfig.TAG_VHC_PARC)
    @GetMapping("/vehicles/{vehicleId}")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN', 'FLEET_DRIVER')")
    @Operation(summary = "Détails complets d'un véhicule", description = "Manager/Admin : tout véhicule. Chauffeur : uniquement le sien.")
    public Mono<Vehicle> getVehicle(@PathVariable UUID vehicleId, Authentication auth) {
        UUID callerId = getUserId(auth);
        boolean privileged = AuthPrincipal.isFleetAdmin(auth) || AuthPrincipal.isFleetManager(auth);
        return vehicleUseCase.getVehicleDetails(vehicleId, extractToken(auth))
                .flatMap(vehicle -> {
                    if (privileged) {
                        return accessScope.assertVehicleFleetAccess(callerId, vehicle.fleetId())
                                .thenReturn(vehicle);
                    }
                    if (callerId.equals(vehicle.currentDriverId())) {
                        return Mono.just(vehicle);
                    }
                    return driverPersistencePort.findById(callerId)
                            .filter(d -> vehicleId.equals(d.assignedVehicleId()))
                            .map(d -> vehicle)
                            .switchIfEmpty(Mono.error(new AccessDeniedException(
                                    "Ce véhicule ne vous est pas assigné.")));
                });
    }

    @Tag(name = OpenApiConfig.TAG_VHC_PARC)
    @PutMapping("/vehicles/{vehicleId}")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Mise à jour véhicule", description = "Accepte le payload partiel ApiVehicle du front.")
    public Mono<Vehicle> update(
            @PathVariable UUID vehicleId,
            @RequestBody Map<String, Object> body,
            Authentication auth) {
        Map<String, Object> updates = new HashMap<>(body);
        updates.values().removeIf(java.util.Objects::isNull);
        // Compat galerie front → illustrationImages local
        if (updates.containsKey("galleryUrls") && !updates.containsKey("illustrationImages")) {
            updates.put("illustrationImages", updates.get("galleryUrls"));
        }
        updates.remove("galleryUrls");
        updates.remove("id");
        updates.remove("financialParameters");
        updates.remove("maintenanceParameters");
        updates.remove("operationalParameters");
        return requireVehicleAccess(vehicleId, auth)
                .then(requireTargetFleetAccess(updates, auth))
                .then(vehicleUseCase.patchVehicleInfo(vehicleId, updates, extractToken(auth)));
    }

    @Tag(name = OpenApiConfig.TAG_VHC_PARC)
    @PutMapping("/vehicles/{vehicleId}/gallery")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Mettre à jour photo + galerie (JSON URLs)")
    public Mono<Vehicle> updateGallery(
            @PathVariable UUID vehicleId,
            @RequestBody VehicleGalleryUpdateRequest request,
            Authentication auth) {
        return requireVehicleAccess(vehicleId, auth).then(vehicleUseCase.updateVehicleGallery(
                vehicleId,
                request.photoUrl(),
                request.galleryUrls(),
                extractToken(auth)
        ));
    }

@Tag(name = OpenApiConfig.TAG_VHC_PARC)
    @PatchMapping("/vehicles/{vehicleId}")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Mise à jour partielle", description = "Permet de corriger une immatriculation, marque, modèle, statut, etc.")
    public Mono<Vehicle> patch(
            @PathVariable UUID vehicleId, 
            @RequestBody VehiclePatchRequest request, // Utilisation du DTO documenté
            Authentication auth) {
        
        // Conversion du Record en Map<String, Object> en excluant les nulls
        // Le service attend une Map pour savoir quels champs EXACTEMENT ont été envoyés
        @SuppressWarnings("unchecked")
        Map<String, Object> updates = objectMapper.convertValue(request, Map.class);
        
        // Nettoyage des nulls pour ne pas écraser des données existantes
        updates.values().removeIf(java.util.Objects::isNull);

        return requireVehicleAccess(vehicleId, auth)
                .then(requireTargetFleetAccess(updates, auth))
                .then(vehicleUseCase.patchVehicleInfo(vehicleId, updates, extractToken(auth)));
    }

    @Tag(name = OpenApiConfig.TAG_VHC_PARC)
    @PutMapping("/vehicles/{vehicleId}/financial-parameters")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Paramètres Financiers", description = "Mise à jour Assurance, Coût/KM, Achat. Acteur: Manager.")
    public Mono<Vehicle> updateFinancial(@PathVariable UUID vehicleId, @RequestBody VehicleParameters.Financial params, Authentication auth) {
        return requireVehicleAccess(vehicleId, auth)
                .then(vehicleUseCase.updateFinancialParameters(vehicleId, params, extractToken(auth)));
    }

@Tag(name = OpenApiConfig.TAG_VHC_PARC)
    @PutMapping("/vehicles/{vehicleId}/maintenance-parameters")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Paramètres Maintenance", description = "Mise à jour des statuts techniques.")
    public Mono<Vehicle> updateMaintenance(
            @PathVariable UUID vehicleId, 
            @RequestBody MaintenanceUpdateRequest request, 
            Authentication auth) {
        
        VehicleParameters.Maintenance params = new VehicleParameters.Maintenance(
            request.lastMaintenanceDate(),
            request.nextMaintenanceDue(),
            request.engineStatus(),
            request.batteryHealth(),
            request.maintenanceStatus()
        );

        return requireVehicleAccess(vehicleId, auth)
                .then(vehicleUseCase.updateMaintenanceParameters(vehicleId, params, extractToken(auth)));
    }


    @Tag(name = OpenApiConfig.TAG_VHC_PARC)
    @DeleteMapping("/vehicles/{vehicleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Supprimer un véhicule", description = "Réservé à une fiche créée par erreur (aucun trajet, carburant, dépense, maintenance ni incident) ; sinon utiliser « Retirer du parc ».")
    public Mono<Void> delete(@PathVariable UUID vehicleId, Authentication auth) {
        return requireVehicleAccess(vehicleId, auth)
                .then(vehicleUseCase.removeVehicle(vehicleId, extractToken(auth)));
    }

    @Tag(name = OpenApiConfig.TAG_VHC_PARC)
    @PostMapping("/vehicles/{vehicleId}/retire")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Retirer du parc",
            description = "Vente, réforme, vol, perte totale ou restitution : le véhicule et son historique sont conservés, "
                    + "il n'est plus engageable et libère sa place dans le plan.")
    public Mono<Vehicle> retire(@PathVariable UUID vehicleId,
                                @RequestBody com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.VehicleRetireRequest request,
                                Authentication auth) {
        return requireVehicleAccess(vehicleId, auth)
                .then(vehicleUseCase.retireVehicle(vehicleId, request));
    }

    // ========================================================================
    // --- TAG 09c. VEHICLES | OPÉRATIONNEL [ACTEUR: DRIVER / MANAGER] ---
    // ========================================================================

    @Tag(name = OpenApiConfig.TAG_VHC_OP)
    @GetMapping("/vehicles/{vehicleId}/operational")
    @PreAuthorize("hasAnyRole('FLEET_DRIVER', 'FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Consulter la télémétrie", description = "Voir position, vitesse et niveau de fuel en temps réel. Acteur: Driver/Manager.")
    public Mono<VehicleParameters.Operational> getOp(@PathVariable UUID vehicleId, Authentication auth) {
        Mono<Void> gate = AuthPrincipal.isFleetDriver(auth)
                ? Mono.empty()
                : requireVehicleAccess(vehicleId, auth);
        return gate.then(vehicleUseCase.getOperationalData(vehicleId));
    }

    @Tag(name = OpenApiConfig.TAG_VHC_OP)
    @PatchMapping("/vehicles/{vehicleId}/operational")
    @PreAuthorize("hasRole('FLEET_DRIVER')")
    @Operation(summary = "Mise à jour terrain", description = "Permet au chauffeur d'ajuster l'odomètre ou le niveau de carburant. Acteur: Driver.")
    public Mono<Void> updateOp(@PathVariable UUID vehicleId, @RequestBody Map<String, Object> updates,
                               Authentication auth) {
        // Le chauffeur n'ajuste que le véhicule qu'il conduit (affecté ou trajet en cours).
        return resourceAccess.assertDriverOperatesVehicle(getUserId(auth), vehicleId)
                .then(vehicleUseCase.updateOperationalData(vehicleId, updates));
    }

    // ========================================================================
    // --- TAG 09d. VEHICLES | RÉFÉRENTIELS [ACTEUR: PUBLIC / MANAGER] ---
    // ========================================================================

    @Tag(name = OpenApiConfig.TAG_VHC_LOOKUP)
    @GetMapping("/vehicles/lookup/{resource}")
    @Operation(
    summary = "Listes de référence", 
    description = "Récupère une liste de choix spécifique depuis la base souveraine. " +
                      "\n\n**Ressources disponibles ({resource}) :** " +
                      "\n- `vehicle-types` : Catégories globales (BUS, CAR, etc.)" +
                      "\n- `manufacturers` : Constructeurs industriels" +
                      "\n- `brands` : Marques commerciales" +
                      "\n- `models` : Modèles de véhicules" +
                      "\n- `sizes` : Gabarits / Tailles" +
                      "\n- `usages` : Types d'usage (Taxi, VIP, etc.)" +
                      "\n- `fuel-types` : Types d'énergie" +
                      "\n- `transmissions` : Types de boîtes de vitesse" +
                      "\n- `colors` : Catalogue des couleurs autorisées" +
                      "\n\n**Acteur :** Tous (Public/Manager)."
)
    public Flux<Map<String, Object>> getLookup(@PathVariable String resource) {
        return vehicleUseCase.getLocalLookupData(resource);
    }
    @Tag(name = OpenApiConfig.TAG_VHC_LOOKUP)
    @GetMapping("/vehicles/resources/all")
    @Operation(summary = "Catalogue complet", description = "Récupère les 9 référentiels en un seul appel. Idéal pour initialiser les formulaires.")
    public Mono<Map<String, Object>> getFullCatalog() {
        return vehicleUseCase.getAllResourcesCatalog();
    }
}