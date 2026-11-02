package com.yowyob.fleet.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BudgetTrackingTest {

    private static final LocalDate MONTH = LocalDate.of(2026, 11, 1);
    private final UUID fleet = UUID.randomUUID(), v1 = UUID.randomUUID(), v2 = UUID.randomUUID(), driver = UUID.randomUUID();

    private BudgetTracking.ExpenseRow expense(UUID vehicle, String type, String status, long amount, UUID driverId) {
        return new BudgetTracking.ExpenseRow(UUID.randomUUID(), fleet, vehicle, "PL", driverId, driverId != null ? "Claire" : null,
                type, status, "MANUAL", BigDecimal.valueOf(amount), LocalDateTime.of(2026, 11, 2, 10, 0), "x", false);
    }

    @Test
    void projection_followsTheCurrentPace_andStopsAtMonthEnd() {
        assertEquals(10, BudgetTracking.daysElapsed(MONTH, LocalDate.of(2026, 11, 10)));
        assertEquals(30, BudgetTracking.daysElapsed(MONTH, LocalDate.of(2027, 1, 5)));
        assertEquals(0, BudgetTracking.daysElapsed(MONTH, LocalDate.of(2026, 10, 5)));
        assertEquals(BigDecimal.valueOf(3000), BudgetTracking.project(BigDecimal.valueOf(1000), 10, 30));
        assertNull(BudgetTracking.project(BigDecimal.TEN, 0, 30));
    }

    @Test
    void levels_addUp_andOnlyApprovedExpensesAreConsumed() {
        var overview = BudgetTracking.build(MONTH, LocalDate.of(2026, 11, 10),
                List.of(new BudgetTracking.FleetRow(fleet, "Douala", "Jean")),
                List.of(new BudgetTracking.VehicleRow(v1, fleet, "A"), new BudgetTracking.VehicleRow(v2, fleet, "B")),
                List.of(new BudgetTracking.BudgetRow(UUID.randomUUID(), "FLEET", fleet, BigDecimal.valueOf(10000), "NORMAL"),
                        new BudgetTracking.BudgetRow(UUID.randomUUID(), "VEHICLE", v1, BigDecimal.valueOf(4000), "NORMAL")),
                List.of(expense(v1, "FUEL", "APPROVED", 1000, driver), expense(v1, "TOLL", "PENDING", 500, driver),
                        expense(v2, "FUEL", "APPROVED", 3000, null), expense(v2, "FINE", "REJECTED", 700, null)),
                Map.of(v1, BigDecimal.valueOf(100)), List.of(), Map.of("FUEL", "Carburant"));

        assertEquals(BigDecimal.valueOf(4000), overview.totals().consumed());
        assertEquals(BigDecimal.valueOf(500), overview.totals().pending());
        assertEquals(BigDecimal.valueOf(6000), overview.totals().remaining());
        assertEquals(40.0, overview.totals().rate());
        assertEquals(1, overview.rejectedCount());

        var f = overview.fleets().get(0);
        assertEquals(BigDecimal.valueOf(4000), f.allocatedToVehicles());
        assertEquals(BigDecimal.valueOf(6000), f.unallocated());
        // Le plus gros consommateur d'abord ; un véhicule sans budget garde son consommé, sans taux.
        assertEquals(v2, f.vehicles().get(0).vehicleId());
        assertNull(f.vehicles().get(0).usage().rate());
        assertEquals(BigDecimal.valueOf(10), f.vehicles().get(1).costPerKm());

        assertEquals("Carburant", overview.byType().get(0).label());
        assertEquals(100.0, overview.byType().get(0).share());
        assertEquals(BigDecimal.valueOf(3000), overview.byDriver().get(0).amount());
        assertNull(overview.byDriver().get(0).label());
    }

    @Test
    void noEnvelope_meansNoRateAndNoRemaining() {
        var overview = BudgetTracking.build(MONTH, LocalDate.of(2026, 11, 10),
                List.of(new BudgetTracking.FleetRow(fleet, "Douala", null)), List.of(), List.of(),
                List.of(expense(v1, "FUEL", "APPROVED", 1000, null)), Map.of(), List.of(), Map.of());
        assertNull(overview.totals().budget());
        assertNull(overview.totals().rate());
        assertNull(overview.fleets().get(0).unallocated());
    }
}
