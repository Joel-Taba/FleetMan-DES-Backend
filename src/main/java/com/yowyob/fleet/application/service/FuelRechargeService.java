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
    private final com.yowyob.fleet.domain.ports.in.ManageExpenseUseCase expenseUseCase;
    private final OperationLookup lookup;
    private final OperationNotifier notifications;

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

        // 2. Chauffeur : nom lisible (l'identifiant s'affichait à la place du nom)
        Mono<String> vehicleRegMono = vehiclePersistence.getLocalDataById(cmd.vehicleId())
                .map(v -> v.licensePlate());

        Mono<String> driverNameMono = cmd.driverId() != null
                ? driverPersistence.findById(cmd.driverId())
                        .switchIfEmpty(Mono.error(OperationException.driverNotFound(cmd.driverId())))
                        .flatMap(d -> lookup.fullName(d.userId()))
                : Mono.just("");

        Mono<java.util.Optional<UUID>> actorMono = lookup.localUser(cmd.actorId())
                .map(java.util.Optional::of).defaultIfEmpty(java.util.Optional.empty());

        return Mono.zip(tankCapacityMono, vehicleRegMono, driverNameMono, actorMono)
                .flatMap(tuple -> {
                    BigDecimal tankCapacity = tuple.getT1();
                    String vehicleReg      = tuple.getT2();
                    String driverName      = tuple.getT3().isBlank() ? null : tuple.getT3();
                    UUID actor             = tuple.getT4().orElse(null);

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
                    recharge.setOdometerKm(cmd.odometerKm());
                    recharge.setStationLabel(cmd.stationLabel());
                    recharge.attachReceipt(cmd.receiptUrl(), cmd.receiptName(), cmd.receiptMime());
                    recharge.setCreatedBy(actor);
                    boolean byDriver = actor != null && actor.equals(cmd.driverId());

                    return fuelRechargePersistence.save(recharge)
                            .flatMap(saved -> syncFuelLevel(saved, tankCapacity)
                                    .then(byDriver ? syncDeclaredExpense(saved) : syncExpense(saved))
                                    .then(notifications.fuelCreated(saved, actor))
                                    .thenReturn(saved));
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

                    FuelRecharge updated = existing.copyDetailsTo(new FuelRecharge(
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
                    ));
                    if (cmd.odometerKm() != null) updated.setOdometerKm(cmd.odometerKm());
                    if (cmd.stationLabel() != null) updated.setStationLabel(cmd.stationLabel());

                    return fuelRechargePersistence.save(updated)
                            .flatMap(saved -> syncExpense(saved).thenReturn(saved));
                });
    }

    // ── 5. SUPPRESSION ───────────────────────────────────────────────────────

    @Override
    public Mono<Void> delete(UUID id) {
        return fuelRechargePersistence.existsById(id)
                .flatMap(exists -> {
                    if (!exists) return Mono.error(OperationException.fuelRechargeNotFound(id));
                    return expenseUseCase.deleteAutoExpenses(id)
                            .then(fuelRechargePersistence.deleteById(id));
                });
    }

    // ── HELPERS PRIVÉS ───────────────────────────────────────────────────────

    /** Plein déclaré par le chauffeur : sa dépense attend la validation du gestionnaire, ticket joint. */
    private Mono<Void> syncDeclaredExpense(FuelRecharge recharge) {
        return expenseUseCase.syncDeclaredAutoExpense(
                com.yowyob.fleet.domain.model.Expense.ExpenseType.FUEL,
                recharge.getId(),
                recharge.getPrice(),
                "Carburant : " + recharge.getQuantity().stripTrailingZeros().toPlainString() + " L",
                recharge.getVehicleId(),
                recharge.getDriverId(),
                recharge.getReceiptUrl(),
                recharge.getReceiptName(),
                recharge.getReceiptMime());
    }

    /** Dépense FUEL auto-générée, alignée sur le prix du plein. */
    private Mono<Void> syncExpense(FuelRecharge recharge) {
        return expenseUseCase.syncAutoExpense(
                com.yowyob.fleet.domain.model.Expense.ExpenseType.FUEL,
                recharge.getId(),
                recharge.getPrice(),
                "Carburant : " + recharge.getQuantity().stripTrailingZeros().toPlainString() + " L",
                recharge.getVehicleId(),
                recharge.getDriverId());
    }

    /** Niveau courant en % (« 45 », « 45% », « FULL ») ; null s'il est inconnu. */
    private static BigDecimal parseFuelLevel(String level) {
        if (level == null || level.isBlank()) return null;
        String value = level.trim().toUpperCase();
        if ("FULL".equals(value)) return BigDecimal.valueOf(100);
        try {
            return new BigDecimal(value.replace("%", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Met à jour le fuel_level dans operational_parameters après une recharge : le niveau
     * courant + la quantité rechargée rapportée à la capacité du réservoir, plafonné à 100 %.
     * Avant, le niveau était remplacé par la seule recharge (un appoint de 10 L dans un
     * réservoir à moitié plein le faisait « tomber » à ~15 %), et « FULL » s'affichait 0 %
     * côté front, qui attend un pourcentage numérique.
     */
    private Mono<Void> syncFuelLevel(FuelRecharge recharge, BigDecimal tankCapacity) {
        boolean knownCapacity = tankCapacity.compareTo(BigDecimal.ZERO) != 0;
        if (!knownCapacity) {
            log.warn("⚠️ Capacité réservoir inconnue pour le véhicule {} : fuel_level non mis à jour",
                    recharge.getVehicleId());
            if (recharge.getOdometerKm() == null) {
                return Mono.empty();
            }
        }

        return operationalRepo.findByVehicleId(recharge.getVehicleId())
                .flatMap(params -> {
                    if (knownCapacity) {
                        // Calcul du nouveau niveau en pourcentage
                        BigDecimal added = recharge.getQuantity()
                                .divide(tankCapacity, 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100));
                        BigDecimal current = parseFuelLevel(params.getFuelLevel());
                        BigDecimal percentage = (current != null ? current : BigDecimal.ZERO).add(added)
                                .min(BigDecimal.valueOf(100));
                        params.setFuelLevel(percentage.setScale(0, RoundingMode.HALF_UP) + "%");
                    }
                    // Relevé du compteur au plein, même sans capacité de réservoir connue :
                    // ne fait jamais reculer le kilométrage connu.
                    if (recharge.getOdometerKm() != null && (params.getOdometerReading() == null
                            || recharge.getOdometerKm().compareTo(params.getOdometerReading()) > 0)) {
                        params.setOdometerReading(recharge.getOdometerKm());
                        params.setMileage(recharge.getOdometerKm());
                    }
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
