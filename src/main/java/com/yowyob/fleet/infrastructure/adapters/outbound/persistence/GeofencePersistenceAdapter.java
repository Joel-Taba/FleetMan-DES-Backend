package com.yowyob.fleet.infrastructure.adapters.outbound.persistence;

import com.yowyob.fleet.domain.model.GeofenceZone;
import com.yowyob.fleet.domain.ports.out.GeofencePersistencePort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.GeofenceEventEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.GeofenceZoneEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.GeofenceEventR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.GeofenceR2dbcRepository;
import com.yowyob.fleet.infrastructure.mappers.GeofenceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeofencePersistenceAdapter implements GeofencePersistencePort {

    private final GeofenceR2dbcRepository zoneRepo;
    private final GeofenceEventR2dbcRepository eventRepo;
    private final GeofenceMapper mapper;
    private final DatabaseClient databaseClient;

    @Override
    public Mono<GeofenceZone> saveZone(GeofenceZone zone) {
        GeofenceZoneEntity entity = mapper.toEntity(zone);

        // Crucial : Comme l'ID est déjà rempli par l'API externe,
        // on force R2DBC à faire un INSERT SQL.
        entity.markNew();

        return zoneRepo.save(entity)
                .map(mapper::toDomain);
    }

    @Override
    public Mono<GeofenceZone> findById(UUID id) {
        return zoneRepo.findById(id).map(mapper::toDomain);
    }

    @Override
    public Flux<GeofenceZone> findByFleetId(UUID fleetId) {
        return zoneRepo.findAllByFleetId(fleetId)
                // ✅ Utilise une lambda explicite pour aider le compilateur
                .map(entity -> mapper.toDomain(entity));
    }

    @Override
    public Mono<Void> deleteById(UUID zoneId) {
        return zoneRepo.deleteById(zoneId);
    }

    @Override
    public Mono<Void> saveEvent(UUID vehicleId, UUID zoneId, String type) {
        GeofenceEventEntity event = GeofenceEventEntity.builder()
                .id(UUID.randomUUID())
                .vehicleId(vehicleId)
                .zoneId(zoneId)
                .type(type) // ENTRY ou EXIT
                .timestamp(Instant.now())
                .isRead(false)
                .severity("INFO")
                .build();

        return eventRepo.save(event)
                .doOnSuccess(e -> log.info("🌍 Event Geofence sauvegardé: {} - Zone: {} - Vehicule: {}", type, zoneId,
                        vehicleId))
                .then();
    }

    // Requête dynamique pour les événements (Binding manuel pour éviter l'injection
    // SQL)
    @Override
    public Flux<GeofenceEventEntity> findEventsWithFilters(UUID vehicleId, UUID zoneId, String type, LocalDate date) {
        StringBuilder queryBuilder = new StringBuilder("SELECT * FROM fleet.geofence_events WHERE 1=1");

        if (vehicleId != null)
            queryBuilder.append(" AND vehicle_id = :vehicleId");
        if (zoneId != null)
            queryBuilder.append(" AND zone_id = :zoneId");
        if (type != null)
            queryBuilder.append(" AND type = :type");
        if (date != null)
            queryBuilder.append(" AND DATE(timestamp) = :date");

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(queryBuilder.toString());

        if (vehicleId != null)
            spec = spec.bind("vehicleId", vehicleId);
        if (zoneId != null)
            spec = spec.bind("zoneId", zoneId);
        if (type != null)
            spec = spec.bind("type", type);
        if (date != null)
            spec = spec.bind("date", date);

        return spec.map((row, metadata) -> GeofenceEventEntity.builder()
                .id(row.get("id", UUID.class))
                .vehicleId(row.get("vehicle_id", UUID.class))
                .zoneId(row.get("zone_id", UUID.class))
                .type(row.get("type", String.class))
                .timestamp(row.get("timestamp", Instant.class))
                .isRead(row.get("is_read", Boolean.class))
                .severity(row.get("severity", String.class))
                .build())
                .all();
    }

    // ... (Imports et début de classe existants)

    @Override
    public Mono<Void> linkZoneToFleetManager(UUID fleetManagerId, UUID zoneId) {
        // 1. On cherche d'abord UNE flotte appartenant au manager (la plus récente)
        String findFleetSql = "SELECT id FROM fleet.fleets WHERE manager_id = :managerId ORDER BY created_at DESC LIMIT 1";

        return databaseClient.sql(findFleetSql)
                .bind("managerId", fleetManagerId)
                .map((row, metadata) -> row.get("id", UUID.class))
                .first()
                // Si aucune flotte trouvée, on renvoie une erreur claire
                .switchIfEmpty(Mono.error(new IllegalStateException(
                        "Impossible de créer une zone : Vous devez d'abord créer une Flotte.")))
                .flatMap(fleetId -> {
                    // 2. Si flotte trouvée, on lie la zone à cette flotte
                    String updateSql = "UPDATE fleet.geofence_zones SET fleet_id = :fleetId WHERE id = :zoneId";
                    return databaseClient.sql(updateSql)
                            .bind("fleetId", fleetId)
                            .bind("zoneId", zoneId)
                            .fetch()
                            .rowsUpdated()
                            .flatMap(rows -> {
                                if (rows == 0) {
                                    return Mono.error(new RuntimeException(
                                            "Erreur technique : Zone créée mais introuvable pour la liaison (ID: "
                                                    + zoneId + ")"));
                                }
                                return Mono.empty();
                            });
                });
    }

    // Dans GeofencePersistenceAdapter.java

    @Override
    public Flux<GeofenceZone> findByManagerId(UUID fleetManagerId) {
        log.debug("🔍 Recherche des zones pour le manager : {}", fleetManagerId);
        return zoneRepo.findByManagerId(fleetManagerId)
                .map(entity -> mapper.toDomain(entity));
    }

    // --- IMPLÉMENTATION DE LA SÉCURISATION ---
    @Override
    public Flux<GeofenceEventEntity> findAlertsByManager(UUID managerId, int page, int size) {
        // JOINTURE : Events -> Vehicles -> Vérification manager_id
        String sql = """
                    SELECT e.*
                    FROM fleet.geofence_events e
                    INNER JOIN fleet.vehicles v ON e.vehicle_id = v.id
                    WHERE v.manager_id = :managerId
                    ORDER BY e.timestamp DESC
                    LIMIT :limit OFFSET :offset
                """;

        return databaseClient.sql(sql)
                .bind("managerId", managerId)
                .bind("limit", size)
                .bind("offset", page * size)
                .map((row, metadata) -> GeofenceEventEntity.builder()
                        .id(row.get("id", UUID.class))
                        .vehicleId(row.get("vehicle_id", UUID.class))
                        .zoneId(row.get("zone_id", UUID.class))
                        .type(row.get("type", String.class))
                        .speed(row.get("speed", Double.class))
                        .dwellTimeMinutes(row.get("dwell_time_minutes", Integer.class))
                        .severity(row.get("severity", String.class))
                        .isRead(row.get("is_read", Boolean.class))
                        .timestamp(row.get("timestamp", Instant.class))
                        .build())
                .all();
    }

}