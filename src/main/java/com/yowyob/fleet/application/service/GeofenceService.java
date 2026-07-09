package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.FleetException;
import com.yowyob.fleet.domain.model.GeofenceZone;
import com.yowyob.fleet.domain.ports.in.ManageGeofenceUseCase;
import com.yowyob.fleet.domain.ports.out.ExternalGeofencePort;
import com.yowyob.fleet.domain.ports.out.FleetRepositoryPort;
import com.yowyob.fleet.domain.ports.out.GeofencePersistencePort;
import com.yowyob.fleet.domain.ports.out.VehiclePersistencePort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.GeofenceZoneEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.GeofenceR2dbcRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeofenceService implements ManageGeofenceUseCase {

    private final GeofencePersistencePort localPersistence;
    private final ExternalGeofencePort externalApi;
    private final GeofenceR2dbcRepository zoneRepo;
    private final VehiclePersistencePort vehiclePersistencePort;
    private final FleetRepositoryPort fleetRepositoryPort;
    

    @Override
    @Transactional
    public Mono<GeofenceZone> createZone(GeofenceZone zone) {
        // 1. Appel API Externe
        return externalApi.synchronizeZone(zone)
                .flatMap(externalId -> {
                    // 2. On crée une nouvelle instance du modèle de domaine avec l'ID externe
                    GeofenceZone zoneWithExternalId = new GeofenceZone(
                            externalId, // <--- L'ID venant de Kamga
                            zone.fleetId(), zone.managerId(), zone.name(), zone.description(),
                            zone.zoneType(), zone.centerLatitude(), zone.centerLongitude(),
                            zone.radius(), zone.isTemporalEnabled(), zone.startTime(),
                            zone.endTime(), zone.activeDays(), zone.isConditionalEnabled(),
                            zone.maxSpeed(), zone.maxDwellTime(), zone.minDwellTime(),
                            zone.isActive(), zone.surfaceArea(), zone.perimeter(),
                            zone.vertices());

                    // 3. Sauvegarde en base locale
                    log.info("💾 Sauvegarde locale de la zone avec ID synchronisé: {}", externalId);
                    return localPersistence.saveZone(zoneWithExternalId);
                })
                .doOnError(e -> log.error("❌ Échec de la synchronisation Geofence: {}", e.getMessage()));
    }


    
    @Override
    public Mono<GeofenceZone> updateZone(UUID zoneId, GeofenceZone zone) {
        // Correction du constructeur : on ajoute zone.managerId() en 3ème position (21
        // paramètres au total)
        GeofenceZone updatedZone = new GeofenceZone(
                zoneId,
                zone.fleetId(),
                zone.managerId(), // <--- AJOUTÉ ICI
                zone.name(),
                zone.description(),
                zone.zoneType(),
                zone.centerLatitude(),
                zone.centerLongitude(),
                zone.radius(),
                zone.isTemporalEnabled(),
                zone.startTime(),
                zone.endTime(),
                zone.activeDays(),
                zone.isConditionalEnabled(),
                zone.maxSpeed(),
                zone.maxDwellTime(),
                zone.minDwellTime(),
                zone.isActive(),
                zone.surfaceArea(),
                zone.perimeter(),
                zone.vertices());

        return externalApi.synchronizeZone(updatedZone)
                .thenReturn(updatedZone);
    }

    @Override
    public Flux<GeofenceZone> getZonesByFleet(UUID fleetId) {
        // 1. Récupérer les IDs locaux liés à la flotte
        return localPersistence.findByFleetId(fleetId)
                // 2. Pour chaque ID, aller chercher les détails distants
                .flatMap(localLink -> externalApi.getRemoteZoneDetails("all", localLink.id()) // "all" ou type
                                                                                              // spécifique si stocké
                        .map(details -> mapRemoteToDomain(localLink.id(), fleetId, localLink.managerId(), details))
                        // Si une zone n'existe plus en distant, on l'ignore ou on nettoie
                        .onErrorResume(e -> Mono.empty()));
    }

    @Override
    public Mono<GeofenceZone> getZoneDetails(UUID zoneId) {
        return Mono.empty();
    }

    // Dans GeofenceService.java

    @Override
    public Flux<Map<String, Object>> getMyExternalZones(UUID managerId, String category) {
        log.info("🔍 Récupération des zones externes pour la catégorie : {}", category);
        return getFilteredRemoteZones(managerId, category, null);
    }

    @Override
    public Flux<Map<String, Object>> getAllExternalZones(String category) {
        log.info("🔍 Récupération des zones externes pour la catégorie : {}", category);
        return externalApi.listRemoteZones(category)
                .flatMapMany(Flux::fromIterable);
    }

    @Override
    public Mono<Map<String, Object>> getExternalZoneDetails(String type, UUID id) {
        log.info("ℹ️ Récupération des détails de la zone : {} (Type: {})", id, type);
        return externalApi.getRemoteZoneDetails(type, id)
                .switchIfEmpty(Mono.error(new RuntimeException("Zone introuvable sur le moteur externe")));
    };

    private GeofenceZone mapRemoteToDomain(UUID id, UUID fleetId, UUID managerId, Map<String, Object> remote) {
        // On extrait les coordonnées si c'est un cercle
        Double lat = null;
        Double lng = null;
        if (remote.get("center") instanceof Map<?, ?> center) {
            List<?> coords = (List<?>) center.get("coordinates");
            if (coords != null && coords.size() >= 2) {
                lng = Double.valueOf(coords.get(0).toString());
                lat = Double.valueOf(coords.get(1).toString());
            }
        }

        return new GeofenceZone(
                id,
                fleetId,
                managerId,
                (String) remote.getOrDefault("title", "Zone"),
                (String) remote.get("description"),
                (String) remote.get("type"),
                lat, lng,
                remote.get("radius") != null ? Double.valueOf(remote.get("radius").toString()) : null,
                (Boolean) remote.getOrDefault("isTemporalEnabled", false),
                null, null, null, // temporal data à parser si besoin
                (Boolean) remote.getOrDefault("isConditionalEnabled", false),
                null, null, null,
                (Boolean) remote.getOrDefault("isActive", true),
                null, null,
                null // Vertices à parser depuis le champ 'polygon' si besoin
        );
    }

    @Override
    public Flux<GeofenceZone> getMyZones(UUID managerId) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getMyZones'");
    }

    // Dans GeofenceService.java, remplace la méthode getFilteredRemoteZones par celle-ci :
private Flux<Map<String, Object>> getFilteredRemoteZones(UUID managerId, String category, UUID optionalFleetId) {
    log.info("ðŸ”  [HYBRID FETCH] Start - Manager: {}, Fleet: {}", managerId, optionalFleetId);

    Flux<GeofenceZoneEntity> localQuery = (optionalFleetId == null)
            ? zoneRepo.findByManagerId(managerId)
            : zoneRepo.findAllByManagerIdAndFleetId(managerId, optionalFleetId);

    return localQuery
            .map(entity -> entity.getId().toString().toLowerCase()) // Conversion String minuscule
            .collect(Collectors.toSet())
            .flatMapMany(authorizedIds -> {
                
                if (authorizedIds.isEmpty()) {
                    log.warn("ðŸš« Aucune zone trouvée en DB locale pour Manager: {} | Flotte: {}", managerId, optionalFleetId);
                    return Flux.empty();
                }

                log.info("âœ… {} zones trouvées localement. Synchronisation avec le moteur Pynfi...", authorizedIds.size());

                return externalApi.listRemoteZones(category)
                        .flatMapMany(Flux::fromIterable)
                        .filter(remoteZoneData -> {
                            Object remoteIdObj = remoteZoneData.get("id");
                            if (remoteIdObj == null) return false;
                            
                            // Nettoyage de l'ID distant (certains moteurs ajoutent des guillemets)
                            String remoteId = remoteIdObj.toString().toLowerCase().replace("\"", "").trim();
                            return authorizedIds.contains(remoteId);
                        })
                        .onErrorResume(ex -> {
                            log.warn("⚠️ Moteur geofence indisponible, retour liste vide: {}", ex.getMessage());
                            return Flux.empty();
                        });
            });
}
     private boolean isTypeMatch(String localType, String requestedCategory) {
        if ("all".equalsIgnoreCase(requestedCategory)) return true;
        if (localType == null) return false;

        String normLocal = localType.toLowerCase();
        String normReq = requestedCategory.toLowerCase();

        // Si on demande des cercles
        if (normReq.contains("circle")) {
            return normLocal.contains("circle") || normLocal.equals("c");
        }
        // Si on demande des polygones
        if (normReq.contains("polygon")) {
            return normLocal.contains("polygon") || normLocal.equals("p");
        }
        return true;
    }

    @Override // C'EST ICI : La méthode corrigée
    public Flux<Map<String, Object>> getZonesByFleet(UUID managerId, UUID fleetId) {
        log.info("ðŸ”  Récupération des zones pour la flotte {} (Manager: {})", fleetId, managerId);
        
        // 1. Vérification de la souveraineté (Est-ce ma flotte ?)
        return fleetRepositoryPort.existsByIdAndManagerId(fleetId, managerId)
                .flatMapMany(exists -> {
                    if (!exists) return Flux.error(FleetException.accessDenied());
                    
                    // 2. Utilisation du moteur de filtrage hybride
                    return getFilteredRemoteZones(managerId, "all", fleetId);
                });
    }



    @Override // GET by ID (Sécurisé par managerId)
    public Mono<Map<String, Object>> getZoneDetails(UUID zoneId, UUID managerId) {
        return zoneRepo.findById(zoneId)
                .filter(link -> link.getManagerId().equals(managerId))
                .switchIfEmpty(Mono.error(new RuntimeException("Zone introuvable ou accès refusé.")))
                .flatMap(link -> externalApi.listRemoteZones("all")
                        .flatMapMany(Flux::fromIterable)
                        .filter(remoteMap -> zoneId.toString().equals(remoteMap.get("id")))
                        .next());
    }

    @Override
    public Mono<Void> deleteZone(UUID zoneId, String type, UUID managerId) {
        return zoneRepo.findById(zoneId)
                .filter(z -> z.getManagerId().equals(managerId))
                .flatMap(link -> externalApi.deleteRemoteZone(type, zoneId)
                        .then(zoneRepo.deleteById(zoneId)));
    }

    @Override
    public Flux<Map<String, Object>> getZonesByManager(UUID managerId, String category) {
        // On délègue à l'adaptateur qui utilisera le System Token pour récupérer les
        // zones de ce manager
        return externalApi.getZonesByOwner(managerId, category)
                .flatMapMany(Flux::fromIterable);
    }

    @Override
    public Flux<Map<String, Object>> getZonesByFleetManager(UUID fleetManagerId) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getZonesByFleetManager'");
    }

    @Override
    public Mono<String> checkPointInZone(UUID zoneId, Double lat, Double lng) {
        return externalApi.checkPointInZone(zoneId, lat, lng);
    }

    @Override
    public Mono<Void> updateRemoteZone(String t, UUID id, Map<String, Object> u) {
        return externalApi.updateRemoteZone(t, id, u);
    }

    // @Override
    // public Flux<GeofenceEventEntity> getEvents(UUID v, UUID z, String t, LocalDate d) {
    //     return localPersistence.findEventsWithFilters(v, z, t, d);
    // }

  @Override
    @Transactional
    public Mono<Void> assignZoneToFleet(UUID zoneId, UUID fleetId, UUID managerId) {
        return zoneRepo.findById(zoneId)
                .filter(z -> z.getManagerId().equals(managerId)) // Sécurité propriétaire
                .switchIfEmpty(Mono.error(new RuntimeException("Zone introuvable ou non autorisée")))
                .flatMap(z -> {
                    // 1. Mise à jour locale
                    z.setFleetId(fleetId);
                    z.setNew(false); // Update
                    
                    return zoneRepo.save(z)
                        // 2. Déclenchement de la synchro de masse
                        .then(syncFleetVehiclesToZone(fleetId, zoneId, z.getZoneType()));
                }).then();
    }

    /**
     * Helper : Prend tous les véhicules d'une flotte et les pousse dans une zone donnée.
     */
    private Mono<Void> syncFleetVehiclesToZone(UUID fleetId, UUID zoneId, String zoneType) {
        log.info("🔄 [Geofence Sync] Ajout des véhicules de la flotte {} à la zone {}", fleetId, zoneId);

        return vehiclePersistencePort.getAllVehicles() // Idéalement : getVehiclesByFleetId(fleetId) serait plus performant
                .filter(v -> fleetId.equals(v.fleetId())) // Filtrage mémoire si pas de méthode repo dédiée
                .filter(v -> v.geofenceRemoteId() != null)
                .flatMap(vehicle -> {
                    log.debug("👉 Ajout véhicule {} ({}) -> Zone {}", vehicle.licensePlate(), vehicle.geofenceRemoteId(), zoneId);
                    
                    return externalApi.addVehicleToZone(vehicle.geofenceRemoteId(), zoneId, zoneType)
                            .onErrorResume(e -> {
                                log.warn("⚠️ Échec partiel pour véhicule {}: {}", vehicle.licensePlate(), e.getMessage());
                                return Mono.empty(); // Best effort : on ne bloque pas tout pour un échec
                            });
                })
                .then();
    }

    //  public Mono<Map<String, Object>> getManagerAlerts(UUID managerId, int page, int size) {
    //     return localPersistence.findAlertsByManager(managerId, page, size)
    //         .collectList()
    //         .map(events -> {
    //             // On formate la réponse pour qu'elle ressemble à ce que le frontend attend (Pagination)
    //             Map<String, Object> response = new HashMap<>();
    //             response.put("content", events);
    //             response.put("page", page);
    //             response.put("size", size);
    //             response.put("totalElements", events.size()); // Approximation (il faudrait un count() séparé pour le vrai total)
    //             return response;
    //         });
    // }
    
    @Override
    public Mono<Map<String, Object>> getExternalAlerts(int p, int s) {
        return externalApi.fetchRemoteAlerts(p, s);
    }
    


    // @Override
    // public Mono<Void> handleIncomingAlert(UUID remoteVehicleId, UUID zoneId, String type, Instant timestamp) {
    //     log.info("⚡ [ALERTE KAFKA] Traitement : Véhicule {} -> Zone {} ({})", remoteVehicleId, zoneId, type);

    //     // 2. Sauvegarde locale (Historique)
    //     return localPersistence.saveEvent(remoteVehicleId, zoneId, type)
    //             .flatMap(unused -> {
    //                 // 3. Récupération des infos pour enrichir le message (Optionnel mais mieux)
    //                 // Idéalement, on chercherait le manager propriétaire de la zone pour avoir son email/token
    //                 // Pour l'exemple, on envoie une notif générique au manager concerné
                    
    //                 return sendAlertNotification(remoteVehicleId, zoneId, type, timestamp);
    //             })
    //             .doOnSuccess(v -> log.info("✅ Alerte traitée et notifiée."))
    //             .doOnError(e -> log.error("❌ Erreur traitement alerte : {}", e.getMessage()));
    // }

    /**
     * Construit et envoie la notification
     */
    // private Mono<Void> sendAlertNotification(UUID vehicleId, UUID zoneId, String type, Instant timestamp) {
    //     // Logique métier : On n'alerte que si c'est CRITIQUE ou selon paramètres
    //     // Ici on alerte sur tout pour tester.

    //     String message = String.format("Alerte Geofence : Le véhicule %s vient de faire %s dans la zone %s", 
    //                                    vehicleId, type, zoneId);

    //     SendNotificationRequest request = SendNotificationRequest.builder()
    //             .notificationType(NotificationType.PUSH) // Ou EMAIL, SMS
    //             .templateId(101) // ID fictif d'un template "Geofence Alert" dans ton système de notif
    //             .to(List.of("manager_token_firebase_ou_email")) // À récupérer dynamiquement via le FleetManager
    //             .data(Map.of(
    //                     "vehicleId", vehicleId.toString(),
    //                     "zoneId", zoneId.toString(),
    //                     "event", type,
    //                     "time", timestamp.toString()
    //             ))
    //             .build();

    //     // On envoie en "Fire & Forget" (on ne bloque pas si la notif échoue)
    //     return notificationPort.sendNotification(request)
    //             .onErrorResume(e -> {
    //                 log.warn("⚠️ Impossible d'envoyer la notification Push : {}", e.getMessage());
    //                 return Mono.just(false);
    //             })
    //             .then();
    // }
}