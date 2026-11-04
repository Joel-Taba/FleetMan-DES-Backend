package com.yowyob.fleet.domain.model;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Temps de travail d'un chauffeur : durée d'un poste, repos entre deux périodes de travail,
 * heures de la semaine (lundi → dimanche) et jours travaillés d'affilée. Les dépassements sont
 * des avertissements : le gestionnaire peut passer outre en le motivant.
 */
public final class WorkTimeRules {

    public record Limits(double maxShiftHours, double minRestHours, double maxWeeklyHours, int maxConsecutiveDays) {
        public static final Limits DEFAULT = new Limits(12, 11, 60, 6);
    }

    public record Interval(LocalDateTime start, LocalDateTime end) {
        double hours() {
            return Duration.between(start, end).toMinutes() / 60.0;
        }
    }

    public record Violation(String code, String message) {}

    private WorkTimeRules() {
    }

    public static List<Violation> check(Interval candidate, List<Interval> others, Limits limits) {
        List<Violation> violations = new ArrayList<>();
        double hours = candidate.hours();
        if (hours > limits.maxShiftHours()) {
            violations.add(new Violation("SHIFT_TOO_LONG",
                    "Poste de " + fmt(hours) + " h : la durée maximale est de " + fmt(limits.maxShiftHours()) + " h."));
        }

        LocalDateTime previousEnd = others.stream()
                .map(Interval::end)
                .filter(end -> !end.isAfter(candidate.start()))
                .max(LocalDateTime::compareTo).orElse(null);
        LocalDateTime nextStart = others.stream()
                .map(Interval::start)
                .filter(start -> !start.isBefore(candidate.end()))
                .min(LocalDateTime::compareTo).orElse(null);
        if (previousEnd != null) {
            double rest = Duration.between(previousEnd, candidate.start()).toMinutes() / 60.0;
            if (rest < limits.minRestHours()) {
                violations.add(new Violation("REST_TOO_SHORT",
                        "Repos de " + fmt(rest) + " h seulement depuis la période précédente (minimum " + fmt(limits.minRestHours()) + " h)."));
            }
        }
        if (nextStart != null) {
            double rest = Duration.between(candidate.end(), nextStart).toMinutes() / 60.0;
            if (rest < limits.minRestHours()) {
                violations.add(new Violation("REST_TOO_SHORT",
                        "Repos de " + fmt(rest) + " h seulement avant la période suivante (minimum " + fmt(limits.minRestHours()) + " h)."));
            }
        }

        LocalDateTime weekStart = candidate.start().toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();
        LocalDateTime weekEnd = weekStart.plusDays(7);
        double weekly = overlapHours(candidate, weekStart, weekEnd)
                + others.stream().mapToDouble(i -> overlapHours(i, weekStart, weekEnd)).sum();
        if (weekly > limits.maxWeeklyHours()) {
            violations.add(new Violation("WEEKLY_HOURS",
                    fmt(weekly) + " h de travail sur la semaine : le plafond est de " + fmt(limits.maxWeeklyHours()) + " h."));
        }

        Set<LocalDate> days = new TreeSet<>();
        days.add(candidate.start().toLocalDate());
        others.forEach(i -> days.add(i.start().toLocalDate()));
        LocalDate day = candidate.start().toLocalDate();
        int run = 1;
        for (LocalDate d = day.minusDays(1); days.contains(d); d = d.minusDays(1)) run++;
        for (LocalDate d = day.plusDays(1); days.contains(d); d = d.plusDays(1)) run++;
        if (run > limits.maxConsecutiveDays()) {
            violations.add(new Violation("CONSECUTIVE_DAYS",
                    run + " jours de travail d'affilée : au plus " + limits.maxConsecutiveDays() + " jours avant un jour de repos."));
        }
        return violations;
    }

    private static double overlapHours(Interval i, LocalDateTime from, LocalDateTime to) {
        LocalDateTime start = i.start().isAfter(from) ? i.start() : from;
        LocalDateTime end = i.end().isBefore(to) ? i.end() : to;
        return end.isAfter(start) ? Duration.between(start, end).toMinutes() / 60.0 : 0;
    }

    static String fmt(double hours) {
        double rounded = Math.round(hours * 10) / 10.0;
        return rounded == Math.floor(rounded) ? String.valueOf((long) rounded) : String.valueOf(rounded).replace('.', ',');
    }
}
