package com.yowyob.fleet.application.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.fleet.domain.model.Vehicle;
import com.yowyob.fleet.domain.ports.in.ManageAdminUseCase;
import com.yowyob.fleet.domain.ports.in.ManageAssignmentUseCase;
import com.yowyob.fleet.domain.ports.in.ManageDriverUseCase;
import com.yowyob.fleet.domain.ports.in.ManageFleetUseCase;
import com.yowyob.fleet.domain.ports.in.ManageSubscriptionPlanUseCase;
import com.yowyob.fleet.domain.ports.in.ManageTripUseCase;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.AssignmentResponse;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.FleetResponse;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.sync.DeletedEntityRef;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.sync.SyncChange;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.sync.SyncChangesResponse;
import com.yowyob.fleet.infrastructure.mappers.FleetMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SyncPullService {

    private final ManageFleetUseCase fleetUseCase;
    private final FleetMapper fleetMapper;
    private final ManageDriverUseCase driverUseCase;
    private final ManageAssignmentUseCase assignmentUseCase;
    private final ManageTripUseCase tripUseCase;
    private final VehicleService vehicleService;
    private final ManageAdminUseCase adminUseCase;
    private final VehicleTypeService vehicleTypeService;
    private final VehicleResourceService vehicleResourceService;
    private final ManageSubscriptionPlanUseCase subscriptionPlanUseCase;
    private final SyncDeltaService syncDeltaService;
    private final ObjectMapper objectMapper;

    public Mono<SyncChangesResponse> pull(
            Authentication auth,
            String scope,
            String since,
            boolean full,
            String authorizationHeader
    ) {
        UUID userId = getUserId(auth);
        String token = stripBearer(authorizationHeader);
        Instant serverTime = Instant.now();
        String cursor = serverTime.toString();
        Instant sinceInstant = parseSince(since);
        boolean isFull = full || sinceInstant == null;

        if (!isFull && "manager".equals(scope)) {
            return syncDeltaService.pullManagerDelta(userId, sinceInstant)
                    .map(delta -> new SyncChangesResponse(
                            cursor,
                            serverTime,
                            false,
                            false,
                            delta.changes(),
                            delta.deleted()
                    ));
        }

        Flux<SyncChange> changes = switch (scope) {
            case "manager" -> pullManager(userId, token);
            case "admin" -> pullAdmin(token);
            case "super-admin" -> pullSuperAdmin(token);
            case "driver" -> pullDriver(userId, token);
            default -> Flux.error(new IllegalArgumentException("Scope sync inconnu : " + scope));
        };

        return changes.collectList()
                .map(list -> new SyncChangesResponse(
                        cursor,
                        serverTime,
                        isFull,
                        false,
                        list,
                        List.<DeletedEntityRef>of()
                ));
    }

    private Flux<SyncChange> pullManager(UUID managerId, String token) {
        Flux<SyncChange> fleets = fleetUseCase.getFleets(managerId, false)
                .map(fleetMapper::toResponse)
                .map(this::fleetChange);

        Flux<SyncChange> vehicles = vehicleService.getVehicles(managerId, false, token, null)
                .map(this::vehicleChange);

        Flux<SyncChange> drivers = driverUseCase.getDriversEnriched(null, null, managerId)
                .map(this::driverChange);

        return Flux.merge(fleets, vehicles, drivers);
    }

    private Flux<SyncChange> pullDriver(UUID driverUserId, String token) {
        Flux<SyncChange> profile = driverUseCase.getDriverEnriched(driverUserId)
                .map(this::driverChange)
                .flux();

        Flux<SyncChange> assignments = assignmentUseCase.getByDriver(driverUserId)
                .map(AssignmentResponse::from)
                .map(a -> change("assignment", a.id(), a));

        Flux<SyncChange> trips = tripUseCase.getMyTripHistory(driverUserId)
                .map(t -> change("trip", t.getId(), t));

        Flux<SyncChange> vehicles = driverUseCase.getDriverEnriched(driverUserId)
                .flatMapMany(driver -> {
                    if (driver.assignedVehicleId() == null) {
                        return Flux.empty();
                    }
                    return vehicleService.getVehicleDetails(driver.assignedVehicleId(), token)
                            .map(this::vehicleChange);
                });

        return Flux.merge(profile, assignments, trips, vehicles);
    }

    private Flux<SyncChange> pullAdmin(String token) {
        Flux<SyncChange> managers = adminUseCase.listFleetManagers(token)
                .map(this::fleetManagerChange);

        Flux<SyncChange> references = Flux.merge(
                referenceChanges("vehicle-types", vehicleTypeService.getAllTypes()),
                referenceChanges("manufacturers", vehicleResourceService.getAllMfr()),
                referenceChanges("brands", vehicleResourceService.getAllBrd()),
                referenceChanges("models", vehicleResourceService.getAllMod()),
                referenceChanges("sizes", vehicleResourceService.getAllSize()),
                referenceChanges("usages", vehicleResourceService.getAllUsage()),
                referenceChanges("fuels", vehicleResourceService.getAllFuel()),
                referenceChanges("transmissions", vehicleResourceService.getAllTrans()),
                referenceChanges("colors", vehicleResourceService.getAllColor())
        );

        return Flux.merge(managers, references);
    }

    private Flux<SyncChange> pullSuperAdmin(String token) {
        Flux<SyncChange> adminScope = pullAdmin(token);
        Flux<SyncChange> plans = subscriptionPlanUseCase.listPlans()
                .map(plan -> change("subscriptionPlan", plan.getId(), plan));
        Flux<SyncChange> pending = subscriptionPlanUseCase.listPendingSubscriptions()
                .map(dto -> change("subscriptionPending", dto.id(), dto));
        Flux<SyncChange> active = subscriptionPlanUseCase.listActiveSubscriptions()
                .map(dto -> change("subscriptionActive", dto.managerId(), dto));
        Flux<SyncChange> history = subscriptionPlanUseCase.listSubscriptionHistory()
                .map(dto -> change("subscriptionHistory", dto.id(), dto));

        return Flux.merge(adminScope, plans, pending, active, history);
    }

    private Flux<SyncChange> referenceChanges(String kind, Flux<?> source) {
        return source.map(item -> {
            Map<String, Object> payload = asMap(item);
            payload.put("referenceKind", kind);
            UUID id = extractId(payload);
            return new SyncChange("reference", id, Instant.now(), payload);
        });
    }

    private SyncChange fleetChange(FleetResponse fleet) {
        return change("fleet", fleet.id(), fleet);
    }

    private SyncChange vehicleChange(Vehicle vehicle) {
        return change("vehicle", vehicle.id(), vehicle);
    }

    private SyncChange driverChange(com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.DriverResponse driver) {
        return change("driver", driver.userId(), driver);
    }

    private SyncChange fleetManagerChange(AuthPort.UserDetail manager) {
        return change("fleetManager", manager.id(), manager);
    }

    private SyncChange change(String entityType, UUID entityId, Object payload) {
        return new SyncChange(entityType, entityId, Instant.now(), asMap(payload));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object payload) {
        if (payload == null) {
            return Map.of();
        }
        return objectMapper.convertValue(payload, new TypeReference<Map<String, Object>>() {});
    }

    private UUID extractId(Map<String, Object> payload) {
        Object raw = payload.get("id");
        if (raw == null) {
            raw = payload.get("userId");
        }
        if (raw == null) {
            raw = payload.get("managerId");
        }
        if (raw instanceof UUID uuid) {
            return uuid;
        }
        if (raw instanceof String str) {
            return UUID.fromString(str);
        }
        throw new IllegalArgumentException("Impossible d'extraire l'identifiant de sync");
    }

    private UUID getUserId(Authentication auth) {
        return ((AuthPort.UserDetail) auth.getPrincipal()).id();
    }

    private String stripBearer(String header) {
        if (header == null) {
            return "";
        }
        return header.startsWith("Bearer ") ? header.substring(7) : header;
    }

    private Instant parseSince(String since) {
        if (since == null || since.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(since);
        } catch (Exception e) {
            throw new IllegalArgumentException("Paramètre since invalide (ISO-8601 attendu) : " + since);
        }
    }
}
