package com.yowyob.fleet.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubscriptionStateTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 2);

    @Test
    void explicitStatusesWin() {
        assertEquals(SubscriptionState.State.PENDING, SubscriptionState.of("PENDING", false, TODAY.plusDays(90), TODAY, 7));
        assertEquals(SubscriptionState.State.SUSPENDED, SubscriptionState.of("SUSPENDED", false, TODAY.plusDays(90), TODAY, 7));
        assertEquals(SubscriptionState.State.EXPIRED, SubscriptionState.of("EXPIRED", false, TODAY.plusDays(90), TODAY, 7));
    }

    @Test
    void freePlanHasNoDeadline() {
        assertEquals(SubscriptionState.State.FREE, SubscriptionState.of("ACTIVE", true, null, TODAY, 7));
        assertEquals(SubscriptionState.State.FREE, SubscriptionState.of("ACTIVE", false, null, TODAY, 7));
    }

    @Test
    void deadlineDrivesThePaidStates() {
        assertEquals(SubscriptionState.State.ACTIVE, SubscriptionState.of("ACTIVE", false, TODAY.plusDays(16), TODAY, 7));
        assertEquals(SubscriptionState.State.EXPIRING, SubscriptionState.of("ACTIVE", false, TODAY.plusDays(15), TODAY, 7));
        assertEquals(SubscriptionState.State.EXPIRING, SubscriptionState.of("ACTIVE", false, TODAY, TODAY, 7));
        assertEquals(SubscriptionState.State.GRACE, SubscriptionState.of("ACTIVE", false, TODAY.minusDays(7), TODAY, 7));
        assertEquals(SubscriptionState.State.EXPIRED, SubscriptionState.of("ACTIVE", false, TODAY.minusDays(8), TODAY, 7));
    }

    @Test
    void onlyRunningPaidSubscriptionsArePaying() {
        assertTrue(SubscriptionState.isPaying(SubscriptionState.State.ACTIVE));
        assertTrue(SubscriptionState.isPaying(SubscriptionState.State.EXPIRING));
        assertTrue(SubscriptionState.isPaying(SubscriptionState.State.GRACE));
        assertFalse(SubscriptionState.isPaying(SubscriptionState.State.FREE));
        assertFalse(SubscriptionState.isPaying(SubscriptionState.State.SUSPENDED));
        assertFalse(SubscriptionState.isPaying(SubscriptionState.State.EXPIRED));
    }

    @Test
    void annualPriceIsSpreadOverTwelveMonths() {
        BigDecimal monthly = BigDecimal.valueOf(15000);
        BigDecimal annual = BigDecimal.valueOf(150000);
        assertEquals(0, SubscriptionState.monthlyEquivalent(monthly, annual, "ANNUAL").compareTo(new BigDecimal("12500.00")));
        assertEquals(0, SubscriptionState.monthlyEquivalent(monthly, annual, "MONTHLY").compareTo(monthly));
        assertEquals(0, SubscriptionState.monthlyEquivalent(monthly, null, "ANNUAL").compareTo(monthly));
        assertEquals(0, SubscriptionState.monthlyEquivalent(null, null, null).signum());
    }

    @Test
    void extensionStartsFromTheLaterDate() {
        assertEquals(TODAY.plusDays(40), SubscriptionState.extendedEnd(TODAY.plusDays(10), TODAY, 30));
        assertEquals(TODAY.plusDays(30), SubscriptionState.extendedEnd(TODAY.minusDays(10), TODAY, 30));
        assertEquals(TODAY.plusDays(30), SubscriptionState.extendedEnd(null, TODAY, 30));
    }

    @Test
    void reactivationRespectsDeadlineAndGrace() {
        assertEquals("ACTIVE", SubscriptionState.statusAfterReactivation(TODAY.plusDays(5), TODAY, 7));
        assertEquals("ACTIVE", SubscriptionState.statusAfterReactivation(TODAY.minusDays(7), TODAY, 7));
        assertEquals("EXPIRED", SubscriptionState.statusAfterReactivation(TODAY.minusDays(8), TODAY, 7));
        assertEquals("ACTIVE", SubscriptionState.statusAfterReactivation(null, TODAY, 7));
    }
}
