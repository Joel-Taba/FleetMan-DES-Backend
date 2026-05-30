package com.yowyob.fleet.infrastructure.adapters.outbound.persistence;

import com.yowyob.fleet.domain.model.Coordinates;
import com.yowyob.fleet.domain.model.Incident;
import com.yowyob.fleet.domain.ports.out.IncidentPersistencePort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.IncidentEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.IncidentR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Adapter de persistance pour les Incidents.
 * Implémente IncidentPersistencePort via Spring Data R2DBC.
 * Assure la conversion Entity ↔ Domain, y compris les enums String ↔ Incident.Type/Severity/Status.
 */
@Component
@RequiredArgsConstructor
public class IncidentPersistenceAdapter implements IncidentPersistencePort {

    private final IncidentR2dbcRepository repository;

    @Override
    public Mono<Incident> save(Incident incident) {
        IncidentEntity entity = toEntity(incident);
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID());
            entity.setNew(true);
        }
        return repository.save(entity).map(this::toDomain);
    }

    @Override
    public Mono<Incident> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Flux<Incident> findAll() {
        return repository.findAll().map(this::toDomain);
    }

    @Override
    public Flux<Incident> findAllByManagerId(UUID managerId) {
        return repository.findAllByManagerId(managerId).map(this::toDomain);
    }

    @Override
    public Flux<Incident> findByVehicleId(UUID vehicleId) {
        return repository.findByVehicleId(vehicleId).map(this::toDomain);
    }

    @Override
    public Flux<Incident> findByDriverId(UUID driverId) {
        return repository.findByDriverId(driverId).map(this::toDomain);
    }

    @Override
    public Flux<Incident> findByType(Incident.Type type, UUID managerId) {
        return repository.findByTypeAndManagerId(type.name(), managerId).map(this::toDomain);
    }

    @Override
    public Flux<Incident> findBySeverity(Incident.Severity severity, UUID managerId) {
        return repository.findBySeverityAndManagerId(severity.name(), managerId).map(this::toDomain);
    }

    @Override
    public Flux<Incident> findByStatus(Incident.Status status, UUID managerId) {
        return repository.findByStatusAndManagerId(status.name(), managerId).map(this::toDomain);
    }

    @Override
    public Flux<Incident> findOpenIncidents(UUID managerId) {
        return repository.findOpenIncidentsByManagerId(managerId).map(this::toDomain);
    }

    @Override
    public Flux<Incident> findByDateRange(LocalDateTime start, LocalDateTime end, UUID managerId) {
        return repository.findByDateRangeAndManagerId(start, end, managerId).map(this::toDomain);
    }

    @Override
    public Mono<Long> countByVehicleId(UUID vehicleId) {
        return repository.countByVehicleId(vehicleId);
    }

    @Override
    public Mono<Long> countByDriverId(UUID driverId) {
        return repository.countByDriverId(driverId);
    }

    @Override
    public Mono<BigDecimal> getTotalCostByVehicleId(UUID vehicleId) {
        return repository.getTotalCostByVehicleId(vehicleId);
    }

    @Override
    public Mono<BigDecimal> getTotalCostByDriverId(UUID driverId) {
        return repository.getTotalCostByDriverId(driverId);
    }

    @Override
    public Mono<Boolean> existsById(UUID id) {
        return repository.existsById(id);
    }

    @Override
    public Mono<Void> deleteById(UUID id) {
        return repository.deleteById(id);
    }

    // ── Conversion Entity → Domain ────────────────────────────────────────────

    private Incident toDomain(IncidentEntity e) {
        Coordinates coords = (e.getLongitude() != null && e.getLatitude() != null)
                ? new Coordinates(e.getLongitude(), e.getLatitude())
                : null;

        Incident incident = new Incident(
                e.getId(),
                Incident.Type.valueOf(e.getType()),
                e.getDescription(),
                e.getSeverity() != null ? Incident.Severity.valueOf(e.getSeverity()) : Incident.Severity.MEDIUM,
                e.getIncidentDateTime(),
                coords,
                e.getCost(),
                e.getReportedBy(),
                e.getVehicleId(),
                e.getVehicleRegistration(),
                e.getDriverId(),
                e.getDriverFullName()
        );

        // Restauration de l'état complet depuis la base
        if (e.getStatus() != null) {
            incident.updateStatus(Incident.Status.valueOf(e.getStatus()));
        }
        if (e.getReport() != null)               incident.setReport(e.getReport());
        if (e.getPoliceReportNumber() != null)   incident.setPoliceReportNumber(e.getPoliceReportNumber());
        if (e.getInsuranceClaimNumber() != null) incident.setInsuranceClaimNumber(e.getInsuranceClaimNumber());
        if (e.getWitnessName() != null || e.getWitnessContact() != null) {
            incident.addWitness(e.getWitnessName(), e.getWitnessContact());
        }

        return incident;
    }

    // ── Conversion Domain → Entity ────────────────────────────────────────────

    private IncidentEntity toEntity(Incident i) {
        IncidentEntity entity = new IncidentEntity();
        entity.setId(i.getId());
        entity.setType(i.getType().name());
        entity.setDescription(i.getDescription());
        entity.setSeverity(i.getSeverity() != null ? i.getSeverity().name() : null);
        entity.setIncidentDateTime(i.getIncidentDateTime());
        entity.setCost(i.getCost());
        entity.setStatus(i.getStatus() != null ? i.getStatus().name() : Incident.Status.REPORTED.name());
        entity.setReport(i.getReport());
        entity.setWitnessName(i.getWitnessName());
        entity.setWitnessContact(i.getWitnessContact());
        entity.setPoliceReportNumber(i.getPoliceReportNumber());
        entity.setInsuranceClaimNumber(i.getInsuranceClaimNumber());
        entity.setReportedBy(i.getReportedBy());
        entity.setResolvedAt(i.getResolvedAt());
        entity.setVehicleId(i.getVehicleId());
        entity.setVehicleRegistration(i.getVehicleRegistration());
        entity.setDriverId(i.getDriverId());
        entity.setDriverFullName(i.getDriverFullName());

        if (i.getLocation() != null) {
            entity.setLongitude(i.getLocation().longitude());
            entity.setLatitude(i.getLocation().latitude());
        }
        return entity;
    }
}
