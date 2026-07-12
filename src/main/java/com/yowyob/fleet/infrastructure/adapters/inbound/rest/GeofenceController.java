package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.fleet.application.service.GeofenceService;
import com.yowyob.fleet.domain.model.GeofencePoint;
import com.yowyob.fleet.domain.model.GeofenceZone;
import com.yowyob.fleet.domain.ports.in.ManageGeofenceUseCase;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.dto.GeofenceUpdateDTO;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.dto.GeofenceZoneDTORequest;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.GeofenceEventEntity;
import com.yowyob.fleet.infrastructure.config.OpenApiConfig;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/geofence")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_GEOFENCING)
@PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
@SecurityRequirement(name = "bearerAuth")
public class GeofenceController {

    private final ManageGeofenceUseCase geofenceService;
    private final ObjectMapper objectMapper;

    private UUID getUserId(Authentication auth) {
        return ((AuthPort.UserDetail) auth.getPrincipal()).id();
    }

    @PostMapping("/zones")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer une nouvelle géofence", description = "Envoie la zone au moteur spatial. Pour un POLYGON, la première et la dernière coordonnée doivent être identiques.")
    public Mono<GeofenceZone> create(@Valid @RequestBody GeofenceZoneDTORequest request, Authentication auth) {

        // Conversion manuelle du DTO vers le Domaine GeofenceZone
        List<GeofencePoint> vertices = List.of();
        if (request.polygon() != null && !request.polygon().coordinates().isEmpty()) {
            // On extrait le premier ring (index 0) du polygone
            vertices = request.polygon().coordinates().get(0).stream()
                    .map(coord -> new GeofencePoint(null, coord.get(1), coord.get(0), null)) // Index 1=Lat, 0=Lng
                    .toList();
        }

        // Extract center point for circle zones
        Double centerLatitude = null;
        Double centerLongitude = null;
        if (request.center() != null && !request.center().coordinates().isEmpty()) {
            centerLongitude = request.center().coordinates().get(0);
            centerLatitude = request.center().coordinates().get(1);
        }

        UUID fleetManagerId = getUserId(auth);
        // Create zone with the fleetManagerId as the fleetId (zones belong to fleet
        // managers)
        GeofenceZone domainZone = new GeofenceZone(
                null,
                null, // On gère l'association zone-fleet dans le service, pas besoin de l'ID de
                      // flotte ici
                fleetManagerId, // Use fleetManagerId as managerId since zones are managed at fleet manager
                                // level
                request.title(),
                request.description(),
                request.type(),
                centerLatitude,
                centerLongitude,
                request.radius(),
                request.isTemporalEnabled(),
                request.startTime(),
                request.endTime(),
                null,
                request.isConditionalEnabled(),
                null, null, null,
                true, // isActive
                null, null,
                vertices);

        // Passer fleetManagerId au service
        return geofenceService.createZone(domainZone);
    }

    @GetMapping("/circles")
    @Operation(summary = "Lister mes zones circulaires")
    public Flux<Map<String, Object>> listCircles(Authentication auth) {
        return geofenceService.getMyExternalZones(getUserId(auth), "CIRCLE");
    }

    @GetMapping("/polygons")
    @Operation(summary = "Lister mes zones polygonales")
    public Flux<Map<String, Object>> listPolygons(Authentication auth) {
        return geofenceService.getMyExternalZones(getUserId(auth), "POLYGON");
    }

    @GetMapping("/fleet/{fleetId}")
    @Operation(summary = "Lister les zones d'une flotte spécifique", description = "Retourne les zones enrichies avec la géométrie du moteur externe.")
    public Flux<Map<String, Object>> listByFleet(@PathVariable UUID fleetId, Authentication auth) {
        return geofenceService.getZonesByFleet(getUserId(auth), fleetId);
    }

    @GetMapping("/{type}/{id}")
    @Operation(summary = "Récupérer le détail d'une géofence (par type + id)")
    public Mono<Map<String, Object>> getById(
            @PathVariable String type,
            @PathVariable UUID id) {
        // type peut être 'circle' ou 'polygon'
        return geofenceService.getExternalZoneDetails(type, id);
    }

    @PutMapping("/{type}/{id}")
    @Operation(summary = "Modifier une géofence", description = "Mise à jour partielle des champs.")
    public Mono<Void> update(
            @PathVariable String type,
            @PathVariable UUID id,
            @RequestBody GeofenceUpdateDTO updates) { // Utilisation du DTO ici

        // Conversion DTO -> Map pour le service
        @SuppressWarnings("unchecked")
        Map<String, Object> updateMap = objectMapper.convertValue(updates, Map.class);

        return geofenceService.updateRemoteZone(type, id, updateMap);
    }

    // Nouveau endpoint simplifié
    @GetMapping("/zones/{id}")
    @Operation(summary = "Détails d'une zone par ID unique")
    public Mono<Map<String, Object>> getDetails(@PathVariable UUID id) {
        // Le premier paramètre 'type' est désormais ignoré par le service qui cherche
        // en DB
        return geofenceService.getExternalZoneDetails(null, id);
    }

    @GetMapping("/my-zones")
    @Operation(summary = "Ressortir mes zones de géofence")
    public Flux<Map<String, Object>> listMyZones(Authentication auth) {
        AuthPort.UserDetail user = (AuthPort.UserDetail) auth.getPrincipal();
        return geofenceService.getMyExternalZones(getUserId(auth), "all");
    }
    // Dans GeofenceController.java

    @GetMapping
    @Operation(summary = "Lister toutes les zones du service")
    public Flux<Map<String, Object>> listAll(Authentication auth) {
        return geofenceService.getAllExternalZones("all");
    }

    @GetMapping("/alerts/all")
    @Operation(summary = "Récupérer toutes les alertes du service")
    public Mono<Map<String, Object>> getAlerts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return geofenceService.getExternalAlerts(page, size);
    }

    @GetMapping("/alerts")
    @Operation(summary = "Récupérer mes alertes (Sécurisé par Flotte)", description = "Retourne uniquement les alertes des véhicules appartenant au manager connecté.")
    public Mono<Map<String, Object>> getAlerts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {

        // 1. On récupère l'ID du manager connecté
        UUID managerId = getUserId(auth);

        // 2. On appelle la méthode sécurisée du service
        return geofenceService.getExternalAlerts(page, size);
    }

    @PatchMapping("/{id}/assign-fleet/{fleetId}")
    @Operation(summary = "Assigner une zone à une flotte")
    public Mono<Void> assignToFleet(@PathVariable UUID id, @PathVariable UUID fleetId, Authentication auth) {
        return ((GeofenceService) geofenceService).assignZoneToFleet(id, fleetId, getUserId(auth));
    }

    @DeleteMapping("/{type}/{id}")
    @Operation(summary = "Supprimer une zone de géofence")
    public Mono<Void> delete(@PathVariable String type, @PathVariable UUID id, Authentication auth) {
        return geofenceService.deleteZone(id, type, getUserId(auth));
    }
    // @GetMapping("/events") // Endpoint pour les donnÃ©es LOCALES
    // @Operation(summary = "Historique local des alertes", description =
    // "RÃ©cupÃ¨re les Ã©vÃ©nements stockÃ©s localement (via Kafka).")
    // @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN')")
    // public Flux<GeofenceEventEntity> getLocalEvents(
    // @RequestParam(required = false) UUID vehicleId,
    // @RequestParam(required = false) UUID zoneId,
    // @RequestParam(required = false) String type, // ENTRY / EXIT
    // @RequestParam(required = false) @DateTimeFormat(iso =
    // DateTimeFormat.ISO.DATE) LocalDate date,
    // Authentication auth) {

    // return geofenceService.getEvents(vehicleId, zoneId, type, date);
    // }

}