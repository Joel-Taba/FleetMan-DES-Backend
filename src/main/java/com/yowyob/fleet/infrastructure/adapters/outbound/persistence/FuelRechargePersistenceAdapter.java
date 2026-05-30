package com.yowyob.fleet.infrastructure.adapters.outbound.persistence;

import com.yowyob.fleet.domain.model.Coordinates;
import com.yowyob.fleet.domain.model.FuelRecharge;
import com.yowyob.fleet.domain.ports.out.FuelRechargePersistencePort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.FuelRechargeEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.FuelRechargeR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Adapter de persistance pour les Recharges de Carburant.
 * Implémente FuelRechargePersistencePort via Spring Data R2DBC.
 * Assure la conversion Entity ↔ Domain, y compris l'enum StationName.
 */
@Component
@RequiredArgsConstructor
public class FuelRechargePersistenceAdapter implements FuelRechargePersistencePort {

    private final FuelRechargeR2dbcRepository repository;

    @Override
    public Mono<FuelRecharge> save(FuelRecharge fuelRecharge) {
        FuelRechargeEntity entity = toEntity(fuelRecharge);
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID());
            entity.setNew(true);
        }
        return repository.save(entity).map(this::toDomain);
    }

    @Override
    public Mono<FuelRecharge> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Flux<FuelRecharge> findAll() {
        return repository.findAll().map(this::toDomain);
    }

    @Override
    public Flux<FuelRecharge> findAllByManagerId(UUID managerId) {
        return repository.findAllByManagerId(managerId).map(this::toDomain);
    }

    @Override
    public Flux<FuelRecharge> findByVehicleId(UUID vehicleId) {
        return repository.findByVehicleId(vehicleId).map(this::toDomain);
    }

    @Override
    public Flux<FuelRecharge> findByDriverId(UUID driverId) {
        return repository.findByDriverId(driverId).map(this::toDomain);
    }

    @Override
    public Flux<FuelRecharge> findByDateRange(LocalDateTime start, LocalDateTime end, UUID managerId) {
        return repository.findByDateRangeAndManagerId(start, end, managerId).map(this::toDomain);
    }

    @Override
    public Mono<BigDecimal> getTotalQuantityByVehicleId(UUID vehicleId) {
        return repository.getTotalQuantityByVehicleId(vehicleId);
    }

    @Override
    public Mono<BigDecimal> getTotalCostByVehicleId(UUID vehicleId) {
        return repository.getTotalCostByVehicleId(vehicleId);
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

    private FuelRecharge toDomain(FuelRechargeEntity e) {
        Coordinates coords = (e.getLongitude() != null && e.getLatitude() != null)
                ? new Coordinates(e.getLongitude(), e.getLatitude())
                : null;

        FuelRecharge.StationName stationName = null;
        if (e.getStationName() != null) {
            try {
                stationName = FuelRecharge.StationName.valueOf(e.getStationName());
            } catch (IllegalArgumentException ex) {
                stationName = FuelRecharge.StationName.OTHER;
            }
        }

        return new FuelRecharge(
                e.getId(),
                e.getQuantity(),
                e.getPrice(),
                e.getRechargeDateTime(),
                coords,
                stationName,
                e.getVehicleId(),
                e.getVehicleRegistration(),
                e.getDriverId(),
                e.getDriverFullName()
        );
    }

    // ── Conversion Domain → Entity ────────────────────────────────────────────

    private FuelRechargeEntity toEntity(FuelRecharge f) {
        FuelRechargeEntity entity = new FuelRechargeEntity();
        entity.setId(f.getId());
        entity.setQuantity(f.getQuantity());
        entity.setPrice(f.getPrice());
        entity.setRechargeDateTime(f.getRechargeDateTime());
        entity.setStationName(f.getStationName() != null ? f.getStationName().name() : null);
        entity.setVehicleId(f.getVehicleId());
        entity.setVehicleRegistration(f.getVehicleRegistration());
        entity.setDriverId(f.getDriverId());
        entity.setDriverFullName(f.getDriverFullName());

        if (f.getLocation() != null) {
            entity.setLongitude(f.getLocation().longitude());
            entity.setLatitude(f.getLocation().latitude());
        }
        return entity;
    }
}
