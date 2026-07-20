package com.yowyob.fleet.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value; 
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
public class OpenApiConfig {

    // Injection de l'URL définie dans le YAML
    @Value("${app.swagger.server-url}")
    private String serverUrl;

    // --- 1. DÉFINITION DES CONSTANTES (SPOF: Single Point Of Failure) ---
    public static final String TAG_MONITORING = "01. Monitoring";
    public static final String TAG_SYNC = "01b. Offline Sync";
    public static final String TAG_AUTH = "02. Auth";
    public static final String TAG_ACCOUNT = "03. Account";
    public static final String TAG_SUPER_ADMIN = "04. Super Admin | Gestion des Administrateurs";
    public static final String TAG_ADMIN_MANAGERS = "05. Admin | Gestion des Fleet Managers";
    public static final String TAG_ADMIN_RESOURCES = "06. Admin | Gestion des Ressources";
    public static final String TAG_FLEET_MANAGERS = "07. Fleet Managers";
    public static final String TAG_DRIVERS = "08. Drivers";
    // --- SOUS-TAGS VEHICLES ---
    public static final String TAG_VHC_PARC = "09a. Vehicles | Gestion du Parc [Fleet MANAGER]";
    public static final String TAG_VHC_MEDIA = "09b. Vehicles | Médias & Documents [Fleet MANAGER]";
    public static final String TAG_VHC_OP = "09c. Vehicles | Opérationnel [DRIVER]";
    public static final String TAG_VHC_LOOKUP = "09d. Vehicles | Référentiels [PUBLIC/Fleet MANAGER]";
    public static final String TAG_FLEETS = "10. Fleets";
    public static final String TAG_TRIPS = "11. Trips";
    public static final String TAG_GEOFENCING = "12. Geofencing";
    public static final String TAG_PAYMENTS = "13. Payments";
    // --- MODULE 14-16 : OPÉRATIONS TERRAIN ---
    public static final String TAG_OPS_MAINTENANCE = "14. Operations | Maintenance";
    public static final String TAG_OPS_INCIDENTS   = "15. Operations | Incidents";
    public static final String TAG_OPS_FUEL        = "16. Operations | Recharges Carburant";
    // --- SOUS-TAGS RESSOURCES ---
    public static final String TAG_RES_TYPES   = "06a. Admin | Ressources : Types de véhicules";
    public static final String TAG_RES_MFR     = "06b. Admin | Ressources : Constructeurs";
    public static final String TAG_RES_BRANDS  = "06c. Admin | Ressources : Marques";
    public static final String TAG_RES_MODELS  = "06d. Admin | Ressources : Modèles";
    public static final String TAG_RES_SIZES   = "06e. Admin | Ressources : Gabarits";
    public static final String TAG_RES_USAGES  = "06f. Admin | Ressources : Usages";
    public static final String TAG_RES_FUELS   = "06g. Admin | Ressources : Carburants";
    public static final String TAG_RES_TRANS   = "06h. Admin | Ressources : Transmissions";
    public static final String TAG_RES_COLORS  = "06i. Admin | Ressources : Couleurs";

      // --- MODULE 17 : BUDGET & DÉPENSES ---
    public static final String TAG_BUDGET_EXPENSES = "17a. Budget | Dépenses opérationnelles";
    public static final String TAG_BUDGET_BUDGETS  = "17b. Budget | Budgets mensuels";

    // --- MODULE 18 : SCORING CONDUCTEUR ---
    public static final String TAG_SCORING = "18. Scoring | Scores Conducteurs";

    // --- MODULE 19 : MAINTENANCE PRÉVENTIVE ---
    public static final String TAG_PREVENTIVE_PLANS  = "19a. Maintenance Préventive | Plans";
    public static final String TAG_PREVENTIVE_ALERTS = "19b. Maintenance Préventive | Alertes";

    // --- MODULE 20 : ALERTES & RÈGLES MÉTIER ---
    public static final String TAG_ALERT_RULES  = "20a. Alertes | Règles Métier";
    public static final String TAG_ALERT_EVENTS = "20b. Alertes | Notifications";

    // --- MODULE 10 : FLEETS ---
    public static final String TAG_FLEETS_CRUD = "10a. Fleets | Administration (CRUD)";
    public static final String TAG_FLEETS_VHC  = "10b. Fleets | Mes Véhicules";
    public static final String TAG_FLEETS_DRV  = "10c. Fleets | Mes Chauffeurs";

    // --- MODULE 11 : TRIPS ---
    public static final String TAG_TRIPS_OPS = "11a. Trips | Opérations Chauffeur (Mobile)";
    public static final String TAG_TRIPS_MGT = "11b. Trips | Suivi & Historique (Manager)";

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("YowYob Fleet Management API")
                        .version("1.0.0")
                        .description("API Réactive pour la gestion de flottes et le géorepérage.")
                        .contact(new Contact().name("Gabriel Nomo").email("g.nomo@yowyob.com")))
                // Configuration dynamique des serveurs pour Swagger UI
                .servers(List.of(
                        new Server().url(serverUrl).description("Serveur de Production (Proxy)"),
                        new Server().url("http://localhost:8080").description("Localhost")
                ))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }

    @Bean
    public OpenApiCustomizer sortTagsAlphabetically() {
        return openApi -> {
            // --- 2. DÉFINITION DE L'ORDRE STRICT ---
            List<String> order = List.of(
                    TAG_MONITORING,
                    TAG_AUTH,
                    TAG_ACCOUNT,
                    TAG_SUPER_ADMIN,
                    TAG_ADMIN_MANAGERS,
                    TAG_ADMIN_RESOURCES,
                    TAG_RES_TYPES ,
                    TAG_RES_MFR,
                    TAG_RES_BRANDS,
                    TAG_RES_MODELS,
                    TAG_RES_SIZES,
                    TAG_RES_USAGES,
                    TAG_RES_FUELS,
                    TAG_RES_TRANS,
                    TAG_RES_COLORS,
                    TAG_FLEET_MANAGERS,
                    TAG_DRIVERS,
                    TAG_VHC_PARC, 
                    TAG_VHC_MEDIA,
                    TAG_VHC_OP,
                    TAG_VHC_LOOKUP,
                    TAG_FLEETS,
                    TAG_FLEETS_CRUD,
                    TAG_FLEETS_VHC,
                    TAG_FLEETS_DRV,
                    TAG_TRIPS,
                    TAG_TRIPS_OPS,
                    TAG_TRIPS_MGT,
                    TAG_GEOFENCING,
                    TAG_PAYMENTS,
                    TAG_OPS_MAINTENANCE,
                    TAG_OPS_INCIDENTS,
                    TAG_OPS_FUEL,
                    TAG_BUDGET_EXPENSES,
                    TAG_BUDGET_BUDGETS,
                    TAG_SCORING,
                    TAG_PREVENTIVE_PLANS,
                    TAG_PREVENTIVE_ALERTS,
                    TAG_ALERT_RULES,
                    TAG_ALERT_EVENTS
            );

            if (openApi.getTags() != null) {
                openApi.setTags(openApi.getTags().stream()
                        .sorted(Comparator.comparingInt(tag -> {
                            int index = order.indexOf(tag.getName());
                            // Si le tag n'est pas dans la liste, on le met à la fin (999)
                            return index == -1 ? 999 : index;
                        }))
                        .collect(Collectors.toList()));
            }
        };
    }
}