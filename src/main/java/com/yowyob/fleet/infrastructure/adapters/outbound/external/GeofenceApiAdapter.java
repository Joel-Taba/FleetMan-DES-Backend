package com.yowyob.fleet.infrastructure.adapters.outbound.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.fleet.domain.model.GeofenceZone;
import com.yowyob.fleet.domain.model.Vehicle;
import com.yowyob.fleet.domain.ports.out.ExternalGeofencePort;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.client.GeofenceApiClient;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.client.GeofenceAuthClient;

import org.springframework.http.client.MultipartBodyBuilder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeofenceApiAdapter implements ExternalGeofencePort {

    private final GeofenceApiClient apiClient;
    
    private final GeofenceAuthClient authClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${application.geofence-system-user.username}")
    private String systemUser;

    @Value("${application.geofence-system-user.password}")
    private String systemPass;

    private Mono<String> cachedToken;

    @Override
    public Mono<String> getSystemToken() {
        if (cachedToken == null) {
            Map<String, String> loginReq = Map.of("type", "username", "username", systemUser, "password", systemPass);
            cachedToken = authClient.login(loginReq)
                    .map(res -> "Bearer " + (res.containsKey("token") ? res.get("token") : res.get("accessToken")))
                    .cache(Duration.ofHours(24));
        }
        return cachedToken;
    }

    private String resolveShortType(String type) {
        if (type == null)
            return "p";
        String t = type.toLowerCase();
        if (t.equals("c") || t.contains("circle"))
            return "c";
        return "p";
    }

    @Override
    public Mono synchronizeZone(GeofenceZone zone) {
        return getSystemToken().flatMap(token -> {
            Map<String, Object> request = buildGeofenceMap(zone);
            return apiClient.createZone(request, token)
                    .map(response -> {
                        Object idValue = response.get("id");
                        if (idValue == null) {
                            throw new RuntimeException("L'API externe n'a pas renvoyé d'ID");
                        }

                        String cleanId = idValue.toString().replace("\"", "").trim();

                        log.info("✅ Zone créée en externe. ID nettoyé : {}", cleanId);

                        try {
                            return UUID.fromString(cleanId);
                        } catch (IllegalArgumentException e) {
                            log.error("❌ Format d'UUID invalide reçu de l'API : {}", cleanId);
                            throw new RuntimeException("ID externe invalide : " + cleanId);
                        }
                        // ----------------------
                    });
        });
    }

    @Override
    public Mono<Map<String, Object>> getRemoteZoneDetails(String type, UUID id) {
        String shortType = resolveShortType(type);
        log.info("🌐 [EXTERNAL CALL] Fetching zone details. Type: {}, ID: {}", shortType, id);

        return getSystemToken()
                .flatMap(token -> {
                    log.debug("🔑 Using Token: {}...", token.substring(0, 15));
                    return apiClient.getZoneById(shortType, id, token)
                            .doOnNext(node -> log.info("✅ Response received for zone {}", id))
                            .map(node -> objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() {
                            }))
                            .onErrorResume(e -> {
                                // On capture l'erreur réelle pour ne pas l'étouffer
                                if (e instanceof org.springframework.web.reactive.function.client.WebClientResponseException ex) {
                                    log.error("❌ Geofence Service Error: {} - Body: {}",
                                            ex.getStatusCode(), ex.getResponseBodyAsString());
                                    return Mono.error(
                                            new RuntimeException("Erreur externe: " + ex.getResponseBodyAsString()));
                                }
                                return Mono.error(e);
                            });
                });
    }

    @Override
    public Mono<List<Map<String, Object>>> listRemoteZones(String category) {
      // Appelle la version sans userId (récupère tout si admin, ou ses propres zones si token user)
        return getSystemToken().flatMap(token -> fetchZonesInternal(category, null, token));
    }

    // IMPLEMENTATION DE LA NOUVELLE METHODE
    @Override
    public Mono<List<Map<String, Object>>> getZonesByOwner(UUID ownerId, String category) {
        log.info("🔍 Récupération des zones pour le manager {} (via System Token)", ownerId);
        return getSystemToken().flatMap(token -> fetchZonesInternal(category, ownerId, token));
    }

    @Override
    public Mono<Map<String, Object>> fetchRemoteAlerts(int page, int size) {
        return getSystemToken().flatMap(token -> apiClient.getAlerts(page, size, token));
    }

    @Override
    public Mono<String> checkPointInZone(UUID zoneId, Double lat, Double lng) {
        return getSystemToken().flatMap(token -> apiClient.checkPoint(zoneId.toString(), lat, lng, token));
    }
    // Dans GeofenceApiAdapter.java

    // Helper pour construire le JSON complexe attendu par Geofence
   private Map<String, Object> buildGeofenceMap(GeofenceZone zone) {
        Map<String, Object> request = new HashMap<>();
        
        request.put("title", zone.name());
        request.put("description", zone.description() != null ? zone.description() : ""); 
        request.put("isTemporalEnabled", Boolean.TRUE.equals(zone.getIsTemporalEnabled()));
        request.put("isConditionalEnabled", Boolean.TRUE.equals(zone.getIsConditionalEnabled()));
        request.put("isActive", true);

        if (zone.startTime() != null) {
            request.put("startTime", zone.startTime().toString());
        }
        if (zone.endTime() != null) {
            request.put("endTime", zone.endTime().toString());
        }

        if ("CIRCLE".equalsIgnoreCase(zone.zoneType())) {
            request.put("type", "circle");
            request.put("center", Map.of(
                "type", "Point",
                "coordinates", Arrays.asList(zone.centerLongitude(), zone.centerLatitude())
            ));
            request.put("radius", zone.radius() != null ? zone.radius() : 100.0);
        } else {
            request.put("type", "polygon");
            List<List<Double>> ring = new ArrayList<>();
            if (zone.vertices() != null) {
                ring = new ArrayList<>(zone.vertices().stream()
                        .map(v -> Arrays.asList(v.longitude(), v.latitude())).toList());
                
                if (!ring.isEmpty() && !ring.get(0).equals(ring.get(ring.size()-1))) {
                    ring.add(ring.get(0));
                }
            }
            request.put("polygon", Map.of(
                "type", "Polygon", 
                "coordinates", List.of(ring)
            ));
        }
        return request;
    }

    @Override
    public Flux<Map<String, Object>> getZonesByManager(UUID managerId, String category) {
        return listRemoteZones(category)
                .flatMapMany(Flux::fromIterable);
    }

    @Override
    public Mono<Void> updateRemoteZone(String type, UUID id, Map<String, Object> updates) {
        String shortType = resolveShortType(type);
        return getSystemToken().flatMap(token -> apiClient.updateZone(shortType, id, updates, token)
                .doOnSuccess(v -> log.info("🔄 Zone {} mise à jour", id)));
    }

    @Override
    public Mono<Void> deleteRemoteZone(String type, UUID zoneId) {
        String shortType = resolveShortType(type);
        return getSystemToken().flatMap(token -> apiClient.deleteZone(shortType, zoneId, token));
    }


    // Méthode helper pour éviter la duplication
    private Mono<List<Map<String, Object>>> fetchZonesInternal(String category, UUID userId, String token) {
        Mono<JsonNode> response;

        if ("circles".equalsIgnoreCase(category)) {
            response = apiClient.getCircles(userId, token);
        } else if ("polygons".equalsIgnoreCase(category)) {
            response = apiClient.getPolygons(userId, token);
        } else {
            response = apiClient.getAllZones(userId, token);
        }

        return response.map(jsonNode -> {
            List<Map<String, Object>> result = new ArrayList<>();
            
            // Cas 1 : C'est un tableau JSON direct (Rare pour 'all', courant pour spécifique)
            if (jsonNode.isArray()) {
                jsonNode.forEach(node -> result.add(convertNodeToMap(node)));
            } 
            // Cas 2 : Structure Geofence Engine { "polygons": [], "circles": [] }
            else if (jsonNode.has("polygons") || jsonNode.has("circles")) {
                if (jsonNode.has("polygons") && jsonNode.get("polygons").isArray()) {
                    jsonNode.get("polygons").forEach(node -> result.add(convertNodeToMap(node)));
                }
                if (jsonNode.has("circles") && jsonNode.get("circles").isArray()) {
                    jsonNode.get("circles").forEach(node -> result.add(convertNodeToMap(node)));
                }
            }
            // Cas 3 : Pagination classique "content"
            else if (jsonNode.has("content") && jsonNode.get("content").isArray()) {
                jsonNode.get("content").forEach(node -> result.add(convertNodeToMap(node)));
            } 
            // Cas 4 : Objet unique (Erreur ou fallback) -> On évite de l'ajouter si c'est le conteneur racine vide
            else if (!jsonNode.isEmpty()) {
                // On log pour debug si on tombe ici, car ça cause souvent des soucis
                // log.debug("Structure JSON inconnue reçue : {}", jsonNode.fieldNames());
            }
            
            return result;
        }).defaultIfEmpty(Collections.emptyList());
    }


    @SuppressWarnings("unchecked")
    private Map<String, Object> convertNodeToMap(JsonNode node) {
        return objectMapper.convertValue(node, Map.class);
    }
// --- DANS GeofenceApiAdapter.java ---

     @Override
    public Mono<Void> registerVehicleAndAssignToZone(Vehicle vehicle, UUID zoneId, String zoneType) {
        return getSystemToken().flatMap(token -> {
            
            // 1. PrÃ©paration des donnÃ©es "VehicleDTORequest"
            Map<String, Object> vehicleData = new HashMap<>();
            vehicleData.put("brand", vehicle.brand());
            vehicleData.put("model", vehicle.model());
            vehicleData.put("licensePlate", vehicle.licensePlate());
            vehicleData.put("description", "ImportÃ© depuis Fleet Management");
            // geofenceZoneIds est optionnel ou peut Ãªtre une liste vide si le DTO serveur le demande
            vehicleData.put("geofenceZoneIds", Collections.emptyList()); 

            // 2. Construction du Multipart
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("vehicle", vehicleData); 
            // Note: Pas d'image pour l'instant, c'est 'required = false' sur le serveur

            log.info("ðŸš€ Sync VÃ©hicule vers Geofence API (Multipart) : Plaque {}", vehicle.licensePlate());

            // 3. Appel avec le build() qui retourne un MultiValueMap
            return apiClient.createVehicle(builder.build(), token)
                .flatMap(responseNode -> {
                    String remoteVehicleId = extractIdFromResponse(responseNode);
                    if (remoteVehicleId == null) {
                        return Mono.error(new RuntimeException("ID distant introuvable pour : " + vehicle.licensePlate()));
                    }

                    log.info("âœ… VÃ©hicule crÃ©Ã© dans Geofence (ID: {}). Assignation Ã  la zone {}...", remoteVehicleId, zoneId);

                    String shortType = resolveShortType(zoneType);
                    return apiClient.addVehicleToZone(remoteVehicleId, shortType, zoneId, token);
                })
                .doOnSuccess(v -> log.info("ðŸŽ‰ VÃ©hicule assignÃ© Ã  la zone {} avec succÃ¨s.", zoneId))
                .doOnError(e -> log.error("â Œ Erreur Geofence Sync : {}", e.getMessage()));
        });
    }

    // --- CORRECTION MAJEURE ICI AUSSI ---
    @Override
    public Mono<String> registerRemoteVehicle(Vehicle vehicle) {
        return getSystemToken().flatMap(token -> {
            // 1. PrÃ©paration Map
            Map<String, Object> vehicleData = new HashMap<>();
            vehicleData.put("brand", vehicle.brand() != null ? vehicle.brand() : "Unknown");
            vehicleData.put("model", vehicle.model() != null ? vehicle.model() : "Unknown");
            vehicleData.put("licensePlate", vehicle.licensePlate());
            vehicleData.put("description", "VÃ©hicule importÃ© de Fleet Management");
            vehicleData.put("geofenceZoneIds", Collections.emptyList());

            // 2. Construction Multipart
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("vehicle", vehicleData);

            log.info("ðŸš€ Enregistrement vÃ©hicule (Multipart) : {}", vehicle.licensePlate());

            return apiClient.createVehicle(builder.build(), token)
                .map(node -> {
                    String remoteId = extractIdFromResponse(node);
                    if (remoteId == null) {
                        throw new RuntimeException("API Geofence n'a pas retournÃ© d'ID");
                    }
                    return remoteId;
                })
                .doOnSuccess(id -> log.info("âœ… VÃ©hicule enregistrÃ© avec ID : {}", id))
                .doOnError(e -> log.error("â Œ Ã‰chec enregistrement : {}", e.getMessage()));
        });
    }
     // Helper pour extraire l'ID (car le format JSON peut varier)
    private String extractIdFromResponse(JsonNode node) {
        if (node.has("id")) return node.get("id").asText();
        if (node.has("_id")) return node.get("_id").asText();
        if (node.has("data") && node.get("data").has("id")) {
            return node.get("data").get("id").asText();
        }
        return null;
    }

    @Override
    public Mono<Void> addVehicleToZone(String remoteVehicleId, UUID zoneId, String zoneType) {
        String shortType = resolveShortType(zoneType);
        return getSystemToken().flatMap(token -> 
            apiClient.addVehicleToZone(remoteVehicleId, shortType, zoneId, token)
                .doOnSuccess(v -> log.info("ðŸ”— Linked remote vehicle {} to zone {}", remoteVehicleId, zoneId))
        );
    }
}