package com.yowyob.fleet.domain.model;

import java.time.LocalDate;

/** Cycle de facturation d'un abonnement : sa durée dépend du cycle choisi. */
public enum BillingCycle {
    MONTHLY,
    ANNUAL;

    /** Fin de période d'un abonnement démarrant à {@code start}. */
    public LocalDate periodEnd(LocalDate start) {
        return this == MONTHLY ? start.plusMonths(1) : start.plusYears(1);
    }

    public static BillingCycle parseOrDefault(String value, BillingCycle fallback) {
        if (value == null || value.isBlank()) return fallback;
        return BillingCycle.valueOf(value.trim().toUpperCase());
    }
}
