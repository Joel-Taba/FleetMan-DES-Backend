package com.yowyob.fleet.infrastructure.adapters.outbound.external;

import com.yowyob.fleet.domain.model.Vehicle;
import com.yowyob.fleet.domain.ports.out.ExternalVehiclePort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.VehicleRequest;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.client.KernelOrganizationApiClient;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.client.KernelResourceApiClient;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.DriverR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.UserLocalR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.VehicleLocalR2dbcRepository;
import com.yowyob.fleet.infrastructure.config.KernelCallSupport;
import com.yowyob.fleet.infrastructure.config.KernelTokenHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class KernelResourceAdapter implements ExternalVehiclePort {

    private static final String VEHICLE_CATEGORY = "VEHICLE";
    private static final String DEFAULT_AGENCY_CODE = "FLEET-DEPOT";

    private final KernelResourceApiClient resourceClient;
    private final KernelOrganizationApiClient organizationClient;
    private final VehicleLocalR2dbcRepository vehicleRepository;
    private final DriverR2dbcRepository driverRepository;
    private final UserLocalR2dbcRepository userRepository;
    private final KernelTokenHolder kernelTokenHolder;
    private final KernelCallSupport kernelCallSupport;
    private final String tenantId;
    private final UUID defaultOrganizationId;

    private final ConcurrentHashMap<UUID, UUID> agencyCache = new ConcurrentHashMap<>();

    public KernelResourceAdapter(
            KernelResourceApiClient resourceClient,
            KernelOrganizationApiClient organizationClient,
            VehicleLocalR2dbcRepository vehicleRepository,
            DriverR2dbcRepository driverRepository,
            UserLocalR2dbcRepository userRepository,
            KernelTokenHolder kernelTokenHolder,
            KernelCallSupport kernelCallSupport,
            String tenantId,
            UUID defaultOrganizationId) {
        this.resourceClient = resourceClient;
        this.organizationClient = organizationClient;
        this.vehicleRepository = vehicleRepository;
        this.driverRepository = driverRepository;
        this.userRepository = userRepository;
        this.kernelTokenHolder = kernelTokenHolder;
        this.kernelCallSupport = kernelCallSupport;
        this.tenantId = tenantId;
        this.defaultOrganizationId = defaultOrganizationId;
    }

    @Override
    public boolean isKernelMode() {
        return true;
    }

    @Override
    public Mono<Vehicle> getExternalVehicleInfo(UUID vehicleId, String token) {
        return vehicleRepository.findById(vehicleId)
                .flatMap(entity -> {
                    UUID resourceId = entity.getKernelResourceId() != null
                            ? entity.getKernelResourceId() : vehicleId;
                    UUID orgId = defaultOrganizationId;
                    return resolveToken(token)
                            .flatMap(bearer -> resourceClient.getResource(
                                    bearerHeader(bearer), tenantId, orgId.toString(), resourceId))
                            .flatMap(this::mapResourceResponse)
                            .map(remote -> enrichFromLocal(entity, remote));
                })
                .doOnSubscribe(s -> log.debug("🚗 [KERNEL RESOURCE] GET vehicle {}", vehicleId))
                .onErrorResume(e -> {
                    log.warn("⚠️ [KERNEL RESOURCE] GET {} ignoré : {}", vehicleId, e.getMessage());
                    return Mono.empty();
                });
    }

    @Override
    public Mono<Vehicle> createRemoteVehicle(
            VehicleRequest request, String token,
            String brandLabel, String modelLabel, String fuelLabel,
            String transLabel, String colorLabel,
            VehicleRemoteContext context) {
        UUID organizationId = context.organizationId() != null
                ? context.organizationId() : defaultOrganizationId;

        return kernelCallSupport.run("kernel-resource",
                resolveToken(token)
                .flatMap(bearer -> ensureAgency(organizationId, bearer)
                        .flatMap(agencyId -> {
                            String serial = request.vehicleSerialNumber() != null
                                    && !request.vehicleSerialNumber().isBlank()
                                    ? request.vehicleSerialNumber()
                                    : request.licensePlate();
                            var body = new KernelResourceApiClient.RegisterMaterialResourceRequest(
                                    organizationId,
                                    agencyId,
                                    toResourceCode(request.licensePlate()),
                                    brandLabel + " " + modelLabel,
                                    VEHICLE_CATEGORY,
                                    serial
                            );
                            return resourceClient.registerResource(
                                    bearerHeader(bearer),
                                    tenantId,
                                    organizationId.toString(),
                                    body);
                        }))
                .flatMap(this::mapResourceResponse)
                .doOnSuccess(v -> log.info("✅ [KERNEL RESOURCE] Ressource créée : {} ({})",
                        v.licensePlate(), v.kernelResourceId()))
                .onErrorMap(this::wrapError));
    }

    @Override
    public Mono<Vehicle> updateRemoteVehicle(
            UUID vehicleId, VehicleRequest request, String token,
            String brandLabel, String modelLabel, String fuelLabel,
            String transLabel, String colorLabel,
            VehicleRemoteContext context) {
        return getExternalVehicleInfo(vehicleId, token);
    }

    @Override
    public Mono<Vehicle> patchRemoteVehicle(UUID vehicleId, Map<String, Object> updates, String token) {
        return getExternalVehicleInfo(vehicleId, token);
    }

    @Override
    public Mono<Void> deleteRemoteVehicle(UUID vehicleId, String token) {
        return vehicleRepository.findById(vehicleId)
                .flatMap(entity -> {
                    UUID resourceId = entity.getKernelResourceId() != null
                            ? entity.getKernelResourceId() : vehicleId;
                    return resolveToken(token)
                            .flatMap(bearer -> resourceClient.unassignResource(
                                    bearerHeader(bearer),
                                    tenantId,
                                    defaultOrganizationId.toString(),
                                    resourceId))
                            .then();
                })
                .onErrorResume(e -> Mono.empty());
    }

    @Override
    public Mono<Void> uploadDocument(UUID vehicleId, String docType, FilePart file, String token) {
        return Mono.empty();
    }

    @Override
    public Mono<Void> deleteDocument(UUID vehicleId, String docType, String token) {
        return Mono.empty();
    }

    @Override
    public Mono<String> addImage(UUID vehicleId, FilePart file, String token) {
        return Mono.empty();
    }

    @Override
    public Flux<String> getImages(UUID vehicleId, String token) {
        return Flux.empty();
    }

    @Override
    public Mono<Void> deleteImage(String imageId, String token) {
        return Mono.empty();
    }

    @Override
    public Flux<Map<String, Object>> getReferenceData(String resource, String token) {
        return Flux.empty();
    }

    @Override
    public Mono<Void> assignDriverRemote(UUID vehicleId, UUID driverUserId, String token) {
        return kernelCallSupport.run("kernel-resource",
                Mono.zip(
                        vehicleRepository.findById(vehicleId),
                        driverRepository.findById(driverUserId))
                .flatMap(tuple -> {
                    var vehicle = tuple.getT1();
                    var driver = tuple.getT2();
                    UUID resourceId = vehicle.getKernelResourceId() != null
                            ? vehicle.getKernelResourceId() : vehicleId;
                    Mono<UUID> assigneeMono = driver.getKernelActorId() != null
                            ? Mono.just(driver.getKernelActorId())
                            : userRepository.findById(driverUserId)
                                    .map(u -> u.getKernelId() != null ? u.getKernelId() : driverUserId)
                                    .defaultIfEmpty(driverUserId);
                    UUID orgId = defaultOrganizationId;
                    return assigneeMono.flatMap(assigneeIdResolved -> {
                        var body = new KernelResourceApiClient.AssignMaterialResourceRequest(
                                "ACTOR", assigneeIdResolved);
                        return resolveToken(token)
                                .flatMap(bearer -> resourceClient.assignResource(
                                        bearerHeader(bearer),
                                        tenantId,
                                        orgId.toString(),
                                        resourceId,
                                        body));
                    });
                })
                .doOnSuccess(v -> log.info("✅ [KERNEL RESOURCE] Conducteur {} assigné au véhicule {}",
                        driverUserId, vehicleId))
                .then()
                .onErrorResume(e -> {
                    log.warn("⚠️ [KERNEL RESOURCE] Assignation conducteur ignorée : {}", e.getMessage());
                    return Mono.empty();
                }));
    }

    private Mono<UUID> ensureAgency(UUID organizationId, String bearerToken) {
        UUID cached = agencyCache.get(organizationId);
        if (cached != null) {
            return Mono.just(cached);
        }
        return organizationClient.listAgencies(
                        bearerHeader(bearerToken),
                        tenantId,
                        organizationId.toString(),
                        organizationId)
                .flatMap(resp -> {
                    if (resp.success() && resp.data() != null && !resp.data().isEmpty()) {
                        UUID agencyId = resp.data().get(0).id();
                        agencyCache.put(organizationId, agencyId);
                        return Mono.just(agencyId);
                    }
                    return organizationClient.createAgency(
                                    bearerHeader(bearerToken),
                                    tenantId,
                                    organizationId.toString(),
                                    organizationId,
                                    new KernelOrganizationApiClient.CreateAgencyRequest(
                                            DEFAULT_AGENCY_CODE, "Dépôt principal FleetMan"))
                            .flatMap(createResp -> {
                                if (!createResp.success() || createResp.data() == null) {
                                    return Mono.error(new IllegalStateException(
                                            "Création agence Kernel échouée : " + createResp.message()));
                                }
                                UUID agencyId = createResp.data().id();
                                agencyCache.put(organizationId, agencyId);
                                log.info("✅ [KERNEL RESOURCE] Agence par défaut créée : {}", agencyId);
                                return Mono.just(agencyId);
                            });
                });
    }

    private Mono<Vehicle> mapResourceResponse(
            com.yowyob.fleet.infrastructure.adapters.outbound.external.client.KernelAuthApiClient.ApiResponse<
                    KernelResourceApiClient.MaterialResourceResponse> resp) {
        if (!resp.success() || resp.data() == null) {
            return Mono.error(new IllegalStateException(
                    "Réponse Kernel resource invalide : " + resp.message()));
        }
        var data = resp.data();
        String status = mapKernelStatus(data.status());
        return Mono.just(new Vehicle(
                data.id(),
                null,
                null,
                null,
                null,
                data.serialNumber(),
                data.serialNumber(),
                null,
                data.name(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                status,
                null,
                null,
                null,
                Collections.emptyList(),
                null,
                null,
                null,
                null,
                data.id()
        ));
    }

    private Vehicle enrichFromLocal(
            com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.VehicleLocalEntity local,
            Vehicle remote) {
        return new Vehicle(
                local.getId(),
                local.getFleetId(),
                local.getManagerId(),
                local.getCurrentDriverId(),
                local.getVehicleTypeId(),
                local.getLicensePlate() != null ? local.getLicensePlate() : remote.licensePlate(),
                remote.vehicleSerialNumber(),
                local.getBrand() != null ? local.getBrand() : remote.brand(),
                local.getModel() != null ? local.getModel() : remote.model(),
                local.getManufacturingYear(),
                remote.transmissionType(),
                remote.fuelType(),
                remote.tankCapacity(),
                remote.totalSeatNumber(),
                remote.averageFuelConsumption(),
                local.getColor(),
                local.getStatus() != null ? local.getStatus() : remote.status(),
                local.getPhotoUrl(),
                local.getSerialNumberPhotoUrl(),
                local.getRegistrationPhotoUrl(),
                remote.illustrationImages(),
                remote.financialParameters(),
                remote.maintenanceParameters(),
                remote.operationalParameters(),
                local.getGeofenceRemoteId(),
                local.getKernelResourceId() != null ? local.getKernelResourceId() : remote.kernelResourceId()
        );
    }

    private Mono<String> resolveToken(String userToken) {
        if (userToken != null && !userToken.isBlank()) {
            return Mono.just(userToken);
        }
        return kernelTokenHolder.getValidAccessToken();
    }

    private String bearerHeader(String token) {
        return token.startsWith("Bearer ") ? token : "Bearer " + token;
    }

    private static String toResourceCode(String licensePlate) {
        String code = licensePlate.toUpperCase().replaceAll("[^A-Z0-9]", "-");
        return code.isBlank() ? "VEH-" + UUID.randomUUID().toString().substring(0, 8) : code;
    }

    private static String mapKernelStatus(String kernelStatus) {
        if (kernelStatus == null) return "AVAILABLE";
        return switch (kernelStatus.toUpperCase()) {
            case "IN_USE", "ASSIGNED", "ON_TRIP" -> "ON_TRIP";
            case "MAINTENANCE", "IN_MAINTENANCE" -> "MAINTENANCE";
            default -> "AVAILABLE";
        };
    }

    private Throwable wrapError(Throwable ex) {
        if (ex instanceof WebClientResponseException wex) {
            log.error("❌ [KERNEL RESOURCE] HTTP {} — {}", wex.getStatusCode(), wex.getResponseBodyAsString());
        }
        return ex;
    }
}
