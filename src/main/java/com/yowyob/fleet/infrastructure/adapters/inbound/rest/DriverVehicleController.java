package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.application.service.DriverVehicleService;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.UUID;

/** Véhicules du chauffeur connecté, pour consulter et exporter leurs documents. */
@RestController
@RequestMapping("/api/v1/drivers/me/vehicles")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_DRIVERS)
@SecurityRequirement(name = "bearerAuth")
public class DriverVehicleController {

    private final DriverVehicleService driverVehicles;

    private static UUID userId(Authentication auth) {
        return ((AuthPort.UserDetail) auth.getPrincipal()).id();
    }

    @GetMapping
    @PreAuthorize("hasRole('FLEET_DRIVER')")
    @Operation(summary = "Mes véhicules (habituel, trajet en cours, postes du jour)",
            description = "Une entrée par véhicule ; reasons parmi REGULAR, TRIP, SHIFT. "
                    + "Les documents de ces véhicules sont lisibles via GET /api/v1/vehicles/{id}/documents.")
    public Flux<DriverVehicleService.DriverVehicle> myVehicles(Authentication auth) {
        return driverVehicles.myVehicles(userId(auth));
    }
}
