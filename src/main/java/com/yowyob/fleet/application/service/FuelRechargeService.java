package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.OperationException;
import com.yowyob.fleet.domain.model.Coordinates;
import com.yowyob.fleet.domain.model.FuelRecharge;
import com.yowyob.fleet.domain.ports.in.ManageFuelRechargeUseCase;
import com.yowyob.fleet.domain.ports.out.DriverPersistencePort;
import com.yowyob.fleet.domain.ports.out.FuelRechargePersistencePort;
import com.yowyob.fleet.domain.ports.out.VehiclePersistencePort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.OperationalParameterR2dbcRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FuelRechargeService implements ManageFuelRechargeUseCase {

    private final FuelRechargePersistencePort fuelRechargePersistence;
    private final VehiclePersistencePort vehiclePersistence;
    private final DriverPersistencePort driverPersistence;
    private final OperationalParameterR2dbcRepository operationalRepo;

    // ── 1. CRÉATION ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    public Mono<FuelRecharge> createFuelRecharge(CreateFuelRechargeCommand cmd) {

        // 1. Vérification existence du véhicule + récupération tankCapacity
        Mono<BigDecimal> tankCapacityMono = vehiclePersistence.getLocalDataById(cmd.vehicleId())
                .switchIfEmpty(Mono.error(OperationException.vehicleNotFound(cmd.vehicleId())))
                .map(v -> v.tankCapacity() != null
                        ? BigDecimal.valueOf(v.tankCapacity())
                        : BigDecimal.ZERO);

        // 2. Résolution optionnelle du nom du chauffeur
        Mono<String> vehicleRegMono = vehiclePersistence.getLocalDataById(cmd.vehicleId())
                .map(v -> v.licensePlate());

        Mono<String> driverNameMono = cmd.driverId() != null
                ? driverPersistence.findById(cmd.driverId())
                        .switchIfEmpty(Mono.error(OperationException.driverNotFound(cmd.driverId())))
                        .map(d -> d.userId().toString())
                : Mono.just("");

        return Mono.zip(tankCapacityMono, vehicleRegMono, driverNameMono)
                .flatMap(tuple -> {
                    BigDecimal tankCapacity = tuple.getT1();
                    String vehicleReg      = tuple.getT2();
                    String driverName      = tuple.getT3().isBlank() ? null : tuple.getT3();

                    Coordinates coords = buildCoordinates(cmd.longitude(), cmd.latitude());

                    FuelRecharge recharge = new FuelRecharge(
                            null,
                            cmd.quantity(),
                            cmd.price(),
                            LocalDateTime.now(),
                            coords,
                            cmd.stationName(),
                            cmd.vehicleId(),
                            vehicleReg,
                            cmd.driverId(),
                            driverName
                    );

                    return fuelRechargePersistence.save(recharge)
                            .flatMap(saved -> syncFuelLevel(saved, tankCapacity).thenReturn(saved));
                });
    }

    // ── 2. LECTURE ───────────────────────────────────────────────────────────

    @Override
    public Mono<FuelRecharge> getById(UUID id) {
        return fuelRechargePersistence.findById(id)
                .switchIfEmpty(Mono.error(OperationException.fuelRechargeNotFound(id)));
    }

    @Override
    public Flux<FuelRecharge> getAllByManager(UUID managerId) {
        return fuelRechargePersistence.findAllByManagerId(managerId);
    }

    @Override
    public Flux<FuelRecharge> getByVehicleId(UUID vehicleId) {
        return vehiclePersistence.getLocalDataById(vehicleId)
                .switchIfEmpty(Mono.error(OperationException.vehicleNotFound(vehicleId)))
                .thenMany(fuelRechargePersistence.findByVehicleId(vehicleId));
    }

    @Override
    public Flux<FuelRecharge> getByDriverId(UUID driverId) {
        return fuelRechargePersistence.findByDriverId(driverId);
    }

    @Override
    public Flux<FuelRecharge> getByDateRange(LocalDateTime start, LocalDateTime end, UUID managerId) {
        return fuelRechargePersistence.findByDateRange(start, end, managerId);
    }

    // ── 3. KPIs ──────────────────────────────────────────────────────────────

    @Override
    public Mono<BigDecimal> getTotalQuantityByVehicleId(UUID vehicleId) {
        return fuelRechargePersistence.getTotalQuantityByVehicleId(vehicleId)
                .defaultIfEmpty(BigDecimal.ZERO);
    }

    @Override
    public Mono<BigDecimal> getTotalCostByVehicleId(UUID vehicleId) {
        return fuelRechargePersistence.getTotalCostByVehicleId(vehicleId)
                .defaultIfEmpty(BigDecimal.ZERO);
    }

    // ── 4. MISE À JOUR ───────────────────────────────────────────────────────

    @Override
    @Transactional
    public Mono<FuelRecharge> update(UpdateFuelRechargeCommand cmd) {
        return fuelRechargePersistence.findById(cmd.rechargeId())
                .switchIfEmpty(Mono.error(OperationException.fuelRechargeNotFound(cmd.rechargeId())))
                .flatMap(existing -> {
                    // Les champs quantity et price sont final dans l'entité (invariants)
                    // → on crée une nouvelle instance avec les valeurs mises à jour
                    BigDecimal newQuantity = cmd.quantity() != null ? cmd.quantity() : existing.getQuantity();
                    BigDecimal newPrice    = cmd.price() != null    ? cmd.price()    : existing.getPrice();

                    Coordinates coords = buildCoordinates(cmd.longitude(), cmd.latitude());

                    FuelRecharge updated = new FuelRecharge(
                            existing.getId(),
                            newQuantity,
                            newPrice,
                            existing.getRechargeDateTime(),
                            coords != null ? coords : existing.getLocation(),
                            cmd.stationName() != null ? cmd.stationName() : existing.getStationName(),
                            existing.getVehicleId(),
                            existing.getVehicleRegistration(),
                            cmd.driverId() != null ? cmd.driverId() : existing.getDriverId(),
                            existing.getDriverFullName()
                    );

                    return fuelRechargePersistence.save(updated);
                });
    }

    // ── 5. SUPPRESSION ───────────────────────────────────────────────────────

    @Override
    public Mono<Void> delete(UUID id) {
        return fuelRechargePersistence.existsById(id)
                .flatMap(exists -> {
                    if (!exists) return Mono.error(OperationException.fuelRechargeNotFound(id));
                    return fuelRechargePersistence.deleteById(id);
                });
    }

    // ── HELPERS PRIVÉS ───────────────────────────────────────────────────────

    /**
     * Met à jour le fuel_level dans operational_parameters après une recharge.
     * Calcule le nouveau niveau en pourcentage : (quantité rechargée / capacité du réservoir) * 100.
     * Le niveau est plafonné à "FULL" si la recharge dépasse la capacité.
     */
    private Mono<Void> syncFuelLevel(FuelRecharge recharge, BigDecimal tankCapacity) {
        if (tankCapacity.compareTo(BigDecimal.ZERO) == 0) {
            log.warn("⚠️ Capacité réservoir inconnue pour le véhicule {} — fuel_level non mis à jour",
                    recharge.getVehicleId());
            return Mono.empty();
        }

        return operationalRepo.findByVehicleId(recharge.getVehicleId())
                .flatMap(params -> {
                    // Calcul du nouveau niveau en pourcentage
                    BigDecimal percentage = recharge.getQuantity()
                            .divide(tankCapacity, 2, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100));

                    String fuelLevel = percentage.compareTo(BigDecimal.valueOf(100)) >= 0
                            ? "FULL"
                            : percentage.setScale(0, RoundingMode.HALF_UP) + "%";

                    params.setFuelLevel(fuelLevel);
                    params.setTimestamp(Instant.now());
                    return operationalRepo.save(params);
                })
                .doOnSuccess(p -> log.info("✅ fuel_level mis à jour pour le véhicule {}", recharge.getVehicleId()))
                .doOnError(e -> log.warn("⚠️ Impossible de mettre à jour fuel_level pour {}: {}",
                        recharge.getVehicleId(), e.getMessage()))
                .then();
    }

    private Coordinates buildCoordinates(Double longitude, Double latitude) {
        if (longitude != null && latitude != null) {
            return new Coordinates(longitude, latitude);
        }
        return null;
    }
}
