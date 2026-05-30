// package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

// import com.yowyob.fleet.domain.ports.in.AdminStatsUseCase;
// import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.GlobalStatsResponse;
// import io.swagger.v3.oas.annotations.Operation;
// import io.swagger.v3.oas.annotations.security.SecurityRequirement;
// import io.swagger.v3.oas.annotations.tags.Tag;
// import lombok.RequiredArgsConstructor;
// import org.springframework.security.access.prepost.PreAuthorize;
// import org.springframework.web.bind.annotation.GetMapping;
// import org.springframework.web.bind.annotation.RequestMapping;
// import org.springframework.web.bind.annotation.RestController;
// import reactor.core.publisher.Mono;

// @RestController
// @RequestMapping("/api/v1/admin/stats")
// @RequiredArgsConstructor
// // @Tag(name = "05. Admin", description = "Statistiques et Supervision") // On le regroupe avec Monitoring
// // @SecurityRequirement(name = "bearerAuth")
// // public class AdminStatsController {

// //     private final AdminStatsUseCase adminStatsUseCase;

// //     @GetMapping
// //     @PreAuthorize("hasRole('FLEET_ADMIN')")
// //     @Operation(summary = "Statistiques Globales (Admin)", description = "Retourne le nombre total d'entités dans le système.")
// //     public Mono<GlobalStatsResponse> getGlobalStats() {
// //         return adminStatsUseCase.getGlobalStats();
//     }
// }
package com.yowyob.fleet.infrastructure.adapters.inbound.rest;
public class AdminStatsController {
    
}