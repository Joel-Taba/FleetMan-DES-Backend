package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.VehicleLocalEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface VehicleLocalR2dbcRepository extends ReactiveCrudRepository<VehicleLocalEntity, UUID> {

    Flux<VehicleLocalEntity> findByFleetId(UUID fleetId);
    Flux<VehicleLocalEntity> findByManagerId(UUID managerId); // Nouveau

    @Query("""
            SELECT v.* FROM fleet.vehicles v
            WHERE v.deleted_at IS NULL
              AND (
                -- Un véhicule en flotte appartient au gestionnaire de sa flotte ; manager_id
                -- ne fait foi que pour un véhicule hors flotte.
                (v.fleet_id IS NULL AND v.manager_id = :managerId)
                OR v.fleet_id IN (
                    SELECT f.id FROM fleet.fleets f
                    WHERE f.manager_id = :managerId AND f.deleted_at IS NULL
                )
              )
            """)
    Flux<VehicleLocalEntity> findAllInAssignedFleets(UUID managerId);

    /** Les véhicules d'une flotte suivent son gestionnaire (réattribution par un administrateur). */
    @org.springframework.data.r2dbc.repository.Modifying
    @Query("UPDATE fleet.vehicles SET manager_id = :managerId WHERE fleet_id = :fleetId AND manager_id <> :managerId")
    Mono<Integer> reassignManagerForFleet(UUID fleetId, UUID managerId);
    Flux<VehicleLocalEntity> findByStatus(String status);

    Flux<VehicleLocalEntity> findByCurrentDriverId(UUID currentDriverId);
    // Les véhicules supprimés (deleted_at) ne comptent ni dans les statistiques ni dans
    // le quota du plan : sans ce filtre, supprimer un véhicule ne libérait aucune place.
    @Query("SELECT COUNT(*) FROM fleet.vehicles WHERE fleet_id = :fleetId AND status = :status AND deleted_at IS NULL")
    Mono<Long> countByFleetIdAndStatus(UUID fleetId, String status);
    @Query("SELECT COUNT(*) FROM fleet.vehicles WHERE fleet_id = :fleetId AND deleted_at IS NULL AND status <> 'RETIRED'")
    Mono<Long> countByFleetId(UUID fleetId);
    @Query("SELECT COUNT(*) FROM fleet.vehicles WHERE manager_id = :managerId AND deleted_at IS NULL AND status <> 'RETIRED'")
    Mono<Long> countByManagerId(UUID managerId);
    // --- AJOUT POUR LES KPIS ---
    // Compte les véhicules d'un manager ayant un statut spécifique (ex: 'ON_TRIP')
    @Query("SELECT COUNT(*) FROM fleet.vehicles WHERE manager_id = :managerId AND status = :status AND deleted_at IS NULL")
    Mono<Long> countByManagerIdAndStatus(UUID managerId, String status);

    Mono<VehicleLocalEntity> findByKernelResourceId(UUID kernelResourceId);

    @Query("SELECT * FROM fleet.vehicles WHERE manager_id = :managerId AND deleted_at IS NULL AND updated_at > :since")
    Flux<VehicleLocalEntity> findActiveByManagerIdUpdatedAfter(UUID managerId, Instant since);

    @Query("SELECT * FROM fleet.vehicles WHERE manager_id = :managerId AND deleted_at IS NOT NULL AND deleted_at > :since")
    Flux<VehicleLocalEntity> findDeletedByManagerIdSince(UUID managerId, Instant since);

    @Query("""
            SELECT v.* FROM fleet.vehicles v
            WHERE v.deleted_at IS NULL
              AND (
                v.manager_id IN (
                    SELECT fm.user_id FROM fleet.fleet_managers fm WHERE fm.created_by_admin_id = :adminId
                )
                OR v.fleet_id IN (
                    SELECT f.id FROM fleet.fleets f
                    WHERE f.deleted_at IS NULL
                      AND (
                        f.created_by = :adminId
                        OR f.manager_id IN (
                            SELECT fm.user_id FROM fleet.fleet_managers fm WHERE fm.created_by_admin_id = :adminId
                        )
                      )
                )
              )
            ORDER BY COALESCE(v.created_at, v.updated_at) DESC NULLS LAST
            """)
    Flux<VehicleLocalEntity> findAllByOrganizationAdminId(UUID adminId);

    @Query("""
            SELECT v.* FROM fleet.vehicles v
            JOIN fleet.fleet_managers fm ON v.manager_id = fm.user_id
            WHERE fm.created_by_admin_id = :adminId
              AND v.deleted_at IS NULL
            ORDER BY COALESCE(v.updated_at, v.created_at) DESC NULLS LAST
            LIMIT :limit
            """)
    Flux<VehicleLocalEntity> findRecentByOrganizationAdminId(UUID adminId, int limit);

    @Query("""
            SELECT COUNT(*) FROM fleet.vehicles v
            JOIN fleet.fleet_managers fm ON v.manager_id = fm.user_id
            WHERE fm.created_by_admin_id = :adminId
              AND v.deleted_at IS NULL
              AND v.status <> 'RETIRED'
            """)
    Mono<Long> countByOrganizationAdminId(UUID adminId);

    @Query("""
            SELECT COUNT(*) FROM fleet.vehicles v
            JOIN fleet.fleet_managers fm ON v.manager_id = fm.user_id
            WHERE fm.created_by_admin_id = :adminId
              AND v.deleted_at IS NULL
              AND v.status = :status
            """)
    Mono<Long> countByOrganizationAdminIdAndStatus(UUID adminId, String status);
}