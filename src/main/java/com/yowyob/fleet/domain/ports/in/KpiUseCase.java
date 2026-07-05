package com.yowyob.fleet.domain.ports.in;

import com.yowyob.fleet.domain.model.KpiSnapshot;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Port entrant — Cas d'utilisation pour les KPIs et rapports.
 */
public interface KpiUseCase {

    // ── Lecture des KPIs ──────────────────────────────────────────────────────

    /** KPIs de la flotte pour la période la plus récente. */
    Mono<KpiSnapshot> getLatestFleetKpi(UUID fleetId,
                                         KpiSnapshot.PeriodType periodType);

    /** KPIs d'un véhicule pour la période la plus récente. */
    Mono<KpiSnapshot> getLatestVehicleKpi(UUID vehicleId,
                                           KpiSnapshot.PeriodType periodType);

    /** KPIs d'un conducteur pour la période la plus récente. */
    Mono<KpiSnapshot> getLatestDriverKpi(UUID driverId,
                                          KpiSnapshot.PeriodType periodType);

    /** Historique des KPIs d'une flotte sur une plage de dates. */
    Flux<KpiSnapshot> getFleetKpiHistory(UUID fleetId,
                                          KpiSnapshot.PeriodType periodType,
                                          LocalDate from,
                                          LocalDate to);

    /** Top N véhicules par kilométrage pour une flotte. */
    Flux<KpiSnapshot> getTopVehiclesByKm(UUID fleetId,
                                          KpiSnapshot.PeriodType periodType,
                                          int limit);

    /** Top N conducteurs par score pour une flotte. */
    Flux<KpiSnapshot> getTopDriversByScore(UUID fleetId,
                                            KpiSnapshot.PeriodType periodType,
                                            int limit);

    /** Comparaison de deux périodes pour une flotte. */
    Mono<KpiComparisonDto> compareFleetKpi(UUID fleetId,
                                            KpiSnapshot.PeriodType periodType,
                                            LocalDate period1Start,
                                            LocalDate period2Start);

    // ── Calcul à la demande ───────────────────────────────────────────────────

    /** Déclenche le calcul immédiat des KPIs pour une flotte (admin). */
    Mono<KpiSnapshot> recalculateFleetKpi(UUID fleetId,
                                           KpiSnapshot.PeriodType periodType,
                                           LocalDate periodStart);

    // ── Record de comparaison ─────────────────────────────────────────────────

    record KpiComparisonDto(
            KpiSnapshot period1,
            KpiSnapshot period2,
            BigDecimalDelta kmDelta,
            BigDecimalDelta costPerKmDelta,
            BigDecimalDelta fuelCostDelta,
            BigDecimalDelta incidentRateDelta,
            BigDecimalDelta availabilityRateDelta
    ) {}

    record BigDecimalDelta(
            java.math.BigDecimal value1,
            java.math.BigDecimal value2,
            java.math.BigDecimal absoluteDelta,
            double percentDelta
    ) {
        public static BigDecimalDelta of(java.math.BigDecimal v1, java.math.BigDecimal v2) {
            if (v1 == null && v2 == null) return new BigDecimalDelta(null, null, null, 0);
            java.math.BigDecimal safe1 = v1 != null ? v1 : java.math.BigDecimal.ZERO;
            java.math.BigDecimal safe2 = v2 != null ? v2 : java.math.BigDecimal.ZERO;
            java.math.BigDecimal delta = safe2.subtract(safe1);
            double pct = safe1.compareTo(java.math.BigDecimal.ZERO) == 0
                    ? 0
                    : delta.doubleValue() / safe1.doubleValue() * 100.0;
            return new BigDecimalDelta(v1, v2, delta, pct);
        }
    }
}
