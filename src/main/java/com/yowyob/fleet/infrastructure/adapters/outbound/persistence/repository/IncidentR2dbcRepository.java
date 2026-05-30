package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.IncidentEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface IncidentR2dbcRepository extends ReactiveCrudRepository<IncidentEntity, UUID> {

    Flux<IncidentEntity> findByVehicleId(UUID vehicleId);

    Flux<IncidentEntity> findByDriverId(UUID driverId);

    /**
     * Récupère tous les incidents des véhicules appartenant à un manager donné.
     */
    @Query("SELECT i.* FROM fleet.incidents i " +
           "JOIN fleet.vehicles v ON i.vehicle_id = v.id " +
           "WHERE v.manager_id = :managerId " +
           "ORDER BY i.incident_date_time DESC")
    Flux<IncidentEntity> findAllByManagerId(UUID managerId);

    /**
     * Filtre par type d'incident pour un manager donné.
     */
    @Query("SELECT i.* FROM fleet.incidents i " +
           "JOIN fleet.vehicles v ON i.vehicle_id = v.id " +
           "WHERE i.type = :type AND v.manager_id = :managerId " +
           "ORDER BY i.incident_date_time DESC")
    Flux<IncidentEntity> findByTypeAndManagerId(String type, UUID managerId);

    /**
     * Filtre par sévérité pour un manager donné.
     */
    @Query("SELECT i.* FROM fleet.incidents i " +
           "JOIN fleet.vehicles v ON i.vehicle_id = v.id " +
           "WHERE i.severity = :severity AND v.manager_id = :managerId " +
           "ORDER BY i.incident_date_time DESC")
    Flux<IncidentEntity> findBySeverityAndManagerId(String severity, UUID managerId);

    /**
     * Filtre par statut pour un manager donné.
     */
    @Query("SELECT i.* FROM fleet.incidents i " +
           "JOIN fleet.vehicles v ON i.vehicle_id = v.id " +
           "WHERE i.status = :status AND v.manager_id = :managerId " +
           "ORDER BY i.incident_date_time DESC")
    Flux<IncidentEntity> findByStatusAndManagerId(String status, UUID managerId);

    /**
     * Incidents encore ouverts (REPORTED ou UNDER_INVESTIGATION) pour un manager.
     */
    @Query("SELECT i.* FROM fleet.incidents i " +
           "JOIN fleet.vehicles v ON i.vehicle_id = v.id " +
           "WHERE i.status IN ('REPORTED', 'UNDER_INVESTIGATION') AND v.manager_id = :managerId " +
           "ORDER BY i.incident_date_time DESC")
    Flux<IncidentEntity> findOpenIncidentsByManagerId(UUID managerId);

    /**
     * Incidents dans une plage de dates pour un manager donné.
     */
    @Query("SELECT i.* FROM fleet.incidents i " +
           "JOIN fleet.vehicles v ON i.vehicle_id = v.id " +
           "WHERE i.incident_date_time BETWEEN :start AND :end AND v.manager_id = :managerId " +
           "ORDER BY i.incident_date_time DESC")
    Flux<IncidentEntity> findByDateRangeAndManagerId(LocalDateTime start, LocalDateTime end, UUID managerId);

    // ── Agrégats KPIs ─────────────────────────────────────────────────────────

    @Query("SELECT COUNT(*) FROM fleet.incidents WHERE vehicle_id = :vehicleId")
    Mono<Long> countByVehicleId(UUID vehicleId);

    @Query("SELECT COUNT(*) FROM fleet.incidents WHERE driver_id = :driverId")
    Mono<Long> countByDriverId(UUID driverId);

    /**
     * Coût total des incidents pour un véhicule (NULL si aucun incident avec coût).
     */
    @Query("SELECT COALESCE(SUM(cost), 0) FROM fleet.incidents WHERE vehicle_id = :vehicleId")
    Mono<BigDecimal> getTotalCostByVehicleId(UUID vehicleId);

    /**
     * Coût total des incidents impliquant un chauffeur.
     */
    @Query("SELECT COALESCE(SUM(cost), 0) FROM fleet.incidents WHERE driver_id = :driverId")
    Mono<BigDecimal> getTotalCostByDriverId(UUID driverId);
}
