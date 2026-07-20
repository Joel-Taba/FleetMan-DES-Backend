package com.yowyob.fleet.infrastructure.config.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.core.io.ClassPathResource;
import org.springframework.r2dbc.connection.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;
import io.r2dbc.spi.ConnectionFactory;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.UserLocalR2dbcRepository;
import reactor.core.publisher.Mono;

/**
 * Charge les données de démonstration après {@link InitialDataLoader}
 * (référentiels véhicules déjà présents).
 * Activé uniquement avec {@code application.bootstrap.demo-data.enabled=true}.
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "application.bootstrap.demo-data.enabled", havingValue = "true")
public class DevDemoDataSeeder implements CommandLineRunner {

    private final ConnectionFactory connectionFactory;
    private final UserLocalR2dbcRepository userRepo;

    @Override
    public void run(String... args) {
        userRepo.existsById(DemoTestAccounts.SUPER_ADMIN_ID)
                .flatMap(exists -> {
                    if (Boolean.FALSE.equals(exists)) {
                        log.info("🌱 [DEMO] Chargement des données de test fleet...");
                        var populator = new ResourceDatabasePopulator(new ClassPathResource("db/demo-seed.sql"));
                        return Mono.from(populator.populate(connectionFactory));
                    }
                    log.info("🌱 [DEMO] Données déjà présentes — seed ignoré.");
                    return Mono.empty();
                })
                .then(runKernelOrgPatch())
                .then(runKernelFleetExtPatch())
                .doOnSuccess(v -> log.info("✅ [DEMO] Données de test chargées."))
                .doOnError(e -> log.error("❌ [DEMO] Échec du seed : {}", e.getMessage(), e))
                .onErrorResume(e -> Mono.empty())
                .block();
    }

    private Mono<Void> runKernelOrgPatch() {
        var patch = new ResourceDatabasePopulator(new ClassPathResource("db/demo-kernel-org-patch.sql"));
        return Mono.from(patch.populate(connectionFactory));
    }

    private Mono<Void> runKernelFleetExtPatch() {
        var patch = new ResourceDatabasePopulator(new ClassPathResource("db/demo-seed-kernel-fleet-ext.sql"));
        return Mono.from(patch.populate(connectionFactory));
    }
}
