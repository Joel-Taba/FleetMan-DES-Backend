package com.yowyob.fleet.infrastructure.config.bootstrap;

import com.yowyob.fleet.application.service.VehicleTypeService;
import com.yowyob.fleet.domain.ports.in.AuthUseCase;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.VehicleTypeRequest;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.resources.*;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.resources.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@Order(50)
@RequiredArgsConstructor
public class InitialDataLoader implements CommandLineRunner {

    private final AuthPort authPort;
    private final VehicleTypeService vehicleTypeService;
    private final ManufacturerR2dbcRepository mfrRepo;
    private final BrandR2dbcRepository brandRepo;
    private final VehicleModelR2dbcRepository modelRepo;
    private final VehicleSizeR2dbcRepository sizeRepo;
    private final UsageTypeR2dbcRepository usageRepo;
    private final FuelTypeR2dbcRepository fuelRepo;
    private final TransmissionTypeR2dbcRepository transRepo;
    private final VehicleColorR2dbcRepository colorRepo;

    @Value("${application.bootstrap.admin.email}") private String adminEmail;
    @Value("${application.bootstrap.admin.password}") private String adminPassword;
    @Value("${application.bootstrap.admin.username}") private String adminUsername;
    @Value("${application.bootstrap.admin.phone}") private String adminPhone;
    @Value("${application.bootstrap.admin.firstname}") private String adminFirstName;
    @Value("${application.bootstrap.admin.lastname}") private String adminLastName;
    @Value("${application.bootstrap.demo-data.enabled:false}") private boolean demoDataEnabled;

    @Override
    public void run(String... args) {
        log.info("🚀 [SEEDER] Démarrage de l'initialisation massive des ressources...");

        seedVehicleTypes()
            .then(seedManufacturers())
            .then(seedBrands())
            .then(seedModels())
            .then(seedSizes())
            .then(seedUsages())
            .then(seedFuels())
            .then(seedTransmissions())
            .then(seedColors())
            .then(seedSuperAdmin())
            .timeout(Duration.ofMinutes(5))
            .onErrorResume(e -> {
                log.error("⚠️ [SEEDER] Interruption : {}", e.getMessage());
                return Mono.empty();
            })
            .block();

        log.info("✅ [SEEDER] Toutes les ressources sont prêtes.");
    }

    private Mono<Void> seedVehicleTypes() {
        return vehicleTypeService.getAllTypes().hasElements().flatMap(exists -> {
            if (exists) return Mono.empty();
            return Flux.just(
                new VehicleTypeRequest("CAR", "Voiture", "Tourisme"),
                new VehicleTypeRequest("VAN", "Fourgonnette", "Transport léger"),
                new VehicleTypeRequest("TRUCK", "Camion", "Poids lourd"),
                new VehicleTypeRequest("BIKE", "Moto", "Deux roues"),
                new VehicleTypeRequest("BUS", "Autobus", "Transport commun"),
                new VehicleTypeRequest("PICKUP", "Pickup", "4x4 utilitaire"),
                new VehicleTypeRequest("TRACTOR", "Tracteur", "Agriculture"),
                new VehicleTypeRequest("TRAILER", "Remorque", "Logistique"),
                new VehicleTypeRequest("SUV", "SUV", "Sport Utility Vehicle"),
                new VehicleTypeRequest("COUPE", "Coupé", "Sportive")
            ).flatMap(vehicleTypeService::createType).then();
        });
    }

    private Mono<Void> seedManufacturers() {
        return mfrRepo.count().flatMap(c -> c > 0 ? Mono.empty() : Flux.just(
            new ManufacturerEntity(UUID.randomUUID(), "TOYOTA_MC", "Toyota Motor Corp", "Japon", true),
            new ManufacturerEntity(UUID.randomUUID(), "VOLKSWAGEN_AG", "Volkswagen AG", "Allemagne", true),
            new ManufacturerEntity(UUID.randomUUID(), "STELLANTIS", "Stellantis NV", "Europe", true),
            new ManufacturerEntity(UUID.randomUUID(), "HYUNDAI_MG", "Hyundai Motor Group", "Corée", true),
            new ManufacturerEntity(UUID.randomUUID(), "GM", "General Motors", "USA", true),
            new ManufacturerEntity(UUID.randomUUID(), "FORD_MC", "Ford Motor Company", "USA", true),
            new ManufacturerEntity(UUID.randomUUID(), "HONDA_MC", "Honda Motor Co", "Japon", true),
            new ManufacturerEntity(UUID.randomUUID(), "BMW_AG", "BMW AG", "Allemagne", true),
            new ManufacturerEntity(UUID.randomUUID(), "MERCEDES_BENZ_AG", "Mercedes-Benz AG", "Allemagne", true),
            new ManufacturerEntity(UUID.randomUUID(), "RENAULT_NISSAN", "Renault-Nissan", "France/Japon", true)
        ).flatMap(mfrRepo::save).then());
    }

    private Mono<Void> seedBrands() {
        return brandRepo.count().flatMap(c -> c > 0 ? Mono.empty() : Flux.just(
            new BrandEntity(UUID.randomUUID(), "TOYOTA", "Toyota", null, true),
            new BrandEntity(UUID.randomUUID(), "LEXUS", "Lexus", null, true),
            new BrandEntity(UUID.randomUUID(), "PEUGEOT", "Peugeot", null, true),
            new BrandEntity(UUID.randomUUID(), "CITROEN", "Citroën", null, true),
            new BrandEntity(UUID.randomUUID(), "HYUNDAI", "Hyundai", null, true),
            new BrandEntity(UUID.randomUUID(), "KIA", "Kia", null, true),
            new BrandEntity(UUID.randomUUID(), "MERCEDES", "Mercedes-Benz", null, true),
            new BrandEntity(UUID.randomUUID(), "BMW", "BMW", null, true),
            new BrandEntity(UUID.randomUUID(), "AUDI", "Audi", null, true),
            new BrandEntity(UUID.randomUUID(), "RENAULT", "Renault", null, true)
        ).flatMap(brandRepo::save).then());
    }

    private Mono<Void> seedModels() {
        return modelRepo.count().flatMap(c -> c > 0 ? Mono.empty() : Flux.just(
            new VehicleModelEntity(UUID.randomUUID(), "YARIS", "Yaris", null, true),
            new VehicleModelEntity(UUID.randomUUID(), "PRADO", "Land Cruiser Prado", null, true),
            new VehicleModelEntity(UUID.randomUUID(), "HILUX", "Hilux", null, true),
            new VehicleModelEntity(UUID.randomUUID(), "COROLLA", "Corolla", null, true),
            new VehicleModelEntity(UUID.randomUUID(), "TUCSON", "Tucson", null, true),
            new VehicleModelEntity(UUID.randomUUID(), "SANTA_FE", "Santa Fe", null, true),
            new VehicleModelEntity(UUID.randomUUID(), "CLIO", "Clio", null, true),
            new VehicleModelEntity(UUID.randomUUID(), "LOGAN", "Logan", null, true),
            new VehicleModelEntity(UUID.randomUUID(), "C_CLASS", "Classe C", null, true),
            new VehicleModelEntity(UUID.randomUUID(), "E_CLASS", "Classe E", null, true)
        ).flatMap(modelRepo::save).then());
    }

    private Mono<Void> seedSizes() {
        return sizeRepo.count().flatMap(c -> c > 0 ? Mono.empty() : Flux.just(
            new VehicleSizeEntity(UUID.randomUUID(), "MINI", "Citadine Mini", null, true),
            new VehicleSizeEntity(UUID.randomUUID(), "COMPACT", "Compacte", null, true),
            new VehicleSizeEntity(UUID.randomUUID(), "SEDAN", "Berline", null, true),
            new VehicleSizeEntity(UUID.randomUUID(), "SUV_SMALL", "Petit SUV", null, true),
            new VehicleSizeEntity(UUID.randomUUID(), "SUV_LARGE", "Grand SUV / 4x4", null, true),
            new VehicleSizeEntity(UUID.randomUUID(), "VAN_SMALL", "Petit Utilitaire", null, true),
            new VehicleSizeEntity(UUID.randomUUID(), "VAN_LARGE", "Grand Fourgon", null, true),
            new VehicleSizeEntity(UUID.randomUUID(), "LIGHT_TRUCK", "Camion Léger", null, true),
            new VehicleSizeEntity(UUID.randomUUID(), "HEAVY_TRUCK", "Poids Lourd", null, true),
            new VehicleSizeEntity(UUID.randomUUID(), "BUS_LONG", "Autocar Longue Distance", null, true)
        ).flatMap(sizeRepo::save).then());
    }

    private Mono<Void> seedUsages() {
        return usageRepo.count().flatMap(c -> c > 0 ? Mono.empty() : Flux.just(
            new UsageTypeEntity(UUID.randomUUID(), "TAXI", "Taxi Urbain", null, true),
            new UsageTypeEntity(UUID.randomUUID(), "PERSONAL", "Usage Personnel", null, true),
            new UsageTypeEntity(UUID.randomUUID(), "FREIGHT", "Transport de Marchandises", null, true),
            new UsageTypeEntity(UUID.randomUUID(), "SCHOOL_BUS", "Transport Scolaire", null, true),
            new UsageTypeEntity(UUID.randomUUID(), "VIP", "Transport VIP / Location Luxe", null, true),
            new UsageTypeEntity(UUID.randomUUID(), "RENTAL", "Location Courte Durée", null, true),
            new UsageTypeEntity(UUID.randomUUID(), "AMBULANCE", "Urgence Médicale", null, true),
            new UsageTypeEntity(UUID.randomUUID(), "POLICE", "Forces de l'ordre", null, true),
            new UsageTypeEntity(UUID.randomUUID(), "CONSTRUCTION", "BTP / Chantier", null, true),
            new UsageTypeEntity(UUID.randomUUID(), "AGRI", "Agriculture", null, true)
        ).flatMap(usageRepo::save).then());
    }

    private Mono<Void> seedFuels() {
        return fuelRepo.count().flatMap(c -> c > 0 ? Mono.empty() : Flux.just(
            new FuelTypeEntity(UUID.randomUUID(), "PETROL", "Essence Super", null, true),
            new FuelTypeEntity(UUID.randomUUID(), "DIESEL", "Gasoil", null, true),
            new FuelTypeEntity(UUID.randomUUID(), "ELECTRIC", "Électrique (BEV)", null, true),
            new FuelTypeEntity(UUID.randomUUID(), "HYBRID", "Hybride (HEV)", null, true),
            new FuelTypeEntity(UUID.randomUUID(), "PLUG_IN", "Hybride Rechargeable (PHEV)", null, true),
            new FuelTypeEntity(UUID.randomUUID(), "LPG", "GPL", null, true),
            new FuelTypeEntity(UUID.randomUUID(), "HYDROGEN", "Hydrogène", null, true),
            new FuelTypeEntity(UUID.randomUUID(), "BIO_ETH", "Bio-Éthanol", null, true),
            new FuelTypeEntity(UUID.randomUUID(), "CNG", "Gaz Naturel (GNV)", null, true),
            new FuelTypeEntity(UUID.randomUUID(), "OTHER", "Autre Énergie", null, true)
        ).flatMap(fuelRepo::save).then());
    }

    private Mono<Void> seedTransmissions() {
        return transRepo.count().flatMap(c -> c > 0 ? Mono.empty() : Flux.just(
            new TransmissionTypeEntity(UUID.randomUUID(), "MANUAL_5", "Manuelle (5 rapports)", null, true),
            new TransmissionTypeEntity(UUID.randomUUID(), "MANUAL_6", "Manuelle (6 rapports)", null, true),
            new TransmissionTypeEntity(UUID.randomUUID(), "AUTO_6", "Automatique (6 rapports)", null, true),
            new TransmissionTypeEntity(UUID.randomUUID(), "AUTO_8", "Automatique (8 rapports)", null, true),
            new TransmissionTypeEntity(UUID.randomUUID(), "CVT", "Variation Continue (CVT)", null, true),
            new TransmissionTypeEntity(UUID.randomUUID(), "DCT", "Double Embrayage (DCT)", null, true),
            new TransmissionTypeEntity(UUID.randomUUID(), "SEMI_AUTO", "Semi-Automatique", null, true),
            new TransmissionTypeEntity(UUID.randomUUID(), "ELECTRIC", "Directe (Électrique)", null, true),
            new TransmissionTypeEntity(UUID.randomUUID(), "ROBOTIZED", "Manuelle Robotisée", null, true),
            new TransmissionTypeEntity(UUID.randomUUID(), "TIPTRONIC", "Tiptronic", null, true)
        ).flatMap(transRepo::save).then());
    }

    private Mono<Void> seedColors() {
        return colorRepo.count().flatMap(c -> c > 0 ? Mono.empty() : Flux.just(
            new VehicleColorEntity(UUID.randomUUID(), "WHITE", "Blanc Arctique", null, true),
            new VehicleColorEntity(UUID.randomUUID(), "BLACK", "Noir Obsidienne", null, true),
            new VehicleColorEntity(UUID.randomUUID(), "SILVER", "Gris Argent", null, true),
            new VehicleColorEntity(UUID.randomUUID(), "GREY", "Gris Anthracite", null, true),
            new VehicleColorEntity(UUID.randomUUID(), "BLUE", "Bleu Nuit", null, true),
            new VehicleColorEntity(UUID.randomUUID(), "RED", "Rouge Flamme", null, true),
            new VehicleColorEntity(UUID.randomUUID(), "BROWN", "Marron Terre", null, true),
            new VehicleColorEntity(UUID.randomUUID(), "YELLOW", "Jaune Taxi", null, true),
            new VehicleColorEntity(UUID.randomUUID(), "GREEN", "Vert Forêt", null, true),
            new VehicleColorEntity(UUID.randomUUID(), "ORANGE", "Orange Cuivré", null, true)
        ).flatMap(colorRepo::save).then());
    }

    private Mono<Void> seedSuperAdmin() {
        if (demoDataEnabled) {
            log.info("ℹ️ [SEEDER] Profil demo actif — bootstrap super-admin distant ignoré.");
            return Mono.empty();
        }
        return authPort.login(adminEmail, adminPassword)
            .doOnSuccess(resp -> log.info("ℹ️ Super Admin déjà présent."))
            .onErrorResume(e -> {
                log.info("⚠️ Création du Super Admin...");
                AuthUseCase.RegisterCommand cmd = new AuthUseCase.RegisterCommand(
                    adminUsername, adminPassword, adminEmail, adminPhone,
                    adminFirstName, adminLastName, List.of("FLEET_ADMIN", "FLEET_SUPER_ADMIN"), null
                );
                return authPort.registerInRemote(cmd)
                    .onErrorResume(err -> (err instanceof WebClientResponseException w && w.getStatusCode() == HttpStatus.CONFLICT) 
                        ? Mono.empty() : Mono.error(err));
            }).then();
    }
}