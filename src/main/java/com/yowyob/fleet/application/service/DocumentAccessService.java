package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.DocumentException;
import com.yowyob.fleet.domain.model.DocumentCatalog.TargetType;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Qui peut lire, déposer et contrôler les pièces d'un dossier.
 * Véhicule : gestionnaire de sa flotte ou administrateur ; le chauffeur qui le conduit lit ses papiers.
 * Chauffeur : lui-même (lecture, dépôt) ; le contrôle revient au gestionnaire de sa flotte ou à l'administrateur.
 * Gestionnaire : lui-même (lecture, dépôt) ; le contrôle revient à l'administrateur de son entreprise.
 * Ainsi personne ne valide les pièces de son propre dossier.
 */
@Service
@RequiredArgsConstructor
public class DocumentAccessService {

    private final AccessScopeService accessScope;
    private final ResourceAccessService resourceAccess;
    private final DatabaseClient db;

    public Mono<Void> assertCanReview(UUID actorId, TargetType type, UUID targetId) {
        return switch (type) {
            case VEHICLE -> requireVehicle(targetId).then(resourceAccess.assertVehicleAccess(actorId, targetId));
            case DRIVER -> driverFleet(targetId)
                    .switchIfEmpty(Mono.error(new AccessDeniedException("Chauffeur hors périmètre.")))
                    .flatMap(fleetId -> accessScope.assertFleetAccess(actorId, fleetId));
            case MANAGER -> accessScope.resolveLocalUserId(actorId).flatMap(localId -> managerAdmin(targetId)
                    .filter(localId::equals)
                    .switchIfEmpty(Mono.error(new AccessDeniedException("Gestionnaire hors périmètre.")))
                    .then());
        };
    }

    public Mono<Void> assertCanSubmit(UUID actorId, TargetType type, UUID targetId) {
        return type == TargetType.VEHICLE ? assertCanReview(actorId, type, targetId) : selfOrReviewer(actorId, type, targetId);
    }

    public Mono<Void> assertCanRead(UUID actorId, TargetType type, UUID targetId) {
        if (type == TargetType.VEHICLE) {
            return assertCanReview(actorId, type, targetId)
                    .onErrorResume(AccessDeniedException.class,
                            e -> resourceAccess.assertDriverOperatesVehicle(actorId, targetId))
                    .onErrorResume(com.yowyob.fleet.domain.exception.FleetException.class,
                            e -> resourceAccess.assertDriverOperatesVehicle(actorId, targetId));
        }
        return selfOrReviewer(actorId, type, targetId);
    }

    private Mono<Void> selfOrReviewer(UUID actorId, TargetType type, UUID targetId) {
        return accessScope.resolveLocalUserId(actorId).flatMap(localId -> localId.equals(targetId)
                ? Mono.<Void>empty()
                : assertCanReview(actorId, type, targetId));
    }

    private Mono<Void> requireVehicle(UUID vehicleId) {
        return db.sql("SELECT COUNT(*) > 0 AS ok FROM fleet.vehicles WHERE id = :id")
                .bind("id", vehicleId)
                .map(row -> Boolean.TRUE.equals(row.get("ok", Boolean.class)))
                .one()
                .flatMap(ok -> ok ? Mono.<Void>empty() : Mono.<Void>error(DocumentException.targetNotFound(vehicleId)));
    }

    private Mono<UUID> driverFleet(UUID driverId) {
        return db.sql("SELECT fleet_id FROM fleet.drivers WHERE user_id = :id")
                .bind("id", driverId)
                .map(row -> row.get("fleet_id", UUID.class))
                .one();
    }

    private Mono<UUID> managerAdmin(UUID managerId) {
        return db.sql("SELECT created_by_admin_id FROM fleet.fleet_managers WHERE user_id = :id")
                .bind("id", managerId)
                .map(row -> row.get("created_by_admin_id", UUID.class))
                .one();
    }
}
