package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.GeofenceZoneEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Repository
public interface GeofenceR2dbcRepository extends ReactiveCrudRepository<GeofenceZoneEntity, UUID> {
   
    Flux<GeofenceZoneEntity> findByManagerId(UUID managerId);

    // CORRECTION : On retire le JOIN inutile et on filtre réellement par fleet_id
    @Query("SELECT * FROM fleet.geofence_zones WHERE manager_id = :managerId AND fleet_id = :fleetId")
    Flux<GeofenceZoneEntity> findAllByManagerIdAndFleetId(UUID managerId, UUID fleetId);

    Flux<GeofenceZoneEntity> findAllByFleetId(UUID fleetId);

    @Query("""
            SELECT z.* FROM fleet.geofence_zones z
            JOIN fleet.fleet_managers fm ON z.manager_id = fm.user_id
            WHERE fm.created_by_admin_id = :adminId
            """)
    Flux<GeofenceZoneEntity> findAllByOrganizationAdminId(UUID adminId);

    @Query("""
            SELECT z.* FROM fleet.geofence_zones z
            WHERE (
                    z.manager_id = :managerId
                    OR z.fleet_id IN (
                        SELECT f.id FROM fleet.fleets f
                        WHERE f.manager_id = :managerId AND f.deleted_at IS NULL
                    )
                  )
              AND EXISTS (
                SELECT 1 FROM fleet.fleet_managers fm
                WHERE fm.user_id = :managerId AND fm.created_by_admin_id = :adminId
              )
            """)
    Flux<GeofenceZoneEntity> findAllByOrganizationAdminIdAndManagerId(UUID adminId, UUID managerId);

    @Query("""
            SELECT COUNT(*) FROM fleet.geofence_zones z
            JOIN fleet.fleet_managers fm ON z.manager_id = fm.user_id
            WHERE fm.created_by_admin_id = :adminId
            """)
    Mono<Long> countByOrganizationAdminId(UUID adminId);
}