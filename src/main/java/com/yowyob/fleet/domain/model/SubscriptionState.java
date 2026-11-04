package com.yowyob.fleet.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** État lisible d'un abonnement et revenu mensuel qu'il représente. */
public final class SubscriptionState {

    public static final int EXPIRING_WITHIN_DAYS = 15;
    public static final int MAX_EXTENSION_DAYS = 365;

    public enum State { PENDING, SUSPENDED, EXPIRED, GRACE, EXPIRING, ACTIVE, FREE }

    private SubscriptionState() {
    }

    public static State of(String status, boolean freePlan, LocalDate end, LocalDate today, int graceDays) {
        if ("PENDING".equals(status)) return State.PENDING;
        if ("SUSPENDED".equals(status)) return State.SUSPENDED;
        if ("EXPIRED".equals(status)) return State.EXPIRED;
        if (freePlan || end == null) return State.FREE;
        long days = ChronoUnit.DAYS.between(today, end);
        if (days < 0) return -days <= graceDays ? State.GRACE : State.EXPIRED;
        return days <= EXPIRING_WITHIN_DAYS ? State.EXPIRING : State.ACTIVE;
    }

    /** Une entreprise payante en service : compte dans le revenu récurrent. */
    public static boolean isPaying(State state) {
        return state == State.ACTIVE || state == State.EXPIRING || state == State.GRACE;
    }

    /** Équivalent mensuel : le prix annuel est lissé sur douze mois. */
    public static BigDecimal monthlyEquivalent(BigDecimal monthly, BigDecimal annual, String cycle) {
        if ("ANNUAL".equals(cycle) && annual != null) {
            return annual.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
        }
        return monthly != null ? monthly : BigDecimal.ZERO;
    }

    /** Une prolongation part de l'échéance, ou d'aujourd'hui si elle est déjà passée. */
    public static LocalDate extendedEnd(LocalDate end, LocalDate today, int days) {
        LocalDate from = end != null && end.isAfter(today) ? end : today;
        return from.plusDays(days);
    }

    /** Statut rendu par une réactivation : une échéance dépassée (grâce comprise) reste expirée. */
    public static String statusAfterReactivation(LocalDate end, LocalDate today, int graceDays) {
        return end != null && ChronoUnit.DAYS.between(end, today) > graceDays ? "EXPIRED" : "ACTIVE";
    }
}
