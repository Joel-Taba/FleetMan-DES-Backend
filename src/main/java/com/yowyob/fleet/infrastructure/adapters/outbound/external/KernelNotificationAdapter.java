package com.yowyob.fleet.infrastructure.adapters.outbound.external;

import com.yowyob.fleet.domain.ports.out.ExternalNotificationPort;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.client.KernelNotificationApiClient;
import com.yowyob.fleet.infrastructure.config.KernelCallSupport;
import com.yowyob.fleet.infrastructure.config.KernelTokenHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/** Anti-corruption layer vers notification-controller Kernel. */
@Slf4j
public class KernelNotificationAdapter implements ExternalNotificationPort {

    private final KernelNotificationApiClient kernelClient;
    private final KernelTokenHolder kernelTokenHolder;
    private final KernelCallSupport kernelCallSupport;
    private final String tenantId;
    private final String organizationId;

    public KernelNotificationAdapter(
            KernelNotificationApiClient kernelClient,
            KernelTokenHolder kernelTokenHolder,
            KernelCallSupport kernelCallSupport,
            String tenantId,
            String organizationId) {
        this.kernelClient = kernelClient;
        this.kernelTokenHolder = kernelTokenHolder;
        this.kernelCallSupport = kernelCallSupport;
        this.tenantId = tenantId;
        this.organizationId = organizationId;
    }

    @Override
    public Mono<Void> sendEmail(String recipientEmail, String subject, String body) {
        // run() = fallback silencieux (Mono.empty()) : un envoi de notification ne
        // doit jamais faire échouer le flux métier (création d'incident/maintenance)
        // qui le déclenche — comportement fire & forget déjà établi côté domaine
        // (SendAlertPort.sendEmail).
        return kernelCallSupport.run(
                "kernel-notification",
                kernelTokenHolder.getValidAccessToken()
                        .flatMap(token -> kernelClient.sendDelivery(
                                bearerHeader(token),
                                tenantId,
                                organizationId,
                                new KernelNotificationApiClient.SendDeliveryRequest(
                                        recipientEmail, "EMAIL", subject, body)))
                        .doOnSuccess(v -> log.info("✅ [KERNEL NOTIFICATION] Email envoyé à {}", recipientEmail))
                        .onErrorMap(this::wrapError));
    }

    private static String bearerHeader(String token) {
        return token.startsWith("Bearer ") ? token : "Bearer " + token;
    }

    private Throwable wrapError(Throwable e) {
        if (e instanceof WebClientResponseException wce) {
            log.error("❌ [KERNEL NOTIFICATION] {} : {}", wce.getStatusCode(), wce.getResponseBodyAsString());
        }
        return e;
    }
}
