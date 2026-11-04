package com.yowyob.fleet.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkTimeRulesTest {

    private static final WorkTimeRules.Limits LIMITS = WorkTimeRules.Limits.DEFAULT;
    /** Lundi 2 novembre 2026. */
    private static final LocalDateTime MONDAY = LocalDateTime.of(2026, 11, 2, 0, 0);

    private static WorkTimeRules.Interval shift(int day, int startHour, int endHour) {
        LocalDateTime start = MONDAY.plusDays(day).withHour(startHour);
        LocalDateTime end = endHour > startHour ? MONDAY.plusDays(day).withHour(endHour) : MONDAY.plusDays(day + 1).withHour(endHour);
        return new WorkTimeRules.Interval(start, end);
    }

    private static List<String> codes(List<WorkTimeRules.Violation> v) {
        return v.stream().map(WorkTimeRules.Violation::code).toList();
    }

    @Test
    void ordinaryWeekIsFine() {
        List<WorkTimeRules.Interval> week = new ArrayList<>();
        for (int d = 0; d < 4; d++) week.add(shift(d, 7, 16));
        assertTrue(WorkTimeRules.check(shift(4, 7, 16), week, LIMITS).isEmpty());
    }

    @Test
    void shiftLongerThanTwelveHours() {
        assertEquals(List.of("SHIFT_TOO_LONG"), codes(WorkTimeRules.check(shift(0, 6, 19), List.of(), LIMITS)));
        assertTrue(WorkTimeRules.check(shift(0, 6, 18), List.of(), LIMITS).isEmpty());
    }

    @Test
    void restTooShortBeforeAndAfter() {
        // Fin à 22 h, reprise à 6 h : 8 h de repos.
        List<WorkTimeRules.Violation> before = WorkTimeRules.check(shift(1, 6, 14), List.of(shift(0, 14, 22)), LIMITS);
        assertEquals(List.of("REST_TOO_SHORT"), codes(before));
        assertTrue(before.get(0).message().contains("8 h"));
        List<WorkTimeRules.Violation> after = WorkTimeRules.check(shift(0, 14, 22), List.of(shift(1, 6, 14)), LIMITS);
        assertEquals(List.of("REST_TOO_SHORT"), codes(after));
        assertTrue(WorkTimeRules.check(shift(1, 9, 17), List.of(shift(0, 14, 22)), LIMITS).isEmpty());
    }

    @Test
    void nightShiftCountsAcrossMidnight() {
        assertEquals(8.0, shift(0, 22, 6).hours());
        assertTrue(WorkTimeRules.check(shift(0, 22, 6), List.of(), LIMITS).isEmpty());
    }

    @Test
    void weeklyCapCountsOnlyTheWeek() {
        List<WorkTimeRules.Interval> week = new ArrayList<>();
        for (int d = 0; d < 5; d++) week.add(shift(d, 6, 18));
        // 5 × 12 h = 60 h, un poste de plus dépasse le plafond.
        assertTrue(codes(WorkTimeRules.check(shift(5, 8, 12), week, LIMITS)).contains("WEEKLY_HOURS"));
        // Le lundi suivant compte dans une autre semaine.
        assertTrue(!codes(WorkTimeRules.check(shift(7, 8, 12), week, LIMITS)).contains("WEEKLY_HOURS"));
    }

    @Test
    void seventhConsecutiveDayIsFlagged() {
        List<WorkTimeRules.Interval> days = new ArrayList<>();
        for (int d = 0; d < 6; d++) days.add(shift(d, 8, 12));
        assertEquals(List.of("CONSECUTIVE_DAYS"), codes(WorkTimeRules.check(shift(6, 8, 12), days, LIMITS)));
        // Un jour de repos casse la série.
        days.remove(3);
        assertTrue(WorkTimeRules.check(shift(6, 8, 12), days, LIMITS).isEmpty());
    }

    @Test
    void companyLimitsAreApplied() {
        WorkTimeRules.Limits strict = new WorkTimeRules.Limits(8, 12, 40, 5);
        assertEquals(List.of("SHIFT_TOO_LONG"), codes(WorkTimeRules.check(shift(0, 7, 16), List.of(), strict)));
    }

    @Test
    void hoursAreFormattedReadably() {
        assertEquals("8", WorkTimeRules.fmt(8.0));
        assertEquals("7,5", WorkTimeRules.fmt(7.5));
    }
}
