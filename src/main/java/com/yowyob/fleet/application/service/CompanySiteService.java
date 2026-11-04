package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.CompanyException;
import com.yowyob.fleet.domain.model.CompanyRules;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.OrganizationDtos;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.CompanySiteEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.CompanySiteR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/** Sites de l'entreprise (siège, dépôts, agences, ateliers) et rattachement des flottes. */
@Service
@RequiredArgsConstructor
public class CompanySiteService {

    private static final UUID NO_ID = new UUID(0, 0);

    private final CompanySiteR2dbcRepository sites;
    private final DatabaseClient db;

    public Flux<OrganizationDtos.CompanySite> list(UUID companyId) {
        return sites.findByCompany(companyId).flatMap(this::toDto, 1);
    }

    public Mono<OrganizationDtos.CompanySite> create(UUID companyId, OrganizationDtos.SiteRequest request) {
        CompanyRules.validateSite(request.name(), request.siteType(), request.city(), request.phone());
        CompanySiteEntity site = new CompanySiteEntity();
        site.setId(UUID.randomUUID());
        site.setCompanyId(companyId);
        site.setCreatedAt(Instant.now());
        site.setNew(true);
        return assertAvailable(companyId, request, NO_ID)
                .then(Mono.defer(() -> sites.save(apply(site, request))))
                .flatMap(this::toDto);
    }

    public Mono<OrganizationDtos.CompanySite> update(UUID companyId, UUID siteId, OrganizationDtos.SiteRequest request) {
        CompanyRules.validateSite(request.name(), request.siteType(), request.city(), request.phone());
        return owned(companyId, siteId)
                .flatMap(site -> assertAvailable(companyId, request, siteId)
                        .then(Mono.defer(() -> {
                            site.setNew(false);
                            return sites.save(apply(site, request));
                        })))
                .flatMap(this::toDto);
    }

    /** Un site qui accueille une flotte ne peut être ni désactivé ni supprimé. */
    public Mono<OrganizationDtos.CompanySite> setActive(UUID companyId, UUID siteId, boolean active) {
        return owned(companyId, siteId)
                .flatMap(site -> (active ? Mono.<Void>empty() : assertEmpty(siteId))
                        .then(Mono.defer(() -> {
                            site.setActive(active);
                            site.setUpdatedAt(Instant.now());
                            site.setNew(false);
                            return sites.save(site);
                        })))
                .flatMap(this::toDto);
    }

    public Mono<Void> delete(UUID companyId, UUID siteId) {
        return owned(companyId, siteId)
                .flatMap(site -> assertEmpty(siteId).then(sites.delete(site)));
    }

    public Mono<Void> attachFleet(UUID companyId, UUID fleetId, UUID siteId) {
        Mono<Void> siteCheck = siteId == null
                ? Mono.empty()
                : owned(companyId, siteId).flatMap(site -> site.isActive()
                        ? Mono.<Void>empty()
                        : Mono.<Void>error(CompanyException.siteInactive()));
        return siteCheck.then(Mono.defer(() -> {
            DatabaseClient.GenericExecuteSpec spec = db.sql("""
                    UPDATE fleet.fleets SET site_id = :site, updated_at = NOW()
                    WHERE id = :fleet AND company_id = :company AND deleted_at IS NULL
                    """)
                    .bind("fleet", fleetId)
                    .bind("company", companyId);
            spec = siteId == null ? spec.bindNull("site", UUID.class) : spec.bind("site", siteId);
            return spec.fetch().rowsUpdated()
                    .flatMap(n -> n == 0 ? Mono.<Void>error(CompanyException.fleetNotFound()) : Mono.<Void>empty());
        }));
    }

    private Mono<CompanySiteEntity> owned(UUID companyId, UUID siteId) {
        return sites.findByIdAndCompanyId(siteId, companyId)
                .switchIfEmpty(Mono.error(CompanyException.siteNotFound()));
    }

    private Mono<Void> assertEmpty(UUID siteId) {
        return sites.countFleets(siteId)
                .flatMap(n -> n > 0 ? Mono.<Void>error(CompanyException.siteInUse(n)) : Mono.<Void>empty());
    }

    private Mono<Void> assertAvailable(UUID companyId, OrganizationDtos.SiteRequest request, UUID excludeId) {
        String name = request.name().trim();
        return sites.nameTaken(companyId, name, excludeId)
                .flatMap(taken -> taken ? Mono.<Boolean>error(CompanyException.siteNameTaken(name)) : Mono.just(false))
                .then("HEAD_OFFICE".equals(request.siteType())
                        ? sites.headOfficeTaken(companyId, excludeId)
                                .flatMap(taken -> taken ? Mono.<Void>error(CompanyException.headOfficeExists()) : Mono.<Void>empty())
                        : Mono.empty());
    }

    private static CompanySiteEntity apply(CompanySiteEntity site, OrganizationDtos.SiteRequest request) {
        site.setName(request.name().trim());
        site.setSiteType(request.siteType());
        site.setAddress(CompanyRules.clean(request.address()));
        site.setCity(CompanyRules.clean(request.city()));
        site.setPhone(CompanyRules.clean(request.phone()));
        site.setUpdatedAt(Instant.now());
        return site;
    }

    private Mono<OrganizationDtos.CompanySite> toDto(CompanySiteEntity site) {
        return sites.countFleets(site.getId()).map(fleets -> new OrganizationDtos.CompanySite(
                site.getId(), site.getName(), site.getSiteType(), site.getAddress(), site.getCity(),
                site.getPhone(), site.isActive(), fleets));
    }
}
