package com.yowyob.fleet.infrastructure.adapters.outbound.external;

import com.yowyob.fleet.domain.ports.out.ExternalOrganizationPort;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.client.KernelAuthApiClient;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.client.KernelOrganizationApiClient;
import com.yowyob.fleet.infrastructure.config.KernelCallSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
public class KernelOrganizationAdapter implements ExternalOrganizationPort {

    private final KernelOrganizationApiClient organizationClient;
    private final KernelCallSupport kernelCallSupport;
    private final String tenantId;

    public KernelOrganizationAdapter(
            KernelOrganizationApiClient organizationClient,
            KernelCallSupport kernelCallSupport,
            String tenantId) {
        this.organizationClient = organizationClient;
        this.kernelCallSupport = kernelCallSupport;
        this.tenantId = tenantId;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public Mono<OrganizationInfo> getOrganization(UUID organizationId, String bearerToken) {
        return kernelCallSupport.run("kernel-organization",
                organizationClient.getOrganization(
                        bearerHeader(bearerToken),
                        tenantId,
                        organizationId.toString(),
                        organizationId)
                .flatMap(this::mapResponse)
                .doOnSubscribe(s -> log.info("🏢 [KERNEL ORG] GET organization {}", organizationId))
                .onErrorMap(this::wrapError));
    }

    @Override
    public Mono<OrganizationInfo> createOrganization(CreateOrganizationCommand command, String bearerToken) {
        var request = new KernelOrganizationApiClient.CreateOrganizationRequest(
                command.businessActorId(),
                command.code(),
                command.service(),
                command.shortName(),
                command.longName()
        );
        return kernelCallSupport.run("kernel-organization",
                organizationClient.createOrganization(
                        bearerHeader(bearerToken),
                        tenantId,
                        request)
                .flatMap(this::mapResponse)
                .doOnSuccess(org -> log.info("✅ [KERNEL ORG] Organisation créée : {} ({})", org.displayName(), org.id()))
                .onErrorMap(this::wrapError));
    }

    @Override
    public Mono<Void> approveOrganization(UUID organizationId, String reason, String bearerToken) {
        return organizationClient.approveOrganization(
                        bearerHeader(bearerToken),
                        tenantId,
                        organizationId,
                        new KernelOrganizationApiClient.GovernanceActionRequest(reason))
                .flatMap(resp -> {
                    if (!resp.success()) {
                        return Mono.error(new IllegalStateException(
                                "Approbation org échouée : " + resp.message()));
                    }
                    log.info("✅ [KERNEL ORG] Organisation {} approuvée", organizationId);
                    return Mono.<Void>empty();
                })
                .onErrorMap(this::wrapError);
    }

    @Override
    public Mono<Void> subscribeService(UUID organizationId, String serviceCode, String bearerToken) {
        var body = new KernelOrganizationApiClient.SubscribeServiceRequest(
                serviceCode, 100_000, 3600);
        return organizationClient.subscribeService(
                        bearerHeader(bearerToken),
                        tenantId,
                        organizationId,
                        body)
                .flatMap(resp -> {
                    if (!resp.success()) {
                        String msg = resp.message() != null ? resp.message() : "";
                        if (msg.toLowerCase().contains("already")) {
                            log.debug("ℹ️ [KERNEL ORG] Service {} déjà souscrit pour {}", serviceCode, organizationId);
                            return Mono.<Void>empty();
                        }
                        return Mono.error(new IllegalStateException(
                                "Souscription service " + serviceCode + " échouée : " + msg));
                    }
                    log.info("✅ [KERNEL ORG] Service {} souscrit pour org {}", serviceCode, organizationId);
                    return Mono.<Void>empty();
                })
                .onErrorResume(WebClientResponseException.class, ex -> {
                    if (ex.getStatusCode() == HttpStatus.CONFLICT) {
                        return Mono.<Void>empty();
                    }
                    return Mono.error(wrapError(ex));
                });
    }

    private Mono<OrganizationInfo> mapResponse(
            KernelAuthApiClient.ApiResponse<KernelOrganizationApiClient.OrganizationResponse> resp) {
        if (!resp.success() || resp.data() == null) {
            return Mono.error(new IllegalStateException(
                    "Réponse Kernel organization invalide : " + resp.message()));
        }
        var data = resp.data();
        return Mono.just(new OrganizationInfo(
                data.id(),
                data.tenantId(),
                data.displayName() != null ? data.displayName() : data.legalName(),
                data.governanceStatus(),
                data.isActive()
        ));
    }

    private String bearerHeader(String token) {
        return token.startsWith("Bearer ") ? token : "Bearer " + token;
    }

    private Throwable wrapError(Throwable ex) {
        if (ex instanceof WebClientResponseException wex) {
            log.error("❌ [KERNEL ORG] HTTP {} — {}", wex.getStatusCode(), wex.getResponseBodyAsString());
        }
        return ex;
    }
}
