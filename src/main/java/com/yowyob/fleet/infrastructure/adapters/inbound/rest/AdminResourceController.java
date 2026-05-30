package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.application.service.VehicleResourceService;
import com.yowyob.fleet.application.service.VehicleTypeService;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.VehicleTypeRequest;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.resources.ResourceRequest;
import com.yowyob.fleet.infrastructure.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/resources")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
public class AdminResourceController {

    private final VehicleTypeService typeService;
    private final VehicleResourceService service;

    // ========================================================================
    // --- 06a. VEHICLE TYPES ---
    // ========================================================================
    @Tag(name = OpenApiConfig.TAG_RES_TYPES)
    @PostMapping("/vehicle-types") @ResponseStatus(HttpStatus.CREATED)
    public Mono<?> createType(@RequestBody VehicleTypeRequest r) { return typeService.createType(r); }
    
    @Tag(name = OpenApiConfig.TAG_RES_TYPES)
    @GetMapping("/vehicle-types") public Flux<?> listTypes() { return typeService.getAllTypes(); }
    
    @Tag(name = OpenApiConfig.TAG_RES_TYPES)
    @GetMapping("/vehicle-types/{id}") public Mono<?> getType(@PathVariable UUID id) { return typeService.getTypeById(id); }
    
    @Tag(name = OpenApiConfig.TAG_RES_TYPES)
    @PutMapping("/vehicle-types/{id}") public Mono<?> updateType(@PathVariable UUID id, @RequestBody VehicleTypeRequest r) { return typeService.updateType(id, r); }
    
    @Tag(name = OpenApiConfig.TAG_RES_TYPES)
    @DeleteMapping("/vehicle-types/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delType(@PathVariable UUID id) { return typeService.deleteType(id); }

    // ========================================================================
    // --- 06b. CONSTRUCTEURS (MANUFACTURERS) ---
    // ========================================================================
    @Tag(name = OpenApiConfig.TAG_RES_MFR)
    @PostMapping("/manufacturers") @ResponseStatus(HttpStatus.CREATED)
    public Mono<?> createMfr(@RequestBody ResourceRequest r) { return service.createMfr(r); }
    
    @Tag(name = OpenApiConfig.TAG_RES_MFR)
    @GetMapping("/manufacturers") public Flux<?> listMfrs() { return service.getAllMfr(); }
    
    @Tag(name = OpenApiConfig.TAG_RES_MFR)
    @GetMapping("/manufacturers/{id}") public Mono<?> getMfr(@PathVariable UUID id) { return service.getMfr(id); }
    
    @Tag(name = OpenApiConfig.TAG_RES_MFR)
    @PutMapping("/manufacturers/{id}") public Mono<?> updateMfr(@PathVariable UUID id, @RequestBody ResourceRequest r) { return service.updateMfr(id, r); }
    
    @Tag(name = OpenApiConfig.TAG_RES_MFR)
    @DeleteMapping("/manufacturers/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delMfr(@PathVariable UUID id) { return service.deleteMfr(id); }

    // ========================================================================
    // --- 06c. MARQUES (BRANDS) ---
    // ========================================================================
    @Tag(name = OpenApiConfig.TAG_RES_BRANDS)
    @PostMapping("/brands") @ResponseStatus(HttpStatus.CREATED)
    public Mono<?> createBrd(@RequestBody ResourceRequest r) { return service.createBrd(r); }
    
    @Tag(name = OpenApiConfig.TAG_RES_BRANDS)
    @GetMapping("/brands") public Flux<?> listBrds() { return service.getAllBrd(); }
    
    @Tag(name = OpenApiConfig.TAG_RES_BRANDS)
    @GetMapping("/brands/{id}") public Mono<?> getBrd(@PathVariable UUID id) { return service.getBrd(id); }
    
    @Tag(name = OpenApiConfig.TAG_RES_BRANDS)
    @PutMapping("/brands/{id}") public Mono<?> updateBrd(@PathVariable UUID id, @RequestBody ResourceRequest r) { return service.updateBrd(id, r); }
    
    @Tag(name = OpenApiConfig.TAG_RES_BRANDS)
    @DeleteMapping("/brands/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delBrd(@PathVariable UUID id) { return service.deleteBrd(id); }

    // ========================================================================
    // --- 06d. MODÈLES (VEHICLE MODELS) ---
    // ========================================================================
    @Tag(name = OpenApiConfig.TAG_RES_MODELS)
    @PostMapping("/models") @ResponseStatus(HttpStatus.CREATED)
    public Mono<?> createMod(@RequestBody ResourceRequest r) { return service.createMod(r); }
    
    @Tag(name = OpenApiConfig.TAG_RES_MODELS)
    @GetMapping("/models") public Flux<?> listMods() { return service.getAllMod(); }
    
    @Tag(name = OpenApiConfig.TAG_RES_MODELS)
    @GetMapping("/models/{id}") public Mono<?> getMod(@PathVariable UUID id) { return service.getMod(id); }
    
    @Tag(name = OpenApiConfig.TAG_RES_MODELS)
    @PutMapping("/models/{id}") public Mono<?> updateMod(@PathVariable UUID id, @RequestBody ResourceRequest r) { return service.updateMod(id, r); }
    
    @Tag(name = OpenApiConfig.TAG_RES_MODELS)
    @DeleteMapping("/models/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delMod(@PathVariable UUID id) { return service.deleteMod(id); }

    // ========================================================================
    // --- 06e. GABARITS (VEHICLE SIZES) ---
    // ========================================================================
    @Tag(name = OpenApiConfig.TAG_RES_SIZES)
    @PostMapping("/sizes") @ResponseStatus(HttpStatus.CREATED)
    public Mono<?> createSize(@RequestBody ResourceRequest r) { return service.createSize(r); }
    
    @Tag(name = OpenApiConfig.TAG_RES_SIZES)
    @GetMapping("/sizes") public Flux<?> listSizes() { return service.getAllSize(); }
    
    @Tag(name = OpenApiConfig.TAG_RES_SIZES)
    @GetMapping("/sizes/{id}") public Mono<?> getSize(@PathVariable UUID id) { return service.getSize(id); }
    
    @Tag(name = OpenApiConfig.TAG_RES_SIZES)
    @PutMapping("/sizes/{id}") public Mono<?> updateSize(@PathVariable UUID id, @RequestBody ResourceRequest r) { return service.updateSize(id, r); }
    
    @Tag(name = OpenApiConfig.TAG_RES_SIZES)
    @DeleteMapping("/sizes/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delSize(@PathVariable UUID id) { return service.deleteSize(id); }

    // ========================================================================
    // --- 06f. USAGES (USAGE TYPES) ---
    // ========================================================================
    @Tag(name = OpenApiConfig.TAG_RES_USAGES)
    @PostMapping("/usages") @ResponseStatus(HttpStatus.CREATED)
    public Mono<?> createUsage(@RequestBody ResourceRequest r) { return service.createUsage(r); }
    
    @Tag(name = OpenApiConfig.TAG_RES_USAGES)
    @GetMapping("/usages") public Flux<?> listUsages() { return service.getAllUsage(); }
    
    @Tag(name = OpenApiConfig.TAG_RES_USAGES)
    @GetMapping("/usages/{id}") public Mono<?> getUsage(@PathVariable UUID id) { return service.getUsage(id); }
    
    @Tag(name = OpenApiConfig.TAG_RES_USAGES)
    @PutMapping("/usages/{id}") public Mono<?> updateUsage(@PathVariable UUID id, @RequestBody ResourceRequest r) { return service.updateUsage(id, r); }
    
    @Tag(name = OpenApiConfig.TAG_RES_USAGES)
    @DeleteMapping("/usages/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delUsage(@PathVariable UUID id) { return service.deleteUsage(id); }

    // ========================================================================
    // --- 06g. CARBURANTS (FUEL TYPES) ---
    // ========================================================================
    @Tag(name = OpenApiConfig.TAG_RES_FUELS)
    @PostMapping("/fuels") @ResponseStatus(HttpStatus.CREATED)
    public Mono<?> createFuel(@RequestBody ResourceRequest r) { return service.createFuel(r); }
    
    @Tag(name = OpenApiConfig.TAG_RES_FUELS)
    @GetMapping("/fuels") public Flux<?> listFuels() { return service.getAllFuel(); }
    
    @Tag(name = OpenApiConfig.TAG_RES_FUELS)
    @GetMapping("/fuels/{id}") public Mono<?> getFuel(@PathVariable UUID id) { return service.getFuel(id); }
    
    @Tag(name = OpenApiConfig.TAG_RES_FUELS)
    @PutMapping("/fuels/{id}") public Mono<?> updateFuel(@PathVariable UUID id, @RequestBody ResourceRequest r) { return service.updateFuel(id, r); }
    
    @Tag(name = OpenApiConfig.TAG_RES_FUELS)
    @DeleteMapping("/fuels/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delFuel(@PathVariable UUID id) { return service.deleteFuel(id); }

    // ========================================================================
    // --- 06h. TRANSMISSIONS (TRANSMISSION TYPES) ---
    // ========================================================================
    @Tag(name = OpenApiConfig.TAG_RES_TRANS)
    @PostMapping("/transmissions") @ResponseStatus(HttpStatus.CREATED)
    public Mono<?> createTrans(@RequestBody ResourceRequest r) { return service.createTrans(r); }
    
    @Tag(name = OpenApiConfig.TAG_RES_TRANS)
    @GetMapping("/transmissions") public Flux<?> listTrans() { return service.getAllTrans(); }
    
    @Tag(name = OpenApiConfig.TAG_RES_TRANS)
    @GetMapping("/transmissions/{id}") public Mono<?> getTrans(@PathVariable UUID id) { return service.getTrans(id); }
    
    @Tag(name = OpenApiConfig.TAG_RES_TRANS)
    @PutMapping("/transmissions/{id}") public Mono<?> updateTrans(@PathVariable UUID id, @RequestBody ResourceRequest r) { return service.updateTrans(id, r); }
    
    @Tag(name = OpenApiConfig.TAG_RES_TRANS)
    @DeleteMapping("/transmissions/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delTrans(@PathVariable UUID id) { return service.deleteTrans(id); }

    // ========================================================================
    // --- 06i. COULEURS (VEHICLE COLORS) ---
    // ========================================================================
    @Tag(name = OpenApiConfig.TAG_RES_COLORS)
    @PostMapping("/colors") @ResponseStatus(HttpStatus.CREATED)
    public Mono<?> createColor(@RequestBody ResourceRequest r) { return service.createColor(r); }
    
    @Tag(name = OpenApiConfig.TAG_RES_COLORS)
    @GetMapping("/colors") public Flux<?> listColors() { return service.getAllColor(); }
    
    @Tag(name = OpenApiConfig.TAG_RES_COLORS)
    @GetMapping("/colors/{id}") public Mono<?> getColor(@PathVariable UUID id) { return service.getColor(id); }
    
    @Tag(name = OpenApiConfig.TAG_RES_COLORS)
    @PutMapping("/colors/{id}") public Mono<?> updateColor(@PathVariable UUID id, @RequestBody ResourceRequest r) { return service.updateColor(id, r); }
    
    @Tag(name = OpenApiConfig.TAG_RES_COLORS)
    @DeleteMapping("/colors/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delColor(@PathVariable UUID id) { return service.deleteColor(id); }
}