package com.yowyob.fleet.domain.model;

import com.yowyob.fleet.domain.exception.SubscriptionException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubscriptionPlanRulesTest {

    private static BigDecimal xaf(long v) {
        return BigDecimal.valueOf(v);
    }

    private static void assertInvalid(Runnable r) {
        SubscriptionException e = assertThrows(SubscriptionException.class, r::run);
        assertEquals("SUB_007", e.getBusinessCode());
    }

    @Test
    void catalogPlans_areValid() {
        assertDoesNotThrow(() -> SubscriptionPlanRules.validate("Découverte", 1, 3, 5, xaf(0), null, "XAF"));
        assertDoesNotThrow(() -> SubscriptionPlanRules.validate("Essentiel", 2, 15, 30, xaf(15000), xaf(150000), "XAF"));
        assertDoesNotThrow(() -> SubscriptionPlanRules.validate("Entreprise", 0, 0, 0, xaf(120000), xaf(1200000), "XAF"));
    }

    @Test
    void moreFleetsThanVehicles_isRejected() {
        // Ancien plan « Pro » : 120 flottes pour 20 véhicules.
        assertInvalid(() -> SubscriptionPlanRules.validate("Pro", 120, 20, 50, xaf(75000), null, "XAF"));
    }

    @Test
    void annualPriceAboveTwelveMonths_isRejected() {
        assertInvalid(() -> SubscriptionPlanRules.validate("X", 1, 5, 5, xaf(10000), xaf(130000), "XAF"));
    }

    @Test
    void freePlanWithAnnualPrice_isRejected() {
        assertInvalid(() -> SubscriptionPlanRules.validate("X", 1, 5, 5, xaf(0), xaf(3000), "XAF"));
    }

    @Test
    void negativeValues_andUnknownCurrency_areRejected() {
        assertInvalid(() -> SubscriptionPlanRules.validate("X", -1, 5, 5, xaf(0), null, "XAF"));
        assertInvalid(() -> SubscriptionPlanRules.validate("X", 1, 5, 5, xaf(-1), null, "XAF"));
        assertInvalid(() -> SubscriptionPlanRules.validate("X", 1, 5, 5, xaf(1000), null, "GBP"));
        assertInvalid(() -> SubscriptionPlanRules.validate(" ", 1, 5, 5, xaf(0), null, "XAF"));
    }

    @Test
    void usageOverflows_listsEachExceededLimit_andIgnoresUnlimited() {
        assertEquals(2, SubscriptionPlanRules.usageOverflows(1, 3, 5, 2, 10, 3).size());
        assertTrue(SubscriptionPlanRules.usageOverflows(0, 0, 0, 50, 500, 900).isEmpty());
    }

    @Test
    void priceFor_dependsOnCycle_andFreePlanCostsNothing() {
        assertEquals(xaf(15000), SubscriptionPlanRules.priceFor(xaf(15000), xaf(150000), BillingCycle.MONTHLY));
        assertEquals(xaf(150000), SubscriptionPlanRules.priceFor(xaf(15000), xaf(150000), BillingCycle.ANNUAL));
        assertNull(SubscriptionPlanRules.priceFor(xaf(15000), null, BillingCycle.ANNUAL));
        assertEquals(0, SubscriptionPlanRules.priceFor(xaf(0), null, BillingCycle.ANNUAL).signum());
    }

    @Test
    void billingCycle_periodEnd() {
        var start = java.time.LocalDate.of(2026, 1, 31);
        assertEquals(java.time.LocalDate.of(2026, 2, 28), BillingCycle.MONTHLY.periodEnd(start));
        assertEquals(java.time.LocalDate.of(2027, 1, 31), BillingCycle.ANNUAL.periodEnd(start));
    }
}
