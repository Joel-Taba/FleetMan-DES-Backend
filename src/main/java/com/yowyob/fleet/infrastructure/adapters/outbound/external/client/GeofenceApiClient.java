package com.yowyob.fleet.infrastructure.adapters.outbound.external.client;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders; 
import org.springframework.util.MultiValueMap; // Import Important
import org.springframework.http.HttpEntity;   // Import Important
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@HttpExchange("/api")
public interface GeofenceApiClient {


    @PostExchange("/geofence")
    Mono<JsonNode> createZone(
        @RequestBody Object request,
        @RequestHeader("Authorization") String token
    );

    @GetExchange("/check")
    Mono<String> checkPoint(
        @RequestParam("zoneId") String zoneId, // Utilisation de String pour plus de souplesse
        @RequestParam("lat") Double lat,
        @RequestParam("lng") Double lng,
        @RequestHeader("Authorization") String token
    );

     @GetExchange("/geofence")
     
    Mono<JsonNode> getAllZones(
         @RequestParam(name = "userId", required = false) UUID userId,
        @RequestHeader("Authorization") String token);

    @GetExchange("/geofence/circles")
    Mono<JsonNode> getCircles(
         @RequestParam(name = "userId", required = false) UUID userId,
        @RequestHeader("Authorization") String token);

    @GetExchange("/geofence/polygons")
    Mono<JsonNode> getPolygons(
         @RequestParam(name = "userId", required = false) UUID userId,
        @RequestHeader("Authorization") String token);

    // --- UNITAIRE ---
    @GetExchange("/geofence/{type}/{id}")
    Mono<JsonNode> getZoneById(
        @PathVariable("type") String type, 
        @PathVariable("id") UUID id, 
        @RequestHeader("Authorization") String token
    );

 
    @PutExchange("/geofence/{type}/{id}")
    Mono<Void> updateZone(
        @PathVariable("type") String type, 
        @PathVariable("id") UUID id, 
        @RequestBody Object request, 
        @RequestHeader("Authorization") String token
    );

    @DeleteExchange("/geofence/{type}/{id}")
    Mono<Void> deleteZone(
        @PathVariable("type") String type, 
        @PathVariable("id") UUID id, 
        @RequestHeader("Authorization") String token
    );

    // --- ALERTS ---
    @GetExchange("/alerts")
    Mono<Map<String, Object>> getAlerts(
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "20") int size,
        @RequestHeader("Authorization") String token
    );
    

    // --- VEHICULES (NOUVEAU) ---
 @PostExchange(url = "/vehicle", contentType = MediaType.MULTIPART_FORM_DATA_VALUE)
    Mono<JsonNode> createVehicle(
        @RequestBody MultiValueMap<String, HttpEntity<?>> parts, 
        @RequestHeader("Authorization") String token
    );

    @PostExchange("/vehicle/{vehicleId}/geofence/{type}/{zoneId}")
    Mono<Void> addVehicleToZone(
        @PathVariable("vehicleId") String remoteVehicleId, 
        @PathVariable("type") String type,
        @PathVariable("zoneId") UUID zoneId,
        @RequestHeader("Authorization") String token
    );

    
}