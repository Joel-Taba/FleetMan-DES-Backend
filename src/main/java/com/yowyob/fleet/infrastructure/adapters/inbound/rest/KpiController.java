package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.domain.model.KpiSnapshot;
import com.yowyob.fleet.domain.ports.in.KpiUseCase;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.shared.util.CsvExportUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/kpis")
@RequiredArgsConstructor
@Tag(name = "20. KPIs & Rapports")
@SecurityRequirement(name = "bearerAuth")
public class KpiController {

    private final KpiUseCase kpiUseCase;

    private UUID getUserId(Authentication auth) {
        return ((AuthPort.UserDetail) auth.getPrincipal()).id();
    }

    // ── KPIs Flotte ───────────────────────────────────────────────────────────

    @GetMapping("/fleet/{fleetId}")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "KPIs d'une flotte",
               description = "Retourne les KPIs les plus récents pour une flotte. "
                       + "Si aucun snapshot n'existe, déclenche un calcul à la demande.")
    public Mono<KpiSnapshot> getFleetKpi(
            @PathVariable UUID fleetId,
            @Parameter(description = "Granularité")
            @RequestParam(defaultValue = "MONTHLY") String period) {

        return kpiUseCase.getLatestFleetKpi(fleetId,
                KpiSnapshot.PeriodType.valueOf(period.toUpperCase()));
    }

    @GetMapping("/fleet/{fleetId}/history")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Historique des KPIs d'une flotte",
               description = "Retourne l'évolution des KPIs sur une plage de dates.")
    public Mono<List<KpiSnapshot>> getFleetKpiHistory(
            @PathVariable UUID fleetId,
            @RequestParam(defaultValue = "MONTHLY") String period,
            @Parameter(example = "2026-01-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(example = "2026-06-30")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        return kpiUseCase.getFleetKpiHistory(fleetId,
                KpiSnapshot.PeriodType.valueOf(period.toUpperCase()), from, to)
                .collectList();
    }

    @GetMapping("/fleet/{fleetId}/compare")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Comparer deux périodes",
               description = "Compare les KPIs de deux périodes identiques. "
                       + "Retourne les deltas absolus et en pourcentage.")
    public Mono<KpiUseCase.KpiComparisonDto> compareFleetKpi(
            @PathVariable UUID fleetId,
            @RequestParam(defaultValue = "MONTHLY") String period,
            @Parameter(description = "Début période 1", example = "2026-04-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate period1Start,
            @Parameter(description = "Début période 2", example = "2026-05-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate period2Start) {

        return kpiUseCase.compareFleetKpi(fleetId,
                KpiSnapshot.PeriodType.valueOf(period.toUpperCase()),
                period1Start, period2Start);
    }

    @GetMapping("/fleet/{fleetId}/top-vehicles")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Top véhicules par kilométrage",
               description = "Retourne les N véhicules les plus actifs de la flotte.")
    public Mono<List<KpiSnapshot>> getTopVehicles(
            @PathVariable UUID fleetId,
            @RequestParam(defaultValue = "MONTHLY") String period,
            @Parameter(description = "Nombre de résultats", example = "5")
            @RequestParam(defaultValue = "5") int limit) {

        return kpiUseCase.getTopVehiclesByKm(fleetId,
                KpiSnapshot.PeriodType.valueOf(period.toUpperCase()), limit)
                .collectList();
    }

    @GetMapping("/fleet/{fleetId}/top-drivers")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Top conducteurs par score",
               description = "Retourne les N meilleurs conducteurs selon leur score de conduite.")
    public Mono<List<KpiSnapshot>> getTopDrivers(
            @PathVariable UUID fleetId,
            @RequestParam(defaultValue = "MONTHLY") String period,
            @RequestParam(defaultValue = "5") int limit) {

        return kpiUseCase.getTopDriversByScore(fleetId,
                KpiSnapshot.PeriodType.valueOf(period.toUpperCase()), limit)
                .collectList();
    }

    // ── KPIs Véhicule / Conducteur ────────────────────────────────────────────

    @GetMapping("/vehicle/{vehicleId}")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "KPIs d'un véhicule")
    public Mono<KpiSnapshot> getVehicleKpi(
            @PathVariable UUID vehicleId,
            @RequestParam(defaultValue = "MONTHLY") String period) {

        return kpiUseCase.getLatestVehicleKpi(vehicleId,
                KpiSnapshot.PeriodType.valueOf(period.toUpperCase()));
    }

    @GetMapping("/driver/{driverId}")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "KPIs d'un conducteur")
    public Mono<KpiSnapshot> getDriverKpi(
            @PathVariable UUID driverId,
            @RequestParam(defaultValue = "MONTHLY") String period) {

        return kpiUseCase.getLatestDriverKpi(driverId,
                KpiSnapshot.PeriodType.valueOf(period.toUpperCase()));
    }

    // ── Export CSV ────────────────────────────────────────────────────────────

    @GetMapping(value = "/fleet/{fleetId}/export", produces = "text/csv")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    @Operation(summary = "Exporter les KPIs en CSV",
               description = "Génère un fichier CSV de l'historique des KPIs. "
                       + "Compatible Microsoft Excel (BOM UTF-8).")
    public Mono<ResponseEntity<String>> exportCsv(
            @PathVariable UUID fleetId,
            @RequestParam(defaultValue = "MONTHLY") String period,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        return CsvExportUtil.export(
                kpiUseCase.getFleetKpiHistory(fleetId,
                        KpiSnapshot.PeriodType.valueOf(period.toUpperCase()), from, to),
                List.of("Période", "Début", "Fin", "Km totaux", "Trajets",
                        "Coût/km", "Carburant (L)", "Coût carburant",
                        "Coût maintenance", "Coût incidents",
                        "Nb incidents", "Taux incidents/1000km",
                        "Score moyen conducteurs", "Conformité docs (%)"),
                s -> List.of(
                        s.periodType().name(),
                        s.periodStart() != null ? s.periodStart().toString() : "",
                        s.periodEnd()   != null ? s.periodEnd().toString()   : "",
                        s.totalKm()     != null ? s.totalKm().toPlainString()     : "0",
                        s.totalTrips()  != null ? s.totalTrips().toString()       : "0",
                        s.costPerKm()   != null ? s.costPerKm().toPlainString()   : "",
                        s.totalFuelLiters() != null ? s.totalFuelLiters().toPlainString() : "0",
                        s.totalFuelCost()   != null ? s.totalFuelCost().toPlainString()   : "0",
                        s.totalMaintenanceCost() != null ? s.totalMaintenanceCost().toPlainString() : "0",
                        s.totalIncidentCost()    != null ? s.totalIncidentCost().toPlainString()    : "0",
                        s.totalIncidents() != null ? s.totalIncidents().toString() : "0",
                        s.incidentRate()   != null ? s.incidentRate().toPlainString()   : "",
                        s.avgDriverScore() != null ? s.avgDriverScore().toPlainString() : "",
                        s.docComplianceRate() != null ? s.docComplianceRate().toPlainString() : ""
                )
        ).map(csv -> ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"kpis-" + fleetId + "-" + period + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv));
    }

    // ── Recalcul admin ────────────────────────────────────────────────────────

    @PostMapping("/fleet/{fleetId}/recalculate")
    @PreAuthorize("hasRole('FLEET_ADMIN')")
    @Operation(summary = "Recalculer les KPIs (Admin)",
               description = "Déclenche un recalcul immédiat des KPIs pour une flotte. "
                       + "Réservé aux administrateurs.")
    public Mono<KpiSnapshot> recalculate(
            @PathVariable UUID fleetId,
            @RequestParam(defaultValue = "MONTHLY") String period,
            @Parameter(example = "2026-05-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart) {

        return kpiUseCase.recalculateFleetKpi(fleetId,
                KpiSnapshot.PeriodType.valueOf(period.toUpperCase()), periodStart);
    }
}
