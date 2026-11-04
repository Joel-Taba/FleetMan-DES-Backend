package com.yowyob.fleet.domain.model;

import com.yowyob.fleet.domain.exception.TripException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TripStopRulesTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 4, 8, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 11, 4, 18, 0);

    private static TripStopRules.StopInput stop(String type, String location, LocalDateTime planned) {
        return new TripStopRules.StopInput(type, location, null, null, null, null, null, planned);
    }

    @Test
    void planNeedsBetweenOneAndTwentyStops() {
        assertThrows(TripException.class, () -> TripStopRules.validatePlan(List.of(), START, END));
        List<TripStopRules.StopInput> many = new ArrayList<>();
        for (int i = 0; i < 21; i++) many.add(stop("DELIVERY", "Arrêt " + i, null));
        assertThrows(TripException.class, () -> TripStopRules.validatePlan(many, START, END));
        assertDoesNotThrow(() -> TripStopRules.validatePlan(many.subList(0, 20), START, END));
    }

    @Test
    void eachStopNeedsKnownTypeAndLocation() {
        assertThrows(TripException.class, () -> TripStopRules.validatePlan(List.of(stop("TRANSIT", "Douala", null)), START, END));
        assertThrows(TripException.class, () -> TripStopRules.validatePlan(List.of(stop("DELIVERY", " ", null)), START, END));
        assertThrows(TripException.class, () -> TripStopRules.validateStop(
                new TripStopRules.StopInput("PICKUP", "Douala", null, null, "Awa", "abc", null, null)));
    }

    @Test
    void plannedArrivalsStayInSlotAndInOrder() {
        assertThrows(TripException.class, () -> TripStopRules.validatePlan(
                List.of(stop("DELIVERY", "A", START.minusMinutes(5))), START, END));
        assertThrows(TripException.class, () -> TripStopRules.validatePlan(
                List.of(stop("DELIVERY", "A", END.plusMinutes(1))), START, END));
        assertThrows(TripException.class, () -> TripStopRules.validatePlan(List.of(
                stop("DELIVERY", "A", START.plusHours(3)), stop("DELIVERY", "B", START.plusHours(2))), START, END));
        assertDoesNotThrow(() -> TripStopRules.validatePlan(List.of(
                stop("DELIVERY", "A", START.plusHours(2)), stop("PICKUP", "B", null),
                stop("DELIVERY", "C", START.plusHours(5))), START, END));
    }

    @Test
    void deliveredQuantityIsBoundedAndShortageNeedsReason() {
        assertThrows(TripException.class, () -> TripStopRules.validateHandled("DELIVERY", 10, 11, null));
        assertThrows(TripException.class, () -> TripStopRules.validateHandled("DELIVERY", 10, -1, null));
        assertThrows(TripException.class, () -> TripStopRules.validateHandled("DELIVERY", 10, 8, null));
        assertThrows(TripException.class, () -> TripStopRules.validateHandled("DELIVERY", 10, 8, "LOST"));
        assertDoesNotThrow(() -> TripStopRules.validateHandled("DELIVERY", 10, 8, "DAMAGED"));
        assertDoesNotThrow(() -> TripStopRules.validateHandled("DELIVERY", 10, 10, null));
    }

    @Test
    void pickupMayCollectMoreThanPlanned() {
        assertDoesNotThrow(() -> TripStopRules.validateHandled("PICKUP", 5, 7, null));
        assertThrows(TripException.class, () -> TripStopRules.validateHandled("PICKUP", 5, 3, null));
    }

    @Test
    void tourIsDoneOnlyWhenEveryStopIsClosed() {
        assertTrue(TripStopRules.allTerminal(List.of("COMPLETED", "FAILED", "SKIPPED")));
        assertFalse(TripStopRules.allTerminal(List.of("COMPLETED", "ARRIVED")));
        assertFalse(TripStopRules.allTerminal(List.of()));
        assertTrue(TripStopRules.isServable("ARRIVED"));
        assertFalse(TripStopRules.isServable("FAILED"));
    }

    @Test
    void punctualityUsesFifteenMinuteTolerance() {
        LocalDateTime planned = START.plusHours(2);
        assertEquals("ON_TIME", TripStopRules.punctuality(planned, planned.plusMinutes(15)));
        assertEquals("LATE", TripStopRules.punctuality(planned, planned.plusMinutes(16)));
        assertEquals("EARLY", TripStopRules.punctuality(planned, planned.minusMinutes(16)));
        assertNull(TripStopRules.punctuality(null, planned));
        assertEquals(30L, TripStopRules.delayMinutes(planned, planned.plusMinutes(30)));
    }

    @Test
    void declaredTimeIsClampedBetweenDepartureAndNow() {
        LocalDateTime now = START.plusHours(4);
        assertEquals(now, TripStopRules.boundedTime(now.plusHours(1), START, now));
        assertEquals(START, TripStopRules.boundedTime(START.minusHours(1), START, now));
        assertEquals(START.plusHours(1), TripStopRules.boundedTime(START.plusHours(1), START, now));
    }
}
