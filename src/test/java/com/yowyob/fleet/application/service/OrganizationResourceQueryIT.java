package com.yowyob.fleet.application.service;

import com.yowyob.fleet.infrastructure.mappers.FleetMapper;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DataR2dbcTest
@ActiveProfiles("local")
@Import({OrganizationService.class, OrganizationResourceQueryIT.MapperConfig.class})
@TestPropertySource(properties = {
        "spring.docker.compose.enabled=false",
        "spring.liquibase.enabled=false"
})
class OrganizationResourceQueryIT {

    private static final UUID ADMIN = UUID.fromString("96b87460-6179-483d-a6d5-9cbcacd9d06d");
    private static final UUID MANAGER = UUID.fromString("e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb");
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    @Autowired
    OrganizationService organizationService;

    @TestConfiguration
    static class MapperConfig {
        @Bean
        FleetMapper fleetMapper() {
            return Mappers.getMapper(FleetMapper.class);
        }
    }

    @Test
    void summary() {
        StepVerifier.create(organizationService.summary(ADMIN))
                .assertNext(s -> {
                    assertEquals(ADMIN, s.adminId());
                    assertEquals(2L, s.managerCount());
                    assertEquals(3L, s.fleetCount());
                    assertEquals(5L, s.vehicleCount());
                    assertEquals(3L, s.availableVehicleCount());
                    assertEquals(5L, s.driverCount());
                    assertEquals(3L, s.tripCount());
                    assertEquals(1L, s.incidentCount());
                    assertEquals(2L, s.zoneCount());
                })
                .expectComplete()
                .verify(TIMEOUT);
    }

    @Test
    void kpisAndProfile() {
        StepVerifier.create(organizationService.kpis(ADMIN))
                .assertNext(k -> {
                    assertNotNull(k.counts());
                    assertEquals(2L, k.completedTrips());
                    assertEquals(1L, k.openIncidents());
                })
                .expectComplete()
                .verify(TIMEOUT);

        StepVerifier.create(organizationService.profile(ADMIN))
                .assertNext(p -> {
                    assertEquals(ADMIN, p.adminId());
                    assertEquals("admin@fleetman.cm", p.email());
                })
                .expectComplete()
                .verify(TIMEOUT);
    }

    @Test
    void lists() {
        StepVerifier.create(organizationService.fleets(ADMIN).collectList())
                .assertNext(list -> assertEquals(3, list.size()))
                .expectComplete()
                .verify(TIMEOUT);

        StepVerifier.create(organizationService.vehicles(ADMIN).collectList())
                .assertNext(list -> assertEquals(5, list.size()))
                .expectComplete()
                .verify(TIMEOUT);

        StepVerifier.create(organizationService.recentVehicles(ADMIN, 3).collectList())
                .assertNext(list -> assertEquals(3, list.size()))
                .expectComplete()
                .verify(TIMEOUT);

        StepVerifier.create(organizationService.drivers(ADMIN).collectList())
                .assertNext(list -> assertEquals(5, list.size()))
                .expectComplete()
                .verify(TIMEOUT);

        StepVerifier.create(organizationService.managers(ADMIN).collectList())
                .assertNext(list -> assertEquals(2, list.size()))
                .expectComplete()
                .verify(TIMEOUT);

        StepVerifier.create(organizationService.people(ADMIN))
                .assertNext(p -> {
                    assertEquals(2, p.managers().size());
                    assertEquals(5, p.drivers().size());
                })
                .expectComplete()
                .verify(TIMEOUT);

        StepVerifier.create(organizationService.trips(ADMIN, null).collectList())
                .assertNext(list -> assertEquals(3, list.size()))
                .expectComplete()
                .verify(TIMEOUT);

        StepVerifier.create(organizationService.trips(ADMIN, MANAGER).collectList())
                .assertNext(list -> assertEquals(3, list.size()))
                .expectComplete()
                .verify(TIMEOUT);

        StepVerifier.create(organizationService.incidents(ADMIN).collectList())
                .assertNext(list -> assertEquals(1, list.size()))
                .expectComplete()
                .verify(TIMEOUT);

        StepVerifier.create(organizationService.zones(ADMIN, null).collectList())
                .assertNext(list -> assertEquals(2, list.size()))
                .expectComplete()
                .verify(TIMEOUT);

        StepVerifier.create(organizationService.zones(ADMIN, MANAGER).collectList())
                .assertNext(list -> assertEquals(2, list.size()))
                .expectComplete()
                .verify(TIMEOUT);

        StepVerifier.create(organizationService.history(ADMIN).collectList())
                .assertNext(list -> assertEquals(3, list.size()))
                .expectComplete()
                .verify(TIMEOUT);
    }

    @Test
    void resolveAdminAndManagerThenCallAllEndpoints() {
        var adminAuth = auth(ADMIN, "adminfleet", "admin@fleetman.cm", "FLEET_ADMIN");
        var managerAuth = auth(MANAGER, "manager.dupont", "manager1@fleetman.cm", "FLEET_MANAGER");

        StepVerifier.create(organizationService.resolveOrganizationAdminId(adminAuth, null))
                .expectNext(ADMIN)
                .verifyComplete();
        StepVerifier.create(organizationService.resolveOrganizationAdminId(managerAuth, null))
                .expectNext(ADMIN)
                .verifyComplete();

        StepVerifier.create(
                        organizationService.resolveOrganizationAdminId(managerAuth, null)
                                .flatMap(organizationService::summary)
                                .then(organizationService.resolveOrganizationAdminId(managerAuth, null)
                                        .flatMap(organizationService::kpis))
                                .then(organizationService.resolveOrganizationAdminId(managerAuth, null)
                                        .flatMap(organizationService::profile))
                                .then(organizationService.resolveOrganizationAdminId(managerAuth, null)
                                        .flatMap(organizationService::people))
                                .thenMany(organizationService.resolveOrganizationAdminId(managerAuth, null)
                                        .flatMapMany(organizationService::fleets))
                                .thenMany(organizationService.resolveOrganizationAdminId(managerAuth, null)
                                        .flatMapMany(organizationService::vehicles))
                                .thenMany(organizationService.resolveOrganizationAdminId(managerAuth, null)
                                        .flatMapMany(id -> organizationService.recentVehicles(id, 5)))
                                .thenMany(organizationService.resolveOrganizationAdminId(managerAuth, null)
                                        .flatMapMany(organizationService::drivers))
                                .thenMany(organizationService.resolveOrganizationAdminId(managerAuth, null)
                                        .flatMapMany(organizationService::managers))
                                .thenMany(organizationService.resolveOrganizationAdminId(managerAuth, null)
                                        .flatMapMany(id -> organizationService.trips(id, null)))
                                .thenMany(organizationService.resolveOrganizationAdminId(managerAuth, null)
                                        .flatMapMany(id -> organizationService.trips(id, MANAGER)))
                                .thenMany(organizationService.resolveOrganizationAdminId(managerAuth, null)
                                        .flatMapMany(organizationService::incidents))
                                .thenMany(organizationService.resolveOrganizationAdminId(managerAuth, null)
                                        .flatMapMany(id -> organizationService.zones(id, null)))
                                .thenMany(organizationService.resolveOrganizationAdminId(managerAuth, null)
                                        .flatMapMany(id -> organizationService.zones(id, MANAGER)))
                                .thenMany(organizationService.resolveOrganizationAdminId(managerAuth, null)
                                        .flatMapMany(organizationService::history))
                                .then()
                )
                .expectComplete()
                .verify(TIMEOUT);
    }

    private static UsernamePasswordAuthenticationToken auth(UUID id, String username, String email, String role) {
        var user = new com.yowyob.fleet.domain.ports.out.AuthPort.UserDetail(
                id, username, email, "+237600000000",
                "Test", "User", "FLEET_MANAGEMENT",
                List.of(role), List.of(),
                null, null, null, null, true, null
        );
        return new UsernamePasswordAuthenticationToken(
                user, "token", List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
