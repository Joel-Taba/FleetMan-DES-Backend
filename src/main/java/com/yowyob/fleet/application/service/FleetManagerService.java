package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.ManagerException;
import com.yowyob.fleet.domain.ports.in.ManageFleetManagerUseCase;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.domain.ports.out.FleetManagerPersistencePort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.FleetManagerResponse;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.ManagerKpiResponse;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.FleetManagerGalleryImageEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.UserLocalEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.DriverR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.FleetManagerGalleryImageR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.FleetManagerR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.FleetR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.IncidentR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.MaintenanceR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.FuelRechargeR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.UserLocalR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.VehicleLocalR2dbcRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private final UserLocalR2dbcRepository userRepository;
    private final FleetManagerGalleryImageR2dbcRepository galleryRepository;

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
        // Le Kernel ne porte ni nom/prénom/téléphone dans le JWT ni sur
        // GET /api/users/me (vérifié) : remoteUser.firstName()/lastName()/phone()
        // sont donc systématiquement null. La donnée fiable est notre propre
        // fleet.users, alimentée à l'inscription (ou via les migrations de
        // rattrapage pour les comptes de démo provisionnés hors flux applicatif).
        return Mono.zip(
            managerRepository.findById(remoteUser.id())
                .switchIfEmpty(managerPersistencePort.createProfile(remoteUser.id(), "Société de " + remoteUser.lastName())
                    .then(managerRepository.findById(remoteUser.id()))),
            fleetRepository.countByManagerId(remoteUser.id()),
            userRepository.findById(remoteUser.id())
                .switchIfEmpty(userRepository.findByKernelId(remoteUser.id()))
                .defaultIfEmpty(new UserLocalEntity()),
            galleryRepository.findByManagerId(remoteUser.id())
                .map(FleetManagerGalleryImageEntity::getImagePath)
                .collectList()
        ).map(tuple -> {
            var localEntity = tuple.getT1();
            var fleetCount = tuple.getT2();
            var localUser = tuple.getT3();
            var gallery = tuple.getT4();
            return new FleetManagerResponse(
                remoteUser.id(),
                nonBlank(remoteUser.firstName(), localUser.getFirstName()),
                nonBlank(remoteUser.lastName(), localUser.getLastName()),
                nonBlank(remoteUser.email(), localUser.getEmail()),
                nonBlank(remoteUser.phone(), localUser.getPhone()),
                localEntity.getCompanyName(),
                "ACTIVE",
                fleetCount.intValue(),
                // Logo entreprise (localEntity.logoUrl) distinct de la photo de
                // profil personnelle (localUser.photoUrl) : on garde ce dernier
                // en repli seulement si aucun logo dédié n'a encore été défini.
                nonBlank(localEntity.getLogoUrl(), localUser.getPhotoUrl()),
                gallery
            );
        });
    }

    private static String nonBlank(String value, String fallback) {
        return (value != null && !value.isBlank()) ? value : fallback;
    }

    @Override
    public Mono<Void> updateManagerCompany(UUID userId, String companyName) {
        if (companyName == null || companyName.trim().isEmpty()) {
            return Mono.error(ManagerException.invalidCompanyData("Le nom ne peut pas être vide."));
        }
        return managerPersistencePort.updateCompany(userId, companyName);
    }

    @Override
    @Transactional
    public Mono<Void> updateManagerGallery(UUID userId, String photoUrl, java.util.List<String> galleryUrls) {
        Mono<Void> logoUpdate = photoUrl != null
                ? managerRepository.findById(userId)
                        .flatMap(e -> {
                            e.setLogoUrl(photoUrl);
                            e.setNew(false);
                            return managerRepository.save(e);
                        })
                        .then()
                : Mono.empty();
        Mono<Void> galleryUpdate = galleryUrls != null
                ? replaceGallery(userId, galleryUrls)
                : Mono.empty();
        return logoUpdate.then(galleryUpdate);
    }

    private Mono<Void> replaceGallery(UUID managerId, java.util.List<String> galleryUrls) {
        return galleryRepository.findByManagerId(managerId)
                .flatMap(img -> galleryRepository.deleteById(img.getId()))
                .then(Flux.fromIterable(galleryUrls)
                        .filter(url -> url != null && !url.isBlank())
                        .concatMap(url -> galleryRepository.save(
                                new FleetManagerGalleryImageEntity(UUID.randomUUID(), managerId, url)))
                        .then());
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