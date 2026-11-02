package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.DriverEntity;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface DriverR2dbcRepository extends ReactiveCrudRepository<DriverEntity, UUID> {

    /**
     * Recherche tous les chauffeurs appartenant à une flotte spécifique.
     * Spring Data génère automatiquement le SQL basé sur le nom du champ 'fleetId' dans DriverEntity.
     */
    Flux<DriverEntity> findByFleetId(UUID fleetId);

    /**
     * Recherche par statut (actif/inactif).
     */
    Flux<DriverEntity> findByStatus(String status);

    /**
     * Recherche par ID de véhicule assigné.
     */
    Mono<DriverEntity> findByAssignedVehicleId(UUID assignedVehicleId);

    Mono<DriverEntity> findByKernelActorId(UUID kernelActorId);
    // --- AJOUT TÂCHE 6.2 ---
    @Query("SELECT COUNT(*) FROM fleet.drivers WHERE fleet_id = :fleetId AND deleted_at IS NULL AND status <> 'LEFT'")
    Mono<Long> countByFleetId(UUID fleetId);

    // --- CORRECTIF TÂCHE 7.2 : Compter les chauffeurs par Manager ---
    @Query("SELECT COUNT(d.*) FROM fleet.drivers d " +
           "JOIN fleet.fleets f ON d.fleet_id = f.id " +
           "WHERE f.manager_id = :managerId AND d.deleted_at IS NULL AND d.status <> 'LEFT'")
    Mono<Long> countByManagerId(UUID managerId);

    @Query("""
            SELECT d.* FROM fleet.drivers d
            JOIN fleet.fleets f ON d.fleet_id = f.id
            WHERE f.manager_id = :managerId
              AND d.deleted_at IS NULL
            """)
    Flux<DriverEntity> findAllByManagerId(UUID managerId);

    @Query("""
            SELECT d.* FROM fleet.drivers d
            INNER JOIN fleet.fleets f ON d.fleet_id = f.id
            WHERE f.manager_id = :managerId
              AND d.deleted_at IS NULL
              AND d.updated_at > :since
            """)
    Flux<DriverEntity> findActiveByManagerIdUpdatedAfter(UUID managerId, Instant since);

    @Query("""
            SELECT d.* FROM fleet.drivers d
            INNER JOIN fleet.fleets f ON d.fleet_id = f.id
            WHERE f.manager_id = :managerId
              AND d.deleted_at IS NOT NULL
              AND d.deleted_at > :since
            """)
    Flux<DriverEntity> findDeletedByManagerIdSince(UUID managerId, Instant since);

    @Query("""
            SELECT d.* FROM fleet.drivers d
            JOIN fleet.fleets f ON d.fleet_id = f.id
            JOIN fleet.fleet_managers fm ON f.manager_id = fm.user_id
            WHERE fm.created_by_admin_id = :adminId
              AND d.deleted_at IS NULL
            """)
    Flux<DriverEntity> findAllByOrganizationAdminId(UUID adminId);

    @Query("""
            SELECT COUNT(*) FROM fleet.drivers d
            JOIN fleet.fleets f ON d.fleet_id = f.id
            JOIN fleet.fleet_managers fm ON f.manager_id = fm.user_id
            WHERE fm.created_by_admin_id = :adminId
              AND d.deleted_at IS NULL
              AND d.status <> 'LEFT'
            """)
    Mono<Long> countByOrganizationAdminId(UUID adminId);

    /** Numéro de permis déjà pris (comparaison sans casse ni espaces). */
    @Query("SELECT * FROM fleet.drivers WHERE UPPER(REPLACE(licence_number, ' ', '')) = UPPER(REPLACE(:licenceNumber, ' ', '')) LIMIT 1")
    Mono<DriverEntity> findByLicenceNumberNormalized(String licenceNumber);
}
