package com.yowyob.fleet.infrastructure.config;

import com.yowyob.fleet.infrastructure.adapters.outbound.external.client.*;
import io.netty.resolver.DefaultAddressResolverGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import reactor.core.publisher.Mono;
import io.netty.channel.ChannelOption;
import java.time.Duration;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import reactor.netty.http.client.HttpClient;

@Configuration
public class WebClientConfig {

    private static final Logger log = LoggerFactory.getLogger(WebClientConfig.class);

    /**
     * Filtre pour logger toutes les requêtes sortantes vers les microservices tiers.
     */
    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            log.info("📡 [OUTBOUND CALL] {} {}", clientRequest.method(), clientRequest.url());
            return Mono.just(clientRequest);
        });
    }

  private static final Duration KERNEL_DEFAULT_RESPONSE_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration KERNEL_KYC_RESPONSE_TIMEOUT = Duration.ofSeconds(180);

    private HttpClient kernelHttpClient() {
        return kernelHttpClient(KERNEL_DEFAULT_RESPONSE_TIMEOUT);
    }

    private HttpClient kernelHttpClient(Duration responseTimeout) {
        return HttpClient.create()
                .resolver(DefaultAddressResolverGroup.INSTANCE)
                .responseTimeout(responseTimeout)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000);
    }

    // --- BUILDER DE BASE AVEC LOGGING ---
    @Bean
    @Primary // Pour que ce builder soit celui utilisé par défaut partout
    public WebClient.Builder webClientBuilder() {
         HttpClient httpClient = HttpClient.create()
                .resolver(DefaultAddressResolverGroup.INSTANCE);
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .filter(logRequest()); // On lui injecte le filtre de log
    }

    // --- KERNEL RT-COMOPS ---

    @Bean("kernelKycWebClient")
    public WebClient kernelKycWebClient(
            @Value("${application.kernel.url:https://kernel-core.yowyob.com/kernel-api}") String kernelUrl,
            @Value("${application.kernel.client-id:fleet-management-backend}") String clientId,
            @Value("${application.kernel.api-key:fleet-api-key-2026}") String apiKey,
            @Value("${application.kernel.tenant-id:}") String tenantId) {
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(kernelHttpClient(KERNEL_KYC_RESPONSE_TIMEOUT)))
                .baseUrl(kernelUrl)
                .defaultHeader("X-Client-Id", clientId)
                .defaultHeader("X-Api-Key", apiKey)
                .defaultHeader("X-Tenant-Id", tenantId)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .filter(logRequest())
                .build();
    }

    @Bean("kernelWebClient")
    public WebClient kernelWebClient(
            WebClient.Builder builder,
            @Value("${application.kernel.url:https://kernel-core.yowyob.com/kernel-api}") String kernelUrl,
            @Value("${application.kernel.client-id:fleet-management-backend}") String clientId,
            @Value("${application.kernel.api-key:fleet-api-key-2026}") String apiKey,
            @Value("${application.kernel.tenant-id:}") String tenantId) {
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(kernelHttpClient()))
                .baseUrl(kernelUrl)
                .defaultHeader("X-Client-Id", clientId)
                .defaultHeader("X-Api-Key", apiKey)
                .defaultHeader("X-Tenant-Id", tenantId)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .filter(logRequest())
                .build();
    }

    @Bean
    public KernelAuthApiClient kernelAuthApiClient(
            @Value("${application.kernel.url:https://kernel-core.yowyob.com/kernel-api}") String kernelUrl,
            @Value("${application.kernel.client-id:fleet-management-backend}") String clientId,
            @Value("${application.kernel.api-key:fleet-api-key-2026}") String apiKey,
            @Value("${application.kernel.tenant-id:}") String tenantId) {
        WebClient webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(kernelHttpClient()))
                .baseUrl(kernelUrl)
                .defaultHeader("X-Client-Id", clientId)
                .defaultHeader("X-Api-Key", apiKey)
                .defaultHeader("X-Tenant-Id", tenantId)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .filter(logRequest())
                .build();
        return createProxy(webClient, KernelAuthApiClient.class);
    }

    @Bean
    public KernelAdminApiClient kernelAdminApiClient(
            @Value("${application.kernel.url}") String kernelUrl,
            @Value("${application.kernel.client-id}") String clientId,
            @Value("${application.kernel.api-key}") String apiKey,
            @Value("${application.kernel.tenant-id:}") String tenantId) {
        WebClient webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(kernelHttpClient()))
                .baseUrl(kernelUrl)
                .defaultHeader("X-Client-Id", clientId)
                .defaultHeader("X-Api-Key", apiKey)
                .defaultHeader("X-Tenant-Id", tenantId)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .filter(logRequest())
                .build();
        return createProxy(webClient, KernelAdminApiClient.class);
    }

    @Bean
    public KernelOrganizationApiClient kernelOrganizationApiClient(
            @Value("${application.kernel.url}") String kernelUrl,
            @Value("${application.kernel.client-id}") String clientId,
            @Value("${application.kernel.api-key}") String apiKey,
            @Value("${application.kernel.tenant-id:}") String tenantId) {
        WebClient webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(kernelHttpClient()))
                .baseUrl(kernelUrl)
                .defaultHeader("X-Client-Id", clientId)
                .defaultHeader("X-Api-Key", apiKey)
                .defaultHeader("X-Tenant-Id", tenantId)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .filter(logRequest())
                .build();
        return createProxy(webClient, KernelOrganizationApiClient.class);
    }

    @Bean
    public KernelResourceApiClient kernelResourceApiClient(
            @Value("${application.kernel.url}") String kernelUrl,
            @Value("${application.kernel.client-id}") String clientId,
            @Value("${application.kernel.api-key}") String apiKey,
            @Value("${application.kernel.tenant-id:}") String tenantId) {
        WebClient webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(kernelHttpClient()))
                .baseUrl(kernelUrl)
                .defaultHeader("X-Client-Id", clientId)
                .defaultHeader("X-Api-Key", apiKey)
                .defaultHeader("X-Tenant-Id", tenantId)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .filter(logRequest())
                .build();
        return createProxy(webClient, KernelResourceApiClient.class);
    }

    @Bean
    public KernelFileApiClient kernelFileApiClient(
            @Value("${application.kernel.url}") String kernelUrl,
            @Value("${application.kernel.client-id}") String clientId,
            @Value("${application.kernel.api-key}") String apiKey,
            @Value("${application.kernel.tenant-id:}") String tenantId) {
        WebClient webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(kernelHttpClient()))
                .baseUrl(kernelUrl)
                .defaultHeader("X-Client-Id", clientId)
                .defaultHeader("X-Api-Key", apiKey)
                .defaultHeader("X-Tenant-Id", tenantId)
                .filter(logRequest())
                .build();
        return createProxy(webClient, KernelFileApiClient.class);
    }

    // --- CLIENTS STANDARDS ---

    @Bean("paymentWebClient")
    public WebClient paymentWebClient(WebClient.Builder builder,
                                      @Value("${application.external.payment-service-url}") String url) {
        return builder.baseUrl(url).filter(logRequest()).build();
    }

    @Bean
    public VehicleApiClient vehicleApiClient(WebClient.Builder builder,
                                             @Value("${application.external.vehicle-service-url}") String url) {
        WebClient webClient = builder.baseUrl(url).filter(logRequest()).build();
        return createProxy(webClient, VehicleApiClient.class);
    }

    @Bean
    public AuthApiClient authApiClient(WebClient.Builder builder,
                                       @Value("${application.auth.url}") String url) {
        WebClient webClient = builder.baseUrl(url).filter(logRequest()).build();
        return createProxy(webClient, AuthApiClient.class);
    }

    @Bean
    public NotificationApiClient notificationApiClient(WebClient.Builder builder,
                                                      @Value("${application.notification.url}") String url) {
        WebClient webClient = builder.baseUrl(url).filter(logRequest()).build();
        return createProxy(webClient, NotificationApiClient.class);
    }

    // --- CLIENTS SPECIAUX (GEOFENCE - SSL INSECURE) ---

    @Bean
    public GeofenceApiClient geofenceApiClient(@Value("${application.external.geofence-service-url}") String url) {
        WebClient webClient = createInsecureWebClient(url).build();
        return createProxy(webClient, GeofenceApiClient.class);
    }

    @Bean
    public GeofenceAuthClient geofenceAuthClient(WebClient.Builder builder,
                                                 @Value("${application.external.geofence-service-url}") String url) {
        WebClient webClient = builder.baseUrl(url).build();
        return createProxy(webClient, GeofenceAuthClient.class);
    }

    // TODO: Activer après implémentation du service de notification
    // @Bean
    // public NotificationApiClient notificationApiClient(WebClient.Builder builder,
    //                                                    @Value("${application.external.notification.url}") String url) {
    //     WebClient webClient = builder.baseUrl(url).build();
    //     return createProxy(webClient, NotificationApiClient.class);
    // }

    // Helper générique
    private <S> S createProxy(WebClient webClient, Class<S> serviceClass) {
        WebClientAdapter adapter = WebClientAdapter.create(webClient);
        HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(adapter).build();
        return factory.createClient(serviceClass);
    }

    /**
     * Crée un WebClient qui accepte les certificats SSL auto-signés (Utile pour le service Geofence).
     */
    private WebClient.Builder createInsecureWebClient(String baseUrl) {
        try {
            SslContext sslContext = SslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE).build();
            HttpClient httpClient = HttpClient.create()
                .secure(t -> t.sslContext(sslContext))
                .resolver(DefaultAddressResolverGroup.INSTANCE);

            return WebClient.builder()
                    .baseUrl(baseUrl)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .filter(logRequest())
                    .clientConnector(new ReactorClientHttpConnector(httpClient));
        } catch (Exception e) {
            throw new RuntimeException("Erreur configuration WebClient Insecure", e);
        }
    }

    @Bean(name = "syncInternalWebClient")
    public WebClient syncInternalWebClient(@Value("${server.port:8081}") int serverPort) {
        return WebClient.builder()
                .baseUrl("http://127.0.0.1:" + serverPort)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                // Marque cet appel comme un replay interne de SyncPushService, qui gère
                // déjà lui-même l'idempotence sur fleet.sync_mutations avec cette même
                // clé — voir IdempotencyWebFilter.shouldApply().
                .defaultHeader("X-Internal-Sync-Call", "true")
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create()))
                .build();
    }
}
