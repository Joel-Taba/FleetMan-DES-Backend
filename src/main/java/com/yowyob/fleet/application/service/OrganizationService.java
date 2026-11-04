package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.AdminException;
import com.yowyob.fleet.domain.model.CompanyRules;
import com.yowyob.fleet.domain.model.Driver;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.DriverResponse;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.FleetResponse;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.OrganizationDtos;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.DriverEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.FleetManagerEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.IncidentEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.TripEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.UserLocalEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.VehicleLocalEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.VehicleTypeEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.DriverR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.OperationalParameterR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.VehicleTypeR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.FleetManagerR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.FleetR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.FuelRechargeR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.GeofenceR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.IncidentR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.MaintenanceR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.PlatformAdminR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.TripR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.UserLocalR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.VehicleLocalR2dbcRepository;
import com.yowyob.fleet.infrastructure.config.security.AuthPrincipal;
import com.yowyob.fleet.infrastructure.mappers.FleetMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrganizationService {

    private static final int MAX_LOGO_BYTES = 2 * 1024 * 1024;

    private final PlatformAdminR2dbcRepository platformAdminRepo;
    private final FleetManagerR2dbcRepository managerRepo;
    private final com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.CompanyR2dbcRepository companyRepo;
    private final UserLocalR2dbcRepository userRepo;
    private final FleetR2dbcRepository fleetRepo;
    private final VehicleLocalR2dbcRepository vehicleRepo;
    private final VehicleTypeR2dbcRepository vehicleTypeRepo;
    private final OperationalParameterR2dbcRepository operationalParameterRepo;
    private final DriverR2dbcRepository driverRepo;
    private final TripR2dbcRepository tripRepo;
    private final FuelRechargeR2dbcRepository fuelRechargeRepo;
    private final MaintenanceR2dbcRepository maintenanceRepo;
    private final IncidentR2dbcRepository incidentRepo;
    private final GeofenceR2dbcRepository zoneRepo;
    private final FleetMapper fleetMapper;
    private final LocalFileStorageService localFileStorageService;
    private final com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.CompanySiteR2dbcRepository siteRepo;
    private final org.springframework.r2dbc.core.DatabaseClient db;

    public Mono<UUID> resolveOrganizationAdminId(Authentication auth, UUID requestedAdminId) {
        UUID principalId = AuthPrincipal.userId(auth);
        boolean superAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_FLEET_SUPER_ADMIN".equals(a.getAuthority()));
        boolean admin = AuthPrincipal.isFleetAdmin(auth);

        return resolveLocalUserId(principalId).flatMap(localId -> {
            if (requestedAdminId != null) {
                return resolveLocalUserId(requestedAdminId)
                        .flatMap(this::requirePlatformAdmin)
                        .flatMap(target -> authorizeOrgAccess(localId, target, admin, superAdmin));
            }
            if (admin || superAdmin) {
                return platformAdminRepo.existsById(localId)
                        .flatMap(exists -> exists
                                ? Mono.just(localId)
                                : managerOrgAdmin(localId));
            }
            return managerOrgAdmin(localId);
        });
    }

    /**
     * Admin : conserve le filtre manager optionnel (null = toute l'org).
     * Manager : toujours son propre identifiant — périmètre = flottes affectées.
     */
    public Mono<UUID> scopedManagerId(Authentication auth, UUID requestedManagerId) {
        if (AuthPrincipal.isFleetAdmin(auth)) {
            return Mono.justOrEmpty(requestedManagerId);
        }
        if (AuthPrincipal.isFleetManager(auth)) {
            return resolveLocalUserId(AuthPrincipal.userId(auth));
        }
        return Mono.justOrEmpty(requestedManagerId);
    }

    public Mono<OrganizationDtos.OrganizationSummary> summary(UUID adminId) {
        return summary(adminId, null);
    }

    public Mono<OrganizationDtos.OrganizationSummary> summary(UUID adminId, UUID managerId) {
        if (managerId == null) {
            return Mono.zip(
                    managerRepo.countByCreatedByAdminId(adminId).defaultIfEmpty(0L),
                    fleetRepo.countByOrganizationAdminId(adminId).defaultIfEmpty(0L),
                    vehicleRepo.countByOrganizationAdminId(adminId).defaultIfEmpty(0L),
                    vehicleRepo.countByOrganizationAdminIdAndStatus(adminId, "AVAILABLE").defaultIfEmpty(0L),
                    driverRepo.countByOrganizationAdminId(adminId).defaultIfEmpty(0L),
                    tripRepo.countByOrganizationAdminId(adminId).defaultIfEmpty(0L),
                    incidentRepo.countByOrganizationAdminId(adminId).defaultIfEmpty(0L),
                    zoneRepo.countByOrganizationAdminId(adminId).defaultIfEmpty(0L)
            ).map(t -> new OrganizationDtos.OrganizationSummary(
                    adminId, t.getT1(), t.getT2(), t.getT3(), t.getT4(), t.getT5(), t.getT6(), t.getT7(), t.getT8()
            ));
        }
        return Mono.zip(
                Mono.just(1L),
                fleetRepo.countActiveByManagerId(managerId).defaultIfEmpty(0L),
                vehicleRepo.countByManagerId(managerId).defaultIfEmpty(0L),
                vehicleRepo.countByManagerIdAndStatus(managerId, "AVAILABLE").defaultIfEmpty(0L),
                driverRepo.countByManagerId(managerId).defaultIfEmpty(0L),
                tripRepo.countByFleetManagerId(managerId).defaultIfEmpty(0L),
                incidentRepo.findAllByManagerId(managerId).count().defaultIfEmpty(0L),
                zoneRepo.findAllByOrganizationAdminIdAndManagerId(adminId, managerId).count().defaultIfEmpty(0L)
        ).map(t -> new OrganizationDtos.OrganizationSummary(
                adminId, t.getT1(), t.getT2(), t.getT3(), t.getT4(), t.getT5(), t.getT6(), t.getT7(), t.getT8()
        ));
    }

    public Mono<OrganizationDtos.OrganizationProfile> profile(UUID adminId) {
        return userRepo.findById(adminId)
                .switchIfEmpty(Mono.error(AdminException.createdByMustBeAdmin()))
                .flatMap(admin -> Mono.zip(
                        summary(adminId),
                        companyRepo.findById(adminId)
                                .defaultIfEmpty(new com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.CompanyEntity()),
                        siteRepo.findByCompany(adminId).collectList()
                ).map(t -> toProfile(adminId, admin, t.getT2(), t.getT1(), t.getT3())));
    }

    /** Fiche légale de l'entreprise ; le nom recopié sur les gestionnaires suit. */
    public Mono<OrganizationDtos.OrganizationProfile> updateLegalProfile(UUID adminId, OrganizationDtos.UpdateProfileRequest r) {
        CompanyRules.Profile p = CompanyRules.normalize(new CompanyRules.Profile(
                r.organizationName(), r.legalName(), r.legalForm(), r.registrationNumber(), r.taxId(), r.sector(),
                r.address(), r.city(), r.country(), r.companyPhone(), r.companyEmail(), r.website()));
        return companyRepo.findById(adminId)
                .switchIfEmpty(Mono.error(AdminException.createdByMustBeAdmin()))
                .flatMap(company -> {
                    company.setName(p.name());
                    company.setLegalName(p.legalName());
                    company.setLegalForm(p.legalForm());
                    company.setRegistrationNumber(p.registrationNumber());
                    company.setTaxId(p.taxId());
                    company.setSector(p.sector());
                    company.setAddress(p.address());
                    company.setCity(p.city());
                    company.setCountry(p.country());
                    company.setContactPhone(p.phone());
                    company.setContactEmail(p.email());
                    company.setWebsite(p.website());
                    company.setUpdatedAt(Instant.now());
                    company.setNew(false);
                    return companyRepo.save(company);
                })
                .then(db.sql("UPDATE fleet.fleet_managers SET company_name = LEFT(:name, 100) WHERE created_by_admin_id = :id")
                        .bind("name", p.name()).bind("id", adminId)
                        .fetch().rowsUpdated())
                .then(profile(adminId));
    }

    /** Organigramme de l'entreprise, limité aux flottes du gestionnaire si {@code managerId} est donné. */
    public Mono<OrganizationDtos.OrganizationStructure> structure(UUID adminId, UUID managerId) {
        Mono<java.util.List<OrganizationDtos.StructureFleet>> fleets = db.sql("""
                SELECT f.id, f.name, f.site_id, f.manager_id,
                       TRIM(COALESCE(u.first_name, '') || ' ' || COALESCE(u.last_name, '')) AS manager_name,
                       fm.account_status,
                       (SELECT COUNT(*) FROM fleet.vehicles v
                        WHERE v.fleet_id = f.id AND v.deleted_at IS NULL AND v.status <> 'RETIRED') AS vehicles,
                       (SELECT COUNT(*) FROM fleet.drivers d
                        WHERE d.fleet_id = f.id AND d.deleted_at IS NULL AND d.status <> 'LEFT') AS drivers
                FROM fleet.fleets f
                LEFT JOIN fleet.users u ON u.id = f.manager_id
                LEFT JOIN fleet.fleet_managers fm ON fm.user_id = f.manager_id
                WHERE f.company_id = :company AND f.deleted_at IS NULL
                ORDER BY LOWER(f.name)
                """)
                .bind("company", adminId)
                .map((row, meta) -> new OrganizationDtos.StructureFleet(
                        row.get("id", UUID.class), row.get("name", String.class), row.get("site_id", UUID.class),
                        row.get("manager_id", UUID.class), blankToNull(row.get("manager_name", String.class)),
                        row.get("account_status", String.class),
                        row.get("vehicles", Long.class), row.get("drivers", Long.class)))
                .all()
                .filter(f -> managerId == null || managerId.equals(f.managerId()))
                .collectList();
        Mono<java.util.List<OrganizationDtos.StructureManager>> managers = managerId != null
                ? Mono.just(java.util.List.of())
                : db.sql("""
                SELECT fm.user_id, TRIM(COALESCE(u.first_name, '') || ' ' || COALESCE(u.last_name, '')) AS full_name,
                       u.email, u.phone, fm.account_status,
                       (SELECT COUNT(*) FROM fleet.fleets f WHERE f.manager_id = fm.user_id AND f.deleted_at IS NULL) AS fleets
                FROM fleet.fleet_managers fm
                JOIN fleet.users u ON u.id = fm.user_id AND u.deleted_at IS NULL
                WHERE fm.created_by_admin_id = :company AND fm.account_status <> 'CLOSED'
                ORDER BY LOWER(u.last_name), LOWER(u.first_name)
                """)
                .bind("company", adminId)
                .map((row, meta) -> new OrganizationDtos.StructureManager(
                        row.get("user_id", UUID.class), row.get("full_name", String.class),
                        row.get("email", String.class), row.get("phone", String.class),
                        row.get("account_status", String.class), row.get("fleets", Long.class)))
                .all()
                .collectList();
        Mono<java.util.List<OrganizationDtos.CompanySite>> sites = siteRepo.findByCompany(adminId)
                .flatMap(s -> siteRepo.countFleets(s.getId()).map(n -> new OrganizationDtos.CompanySite(
                        s.getId(), s.getName(), s.getSiteType(), s.getAddress(), s.getCity(), s.getPhone(), s.isActive(), n)), 1)
                .collectList();
        return Mono.zip(sites, fleets, managers)
                .map(t -> new OrganizationDtos.OrganizationStructure(t.getT1(), t.getT2(), t.getT3()));
    }

    /** Nom et logo de l'entreprise (ils étaient recopiés sur chacun de ses gestionnaires). */
    public Mono<OrganizationDtos.OrganizationProfile> updateProfile(UUID adminId, String organizationName, String logoUrl) {
        return companyRepo.findById(adminId)
                .switchIfEmpty(Mono.error(AdminException.createdByMustBeAdmin()))
                .flatMap(company -> {
                    if (organizationName != null && !organizationName.isBlank()) {
                        company.setName(organizationName.trim());
                    }
                    if (logoUrl != null && !logoUrl.isBlank()) {
                        company.setLogoUrl(logoUrl);
                    }
                    company.setUpdatedAt(java.time.Instant.now());
                    company.setNew(false);
                    return companyRepo.save(company);
                })
                .then(profile(adminId));
    }

    public Mono<OrganizationDtos.OrganizationProfile> updateLogo(UUID adminId, byte[] data, String filename, String contentType) {
        if (contentType == null || !contentType.startsWith("image/")) {
            return Mono.error(com.yowyob.fleet.domain.exception.CompanyException.invalidField("Le logo doit être une image."));
        }
        if (data.length > MAX_LOGO_BYTES) {
            return Mono.error(com.yowyob.fleet.domain.exception.CompanyException.invalidField("Le logo ne doit pas dépasser 2 Mo."));
        }
        String safeName = (filename == null || filename.isBlank()) ? "logo.jpg" : filename;
        return localFileStorageService.storeBytes(data, safeName, contentType, "org-logo")
                .flatMap(stored -> updateProfile(adminId, null, stored.publicUrl()));
    }

    private static OrganizationDtos.OrganizationProfile toProfile(
            UUID adminId,
            UserLocalEntity admin,
            com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.CompanyEntity org,
            OrganizationDtos.OrganizationSummary summary,
            java.util.List<com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.CompanySiteEntity> sites
    ) {
        String name = org.getName();
        if (name == null || name.isBlank()) {
            String fallback = ((admin.getFirstName() != null ? admin.getFirstName() : "")
                    + " " + (admin.getLastName() != null ? admin.getLastName() : "")).trim();
            name = fallback.isBlank() ? "Organisation" : fallback;
        }
        String logo = org.getLogoUrl();
        if (logo == null || logo.isBlank() || logo.startsWith("/uploads/")) {
            if (admin.getPhotoUrl() != null && !admin.getPhotoUrl().isBlank()) {
                logo = admin.getPhotoUrl();
            }
        }
        boolean hasHeadOffice = sites.stream().anyMatch(s -> "HEAD_OFFICE".equals(s.getSiteType()));
        java.util.List<String> missing = CompanyRules.missingFields(new CompanyRules.Profile(
                name, org.getLegalName(), org.getLegalForm(), org.getRegistrationNumber(), org.getTaxId(), org.getSector(),
                org.getAddress(), org.getCity(), org.getCountry(), org.getContactPhone(), org.getContactEmail(), org.getWebsite()),
                org.getLogoUrl() != null && !org.getLogoUrl().isBlank(), hasHeadOffice);
        return new OrganizationDtos.OrganizationProfile(
                adminId,
                name,
                logo,
                admin.getFirstName(),
                admin.getLastName(),
                admin.getEmail(),
                admin.getPhone(),
                admin.getPhotoUrl(),
                summary.managerCount(),
                summary.fleetCount(),
                summary.vehicleCount(),
                summary.driverCount(),
                org.getLegalName(),
                org.getLegalForm(),
                org.getRegistrationNumber(),
                org.getTaxId(),
                org.getSector(),
                org.getAddress(),
                org.getCity(),
                org.getCountry(),
                org.getContactPhone(),
                org.getContactEmail(),
                org.getWebsite(),
                org.getCreatedAt(),
                sites.size(),
                CompanyRules.completeness(missing),
                missing
        );
    }

    public Mono<OrganizationDtos.OrganizationKpis> kpis(UUID adminId) {
        return kpis(adminId, null);
    }

    public Mono<OrganizationDtos.OrganizationKpis> kpis(UUID adminId, UUID managerId) {
        if (managerId != null) {
            return Mono.zip(
                    summary(adminId, managerId),
                    tripRepo.findHistoryByOrganizationAdminIdAndManagerId(adminId, managerId)
                            .filter(t -> "COMPLETED".equals(t.getStatus()))
                            .count(),
                    incidentRepo.findAllByManagerId(managerId)
                            .filter(i -> "REPORTED".equals(i.getStatus()) || "UNDER_INVESTIGATION".equals(i.getStatus()))
                            .count(),
                    Mono.zip(
                            fuelRechargeRepo.findAllByManagerId(managerId)
                                    .map(fr -> fr.getPrice() != null ? fr.getPrice() : BigDecimal.ZERO)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add),
                            fuelRechargeRepo.findAllByManagerId(managerId)
                                    .map(fr -> fr.getQuantity() != null ? fr.getQuantity() : BigDecimal.ZERO)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add),
                            fuelRechargeRepo.findAllByManagerId(managerId).count(),
                            maintenanceRepo.findAllByManagerId(managerId)
                                    .map(m -> m.getCost() != null ? m.getCost() : BigDecimal.ZERO)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add),
                            maintenanceRepo.findAllByManagerId(managerId).count(),
                            tripRepo.findAllByFleetManagerId(managerId)
                                    .map(t -> {
                                        if (t.getComputedDistanceKm() != null) {
                                            return t.getComputedDistanceKm();
                                        }
                                        if (t.getDistanceKm() != null) {
                                            return BigDecimal.valueOf(t.getDistanceKm());
                                        }
                                        return BigDecimal.ZERO;
                                    })
                                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    ),
                    Mono.zip(
                            vehicleRepo.countByManagerIdAndStatus(managerId, "AVAILABLE").defaultIfEmpty(0L),
                            vehicleRepo.countByManagerIdAndStatus(managerId, "ON_TRIP").defaultIfEmpty(0L),
                            vehicleRepo.countByManagerIdAndStatus(managerId, "MAINTENANCE").defaultIfEmpty(0L)
                    )
            ).map(t -> {
                var ops = t.getT4();
                var fleet = t.getT5();
                return new OrganizationDtos.OrganizationKpis(
                        t.getT1(), t.getT2(), t.getT3(),
                        ops.getT1(), ops.getT2(), ops.getT3(), ops.getT4(), ops.getT5(), ops.getT6(),
                        fleet.getT1(), fleet.getT2(), fleet.getT3()
                );
            });
        }
        return Mono.zip(
                summary(adminId),
                tripRepo.findHistoryByOrganizationAdminId(adminId)
                        .filter(t -> "COMPLETED".equals(t.getStatus()))
                        .count(),
                incidentRepo.findAllByOrganizationAdminId(adminId)
                        .filter(i -> "REPORTED".equals(i.getStatus()) || "UNDER_INVESTIGATION".equals(i.getStatus()))
                        .count(),
                Mono.zip(
                        fuelRechargeRepo.sumCostByOrganizationAdminId(adminId).defaultIfEmpty(BigDecimal.ZERO),
                        fuelRechargeRepo.sumQuantityByOrganizationAdminId(adminId).defaultIfEmpty(BigDecimal.ZERO),
                        fuelRechargeRepo.countByOrganizationAdminId(adminId).defaultIfEmpty(0L),
                        maintenanceRepo.sumCostByOrganizationAdminId(adminId).defaultIfEmpty(BigDecimal.ZERO),
                        maintenanceRepo.countByOrganizationAdminId(adminId).defaultIfEmpty(0L),
                        tripRepo.sumDistanceByOrganizationAdminId(adminId).defaultIfEmpty(BigDecimal.ZERO)
                ),
                Mono.zip(
                        vehicleRepo.countByOrganizationAdminIdAndStatus(adminId, "AVAILABLE").defaultIfEmpty(0L),
                        vehicleRepo.countByOrganizationAdminIdAndStatus(adminId, "ON_TRIP").defaultIfEmpty(0L),
                        vehicleRepo.countByOrganizationAdminIdAndStatus(adminId, "MAINTENANCE").defaultIfEmpty(0L)
                )
        ).map(t -> {
            var ops = t.getT4();
            var fleet = t.getT5();
            return new OrganizationDtos.OrganizationKpis(
                    t.getT1(),
                    t.getT2(),
                    t.getT3(),
                    ops.getT1(),
                    ops.getT2(),
                    ops.getT3(),
                    ops.getT4(),
                    ops.getT5(),
                    ops.getT6(),
                    fleet.getT1(),
                    fleet.getT2(),
                    fleet.getT3()
            );
        });
    }

    public Flux<OrganizationDtos.OrganizationFuelRecharge> fuelRecharges(UUID adminId) {
        return fuelRecharges(adminId, null);
    }

    public Flux<OrganizationDtos.OrganizationFuelRecharge> fuelRecharges(UUID adminId, UUID managerId) {
        return (managerId != null
                ? fuelRechargeRepo.findAllByManagerId(managerId)
                : fuelRechargeRepo.findAllByOrganizationAdminId(adminId))
                .map(this::toFuelRecharge);
    }

    public Flux<OrganizationDtos.OrganizationMaintenance> maintenances(UUID adminId) {
        return maintenances(adminId, null);
    }

    public Flux<OrganizationDtos.OrganizationMaintenance> maintenances(UUID adminId, UUID managerId) {
        return (managerId != null
                ? maintenanceRepo.findAllByManagerId(managerId)
                : maintenanceRepo.findAllByOrganizationAdminId(adminId))
                .map(this::toMaintenance);
    }

    public Flux<FleetResponse> fleets(UUID adminId) {
        return fleets(adminId, null);
    }

    public Flux<FleetResponse> fleets(UUID adminId, UUID managerId) {
        Flux<com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.FleetEntity> source = managerId != null
                ? fleetRepo.findAllByManagerId(managerId).filter(f -> f.getDeletedAt() == null)
                : fleetRepo.findAllByOrganizationAdminId(adminId);
        return source.map(fleetMapper::toDomain).map(fleetMapper::toResponse);
    }

    public Flux<OrganizationDtos.OrganizationVehicle> vehicles(UUID adminId) {
        return vehicles(adminId, null);
    }

    public Flux<OrganizationDtos.OrganizationVehicle> vehicles(UUID adminId, UUID managerId) {
        return vehicleTypeLabels().flatMapMany(labels ->
                (managerId != null
                        ? vehicleRepo.findAllInAssignedFleets(managerId)
                        : vehicleRepo.findAllByOrganizationAdminId(adminId))
                        .flatMap(v -> toVehicle(v, labels)));
    }

    public Flux<OrganizationDtos.OrganizationVehicle> recentVehicles(UUID adminId, int limit) {
        int safe = Math.min(Math.max(limit, 1), 50);
        return vehicleTypeLabels().flatMapMany(labels ->
                vehicleRepo.findRecentByOrganizationAdminId(adminId, safe)
                        .flatMap(v -> toVehicle(v, labels)));
    }

    public Flux<DriverResponse> drivers(UUID adminId) {
        return drivers(adminId, null);
    }

    public Flux<DriverResponse> drivers(UUID adminId, UUID managerId) {
        return (managerId != null
                ? driverRepo.findAllByManagerId(managerId)
                : driverRepo.findAllByOrganizationAdminId(adminId))
                .flatMap(this::toDriverResponse);
    }

    public Flux<OrganizationDtos.AuthManager> managers(UUID adminId) {
        return managerRepo.findByCreatedByAdminId(adminId)
                .flatMap(mgr -> userRepo.findById(mgr.getUserId())
                        .filter(u -> u.getDeletedAt() == null)
                        .map(u -> toManager(u, mgr)));
    }

    public Mono<OrganizationDtos.OrganizationPeople> people(UUID adminId) {
        return people(adminId, null);
    }

    public Mono<OrganizationDtos.OrganizationPeople> people(UUID adminId, UUID managerId) {
        return Mono.zip(managers(adminId).collectList(), drivers(adminId, managerId).collectList())
                .map(t -> new OrganizationDtos.OrganizationPeople(t.getT1(), t.getT2()));
    }

    public Flux<OrganizationDtos.OrganizationTrip> trips(UUID adminId, UUID managerId) {
        Flux<TripEntity> source = managerId != null
                ? tripRepo.findAllByOrganizationAdminIdAndManagerId(adminId, managerId)
                : tripRepo.findAllByOrganizationAdminId(adminId);
        return source.flatMap(this::toTrip);
    }

    public Flux<OrganizationDtos.OrganizationIncident> incidents(UUID adminId) {
        return incidents(adminId, null);
    }

    public Flux<OrganizationDtos.OrganizationIncident> incidents(UUID adminId, UUID managerId) {
        return (managerId != null
                ? incidentRepo.findAllByManagerId(managerId)
                : incidentRepo.findAllByOrganizationAdminId(adminId))
                .map(this::toIncident);
    }

    public Flux<OrganizationDtos.OrganizationZone> zones(UUID adminId, UUID managerId) {
        return (managerId != null
                ? zoneRepo.findAllByOrganizationAdminIdAndManagerId(adminId, managerId)
                : zoneRepo.findAllByOrganizationAdminId(adminId))
                .map(z -> new OrganizationDtos.OrganizationZone(z.getId(), z.getManagerId(), z.getFleetId(), z.getZoneType()));
    }

    public Flux<OrganizationDtos.OrganizationHistoryItem> history(UUID adminId) {
        return history(adminId, null);
    }

    public Flux<OrganizationDtos.OrganizationHistoryItem> history(UUID adminId, UUID managerId) {
        Flux<TripEntity> tripSource = managerId != null
                ? tripRepo.findHistoryByOrganizationAdminIdAndManagerId(adminId, managerId)
                : tripRepo.findHistoryByOrganizationAdminId(adminId);
        Flux<OrganizationDtos.OrganizationHistoryItem> trips = tripSource
                .map(t -> new OrganizationDtos.OrganizationHistoryItem(
                        "TRIP",
                        t.getId(),
                        t.getTripCode() != null ? t.getTripCode() : "Trajet",
                        t.getStatus(),
                        tripInstant(t)
                ));
        Flux<OrganizationDtos.OrganizationHistoryItem> incidents = (managerId != null
                ? incidentRepo.findAllByManagerId(managerId)
                : incidentRepo.findAllByOrganizationAdminId(adminId))
                .map(i -> new OrganizationDtos.OrganizationHistoryItem(
                        "INCIDENT",
                        i.getId(),
                        i.getType() != null ? i.getType() : "Incident",
                        i.getStatus(),
                        i.getIncidentDateTime() != null
                                ? i.getIncidentDateTime().toInstant(ZoneOffset.UTC)
                                : null
                ));
        return Flux.merge(trips, incidents)
                .sort(Comparator.comparing(
                        OrganizationDtos.OrganizationHistoryItem::occurredAt,
                        Comparator.nullsLast(Comparator.reverseOrder())));
    }

    private Mono<UUID> authorizeOrgAccess(UUID localId, UUID targetAdminId, boolean admin, boolean superAdmin) {
        if (superAdmin) {
            return Mono.just(targetAdminId);
        }
        if (admin && localId.equals(targetAdminId)) {
            return Mono.just(targetAdminId);
        }
        return managerOrgAdmin(localId)
                .filter(targetAdminId::equals)
                .switchIfEmpty(Mono.error(AdminException.organizationAccessDenied()));
    }

    private Mono<UUID> managerOrgAdmin(UUID managerLocalId) {
        return managerRepo.findById(managerLocalId)
                .flatMap(mgr -> mgr.getCreatedByAdminId() != null
                        ? Mono.just(mgr.getCreatedByAdminId())
                        : Mono.error(AdminException.organizationAccessDenied()))
                .switchIfEmpty(Mono.error(AdminException.organizationAccessDenied()));
    }

    private Mono<UUID> requirePlatformAdmin(UUID adminId) {
        return platformAdminRepo.existsById(adminId)
                .flatMap(exists -> exists ? Mono.just(adminId) : Mono.error(AdminException.createdByMustBeAdmin()));
    }

    private Mono<UUID> resolveLocalUserId(UUID id) {
        if (id == null) {
            return Mono.empty();
        }
        return userRepo.findById(id)
                .switchIfEmpty(Mono.defer(() -> userRepo.findByKernelId(id)))
                .map(UserLocalEntity::getId)
                .defaultIfEmpty(id);
    }

    private Mono<java.util.Map<UUID, String>> vehicleTypeLabels() {
        return vehicleTypeRepo.findAll()
                .collectMap(VehicleTypeEntity::getId, t -> t.getLabel() != null ? t.getLabel() : t.getCode());
    }

    private Mono<OrganizationDtos.OrganizationVehicle> toVehicle(
            VehicleLocalEntity v,
            java.util.Map<UUID, String> typeLabels
    ) {
        String typeLabel = v.getVehicleTypeId() != null ? typeLabels.get(v.getVehicleTypeId()) : null;
        return operationalParameterRepo.findByVehicleId(v.getId())
                .map(op -> op.getFuelLevel())
                .defaultIfEmpty("")
                .map(fuelLevel -> new OrganizationDtos.OrganizationVehicle(
                        v.getId(),
                        v.getFleetId(),
                        v.getManagerId(),
                        v.getCurrentDriverId(),
                        v.getVehicleTypeId(),
                        typeLabel,
                        v.getLicensePlate(),
                        v.getBrand(),
                        v.getModel(),
                        v.getManufacturingYear(),
                        v.getColor(),
                        v.getStatus(),
                        v.getPhotoUrl(),
                        v.getGeofenceRemoteId(),
                        fuelLevel == null || fuelLevel.isBlank() ? null : fuelLevel,
                        v.getCreatedAt(),
                        v.getUpdatedAt()
                ));
    }

    private Mono<DriverResponse> toDriverResponse(DriverEntity d) {
        Driver domain = new Driver(
                d.getUserId(), d.getFleetId(), d.getLicenceNumber(), d.getStatus(),
                d.getAssignedVehicleId(), d.getPhotoUrl(), d.getKernelActorId()
        );
        return userRepo.findById(d.getUserId())
                .map(u -> DriverResponse.from(domain, u, u.getPhone(), false))
                .defaultIfEmpty(DriverResponse.from(domain, null, null, false));
    }

    private OrganizationDtos.AuthManager toManager(UserLocalEntity u, FleetManagerEntity mgr) {
        return new OrganizationDtos.AuthManager(
                u.getId(), u.getUsername(), u.getEmail(), u.getPhone(),
                u.getFirstName(), u.getLastName(), mgr.getCompanyName(),
                u.isActive(), mgr.getCreatedByAdminId()
        );
    }

    private Mono<OrganizationDtos.OrganizationTrip> toTrip(TripEntity t) {
        Mono<String> plateMono = t.getVehicleId() == null
                ? Mono.just("")
                : vehicleRepo.findById(t.getVehicleId())
                        .map(v -> v.getLicensePlate() != null ? v.getLicensePlate() : "")
                        .defaultIfEmpty("");
        Mono<String> driverMono = t.getDriverId() == null
                ? Mono.just("")
                : userRepo.findById(t.getDriverId())
                        .map(u -> {
                            String first = u.getFirstName() != null ? u.getFirstName() : "";
                            String last = u.getLastName() != null ? u.getLastName() : "";
                            return (first + " " + last).trim();
                        })
                        .defaultIfEmpty("");
        return Mono.zip(plateMono, driverMono).map(tuple -> new OrganizationDtos.OrganizationTrip(
                t.getId(),
                t.getTripCode(),
                t.getFleetId(),
                t.getVehicleId(),
                t.getDriverId(),
                t.getCreatedBy(),
                t.getStatus(),
                t.getStartDate(),
                t.getStartTime(),
                t.getEndDate(),
                t.getEndTime(),
                t.getDepartureLocation(),
                t.getReturnLocation(),
                t.getMissionObject(),
                t.getDistanceKm(),
                t.getComputedDistanceKm(),
                blankToNull(tuple.getT1()),
                blankToNull(tuple.getT2()),
                t.getCancelReason()
        ));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private OrganizationDtos.OrganizationFuelRecharge toFuelRecharge(
            com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.FuelRechargeEntity f
    ) {
        return new OrganizationDtos.OrganizationFuelRecharge(
                f.getId(), f.getQuantity(), f.getPrice(), f.getRechargeDateTime(),
                f.getStationName(), f.getVehicleId(), f.getVehicleRegistration(),
                f.getDriverId(), f.getDriverFullName()
        );
    }

    private OrganizationDtos.OrganizationMaintenance toMaintenance(
            com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.MaintenanceEntity m
    ) {
        return new OrganizationDtos.OrganizationMaintenance(
                m.getId(), m.getSubject(), m.getCost(), m.getDateTime(),
                m.getLocationName(), m.getVehicleId(), m.getVehicleRegistration(),
                m.getDriverId(), m.getDriverFullName()
        );
    }

    private OrganizationDtos.OrganizationIncident toIncident(IncidentEntity i) {
        return new OrganizationDtos.OrganizationIncident(
                i.getId(), i.getType(), i.getSeverity(), i.getStatus(), i.getDescription(),
                i.getIncidentDateTime(), i.getCost(), i.getVehicleId(), i.getDriverId(),
                i.getDriverFullName(), i.getVehicleRegistration(),
                i.getLongitude(), i.getLatitude(), i.getReportedBy()
        );
    }

    private Instant tripInstant(TripEntity t) {
        if (t.getStartDate() == null) {
            return t.getCancelledAt();
        }
        return t.getStartDate().atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
