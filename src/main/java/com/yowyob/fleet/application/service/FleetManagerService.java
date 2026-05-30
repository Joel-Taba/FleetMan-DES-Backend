package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.ManagerException;
import com.yowyob.fleet.domain.ports.in.ManageFleetManagerUseCase;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.domain.ports.out.FleetManagerPersistencePort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.FleetManagerResponse;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.ManagerKpiResponse;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.DriverR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.FleetManagerR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.FleetR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.IncidentR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.MaintenanceR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.FuelRechargeR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.VehicleLocalR2dbcRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FleetManagerService implements ManageFleetManagerUseCase {

    private final FleetManagerR2dbcRepository managerRepository;
    private final FleetManagerPersistencePort managerPersistencePort;
    private final FleetR2dbcRepository fleetRepository;
    private final VehicleLocalR2dbcRepository vehicleRepository;
    private final DriverR2dbcRepository driverRepository;
    private final AuthPort authPort;

    // ── Repositories Opérations Terrain (Phase 6) ─────────────────────────────
    private final MaintenanceR2dbcRepository maintenanceRepository;
    private final IncidentR2dbcRepository incidentRepository;
    private final FuelRechargeR2dbcRepository fuelRechargeRepository;

    private static final String SERVICE_NAME = "FLEET_MANAGEMENT";
    private static final String ROLE_MANAGER = "FLEET_MANAGER";

    @Override
    public Flux<FleetManagerResponse> getAllManagers(String token) {
        return authPort.getUsersByService(SERVICE_NAME, token)
            .filter(user -> user.roles() != null && user.roles().contains(ROLE_MANAGER))
            .flatMap(this::syncAndEnrich);
    }

    @Override
    public Mono<FleetManagerResponse> getManagerDetails(UUID userId, String token) {
        return authPort.getUserById(userId, token)
            .flatMap(this::syncAndEnrich);
    }

    private Mono<FleetManagerResponse> syncAndEnrich(AuthPort.UserDetail remoteUser) {
        return Mono.zip(
            managerRepository.findById(remoteUser.id())
                .switchIfEmpty(managerPersistencePort.createProfile(remoteUser.id(), "Société de " + remoteUser.lastName())
                    .then(managerRepository.findById(remoteUser.id()))),
            fleetRepository.countByManagerId(remoteUser.id())
        ).map(tuple -> {
            var localEntity = tuple.getT1();
            var fleetCount = tuple.getT2();
            return new FleetManagerResponse(
                remoteUser.id(),
                remoteUser.firstName(),
                remoteUser.lastName(),
                remoteUser.email(),
                remoteUser.phone(),
                localEntity.getCompanyName(),
                "ACTIVE",
                fleetCount.intValue(),
                remoteUser.photoUrl()
            );
        });
    }

    @Override
    public Mono<Void> updateManagerCompany(UUID userId, String companyName) {
        if (companyName == null || companyName.trim().isEmpty()) {
            return Mono.error(ManagerException.invalidCompanyData("Le nom ne peut pas être vide."));
        }
        return managerPersistencePort.updateCompany(userId, companyName);
    }

    @Override
    public Mono<ManagerKpiResponse> getManagerKpis(UUID managerId) {
        // Plage du mois courant pour les KPIs temporels
        LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        LocalDateTime now = LocalDateTime.now();

        return Mono.zip(
            // ── KPIs existants ────────────────────────────────────────────────
            fleetRepository.countByManagerId(managerId),
            vehicleRepository.countByManagerId(managerId),
            driverRepository.countByManagerId(managerId),
            vehicleRepository.countByManagerIdAndStatus(managerId, "ON_TRIP"),

            // ── KPIs Opérations Terrain (Phase 6) ────────────────────────────
            // Maintenances du mois courant (filtre applicatif sur le flux)
            maintenanceRepository.findAllByManagerId(managerId)
                .filter(m -> m.getDateTime() != null && m.getDateTime().isAfter(startOfMonth))
                .count(),

            // Incidents ouverts (REPORTED ou UNDER_INVESTIGATION)
            incidentRepository.findOpenIncidentsByManagerId(managerId).count()

        ).flatMap(t -> {
            long totalFleets    = t.getT1();
            long totalVehicles  = t.getT2();
            long totalDrivers   = t.getT3();
            long activeTrips    = t.getT4();
            long maintenancesThisMonth = t.getT5();
            long openIncidents  = t.getT6();

            // Coût total des incidents (tous véhicules du manager)
            Mono<BigDecimal> totalIncidentCostMono = vehicleRepository.findByManagerId(managerId)
                .flatMap(v -> incidentRepository.getTotalCostByVehicleId(v.getId())
                    .defaultIfEmpty(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Consommation et coût carburant du mois courant
            Mono<BigDecimal> totalFuelLitersMono = fuelRechargeRepository.findAllByManagerId(managerId)
                .filter(fr -> fr.getRechargeDateTime() != null && fr.getRechargeDateTime().isAfter(startOfMonth))
                .map(fr -> fr.getQuantity() != null ? fr.getQuantity() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            Mono<BigDecimal> totalFuelCostMono = fuelRechargeRepository.findAllByManagerId(managerId)
                .filter(fr -> fr.getRechargeDateTime() != null && fr.getRechargeDateTime().isAfter(startOfMonth))
                .map(fr -> fr.getPrice() != null ? fr.getPrice() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            return Mono.zip(totalIncidentCostMono, totalFuelLitersMono, totalFuelCostMono)
                .map(ops -> new ManagerKpiResponse(
                    totalFleets,
                    totalVehicles,
                    totalDrivers,
                    activeTrips,
                    maintenancesThisMonth,
                    openIncidents,
                    ops.getT1(),  // totalIncidentCost
                    ops.getT2(),  // totalFuelLitersThisMonth
                    ops.getT3()   // totalFuelCostThisMonth
                ));
        })
        .onErrorResume(e -> {
            log.error("Erreur calcul KPIs pour manager {}: {}", managerId, e.getMessage());
            return Mono.error(ManagerException.kpiCalculationFailed());
        });
    }

    @Override
    public Mono<Void> deleteManager(UUID userId, String token) {
        return authPort.deleteRemoteAccount(userId, token)
                .then(managerRepository.deleteById(userId));
    }
}