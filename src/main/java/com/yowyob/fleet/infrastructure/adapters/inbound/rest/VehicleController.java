package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.domain.model.Vehicle;
import com.yowyob.fleet.domain.model.VehicleParameters;
import com.yowyob.fleet.domain.ports.in.ManageVehicleUseCase;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.MaintenanceUpdateRequest;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.VehicleGalleryUpdateRequest;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.VehicleRequest;
import com.yowyob.fleet.infrastructure.config.OpenApiConfig;
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
    private final ObjectMapper objectMapper;

    /**
     * Helper pour extraire le token JWT
     */
    private String extractToken(Authentication auth) {
        return "Bearer " + auth.getCredentials().toString();
    }

    private UUID getUserId(Authentication auth) {
        return ((AuthPort.UserDetail) auth.getPrincipal()).id();
    }

    // ========================================================================
    // --- TAG 09a. VEHICLES | GESTION DU PARC [ACTEUR: FLEET MANAGER] ---
    // ========================================================================

    @Tag(name = OpenApiConfig.TAG_VHC_PARC)
    @PostMapping("/vehicles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Créer un véhicule", description = "Enregistrement distant (Pynfi) et initialisation locale. Acteur: Manager.")
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
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Lister mes véhicules", description = "Récupère les véhicules gérés par le manager connecté. Acteur: Manager.")
    public Flux<Vehicle> getVehicles(
            @RequestParam(required = false) UUID fleetId,
            Authentication auth) {
        return vehicleUseCase.getVehicles(getUserId(auth), false, extractToken(auth), fleetId);
    }

    @Tag(name = OpenApiConfig.TAG_VHC_PARC)
    @GetMapping("/vehicles/{vehicleId}")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN')")
    @Operation(summary = "Détails complets d'un véhicule", description = "Agrégation Identité + Finance + Maintenance + Opérationnel. Acteur: Manager/Admin.")
    public Mono<Vehicle> getVehicle(@PathVariable UUID vehicleId, Authentication auth) {
        return vehicleUseCase.getVehicleDetails(vehicleId, extractToken(auth));
    }

    @Tag(name = OpenApiConfig.TAG_VHC_PARC)
    @PutMapping("/vehicles/{vehicleId}")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Mise à jour véhicule (Manager UI)", description = "Accepte le payload partiel ApiVehicle du front.")
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
        return vehicleUseCase.patchVehicleInfo(vehicleId, updates, extractToken(auth));
    }

    @Tag(name = OpenApiConfig.TAG_VHC_PARC)
    @PutMapping("/vehicles/{vehicleId}/gallery")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Mettre à jour photo + galerie (JSON URLs)")
    public Mono<Vehicle> updateGallery(
            @PathVariable UUID vehicleId,
            @RequestBody VehicleGalleryUpdateRequest request,
            Authentication auth) {
        return vehicleUseCase.updateVehicleGallery(
                vehicleId,
                request.photoUrl(),
                request.galleryUrls(),
                extractToken(auth)
        );
    }

@Tag(name = OpenApiConfig.TAG_VHC_PARC)
    @PatchMapping("/vehicles/{vehicleId}")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Mise à jour partielle (Admin/Correction)", description = "Permet de corriger une immatriculation ou un VIN.")
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

        return vehicleUseCase.patchVehicleInfo(vehicleId, updates, extractToken(auth));
    }

    @Tag(name = OpenApiConfig.TAG_VHC_PARC)
    @PutMapping("/vehicles/{vehicleId}/financial-parameters")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Paramètres Financiers", description = "Mise à jour Assurance, Coût/KM, Achat. Acteur: Manager.")
    public Mono<Vehicle> updateFinancial(@PathVariable UUID vehicleId, @RequestBody VehicleParameters.Financial params, Authentication auth) {
        return vehicleUseCase.updateFinancialParameters(vehicleId, params, extractToken(auth));
    }

@Tag(name = OpenApiConfig.TAG_VHC_PARC)
    @PutMapping("/vehicles/{vehicleId}/maintenance-parameters")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
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

        return vehicleUseCase.updateMaintenanceParameters(vehicleId, params, extractToken(auth));
    }


    @Tag(name = OpenApiConfig.TAG_VHC_PARC)
    @DeleteMapping("/vehicles/{vehicleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Supprimer un véhicule", description = "Suppression physique distante et locale. Acteur: Manager.")
    public Mono<Void> delete(@PathVariable UUID vehicleId, Authentication auth) {
        return vehicleUseCase.removeVehicle(vehicleId, extractToken(auth));
    }

    // ========================================================================
    // --- TAG 09c. VEHICLES | OPÉRATIONNEL [ACTEUR: DRIVER / MANAGER] ---
    // ========================================================================

    @Tag(name = OpenApiConfig.TAG_VHC_OP)
    @GetMapping("/vehicles/{vehicleId}/operational")
    @PreAuthorize("hasAnyRole('FLEET_DRIVER', 'FLEET_MANAGER')")
    @Operation(summary = "Consulter la télémétrie", description = "Voir position, vitesse et niveau de fuel en temps réel. Acteur: Driver/Manager.")
    public Mono<VehicleParameters.Operational> getOp(@PathVariable UUID vehicleId) {
        return vehicleUseCase.getOperationalData(vehicleId);
    }

    @Tag(name = OpenApiConfig.TAG_VHC_OP)
    @PatchMapping("/vehicles/{vehicleId}/operational")
    @PreAuthorize("hasRole('FLEET_DRIVER')")
    @Operation(summary = "Mise à jour terrain", description = "Permet au chauffeur d'ajuster l'odomètre ou le niveau de carburant. Acteur: Driver.")
    public Mono<Void> updateOp(@PathVariable UUID vehicleId, @RequestBody Map<String, Object> updates) {
        return vehicleUseCase.updateOperationalData(vehicleId, updates);
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