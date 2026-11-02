package com.yowyob.fleet.domain.model;

import com.yowyob.fleet.domain.exception.SubscriptionException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Règles de cohérence d'un plan tarifaire. Convention des limites : 0 = illimité.
 *
 * <ul>
 *   <li>Nom obligatoire ; limites positives ou nulles (nulle = illimité).</li>
 *   <li>Une flotte doit pouvoir contenir au moins un véhicule : avec des flottes et des
 *       véhicules limités, le nombre de véhicules est au moins égal au nombre de flottes.</li>
 *   <li>Prix mensuel positif ou nul (nul = plan gratuit) ; un plan gratuit n'a pas de prix
 *       annuel ; un prix annuel, s'il existe, est positif et ne dépasse pas 12 mensualités
 *       (l'annuel doit rester avantageux).</li>
 *   <li>Devise parmi celles gérées.</li>
 * </ul>
 */
public final class SubscriptionPlanRules {

    public static final Set<String> CURRENCIES = Set.of("XAF", "EUR", "USD");

    private SubscriptionPlanRules() {
    }

    public static void validate(String name, int maxFleets, int maxVehicles, int maxDrivers,
                                BigDecimal monthlyPrice, BigDecimal annualPrice, String currency) {
        if (name == null || name.isBlank()) {
            throw SubscriptionException.invalidPlan("Le nom du plan est obligatoire.");
        }
        if (maxFleets < 0 || maxVehicles < 0 || maxDrivers < 0) {
            throw SubscriptionException.invalidPlan(
                    "Les limites ne peuvent pas être négatives (0 signifie illimité).");
        }
        if (maxFleets > 0 && maxVehicles > 0 && maxVehicles < maxFleets) {
            throw SubscriptionException.invalidPlan(
                    "Le nombre de véhicules (" + maxVehicles + ") doit être au moins égal au nombre de flottes ("
                            + maxFleets + ") : chaque flotte doit pouvoir contenir un véhicule.");
        }
        BigDecimal monthly = monthlyPrice != null ? monthlyPrice : BigDecimal.ZERO;
        if (monthly.signum() < 0) {
            throw SubscriptionException.invalidPlan("Le prix mensuel ne peut pas être négatif.");
        }
        if (monthly.signum() == 0 && annualPrice != null && annualPrice.signum() != 0) {
            throw SubscriptionException.invalidPlan("Un plan gratuit ne peut pas avoir de prix annuel.");
        }
        if (annualPrice != null && monthly.signum() > 0) {
            if (annualPrice.signum() <= 0) {
                throw SubscriptionException.invalidPlan("Le prix annuel doit être positif.");
            }
            BigDecimal twelveMonths = monthly.multiply(BigDecimal.valueOf(12));
            if (annualPrice.compareTo(twelveMonths) > 0) {
                throw SubscriptionException.invalidPlan(
                        "Le prix annuel (" + annualPrice.stripTrailingZeros().toPlainString()
                                + ") ne doit pas dépasser 12 mensualités ("
                                + twelveMonths.stripTrailingZeros().toPlainString() + ").");
            }
        }
        if (currency != null && !CURRENCIES.contains(currency.trim().toUpperCase())) {
            throw SubscriptionException.invalidPlan("Devise non gérée : " + currency + " (XAF, EUR ou USD).");
        }
    }

    /** Dépassements de l'usage actuel par rapport aux limites d'un plan (vide si tout tient). */
    public static List<String> usageOverflows(int maxFleets, int maxVehicles, int maxDrivers,
                                              long fleets, long vehicles, long drivers) {
        List<String> overflows = new ArrayList<>();
        if (maxFleets > 0 && fleets > maxFleets) overflows.add(fleets + " flottes pour " + maxFleets + " autorisées");
        if (maxVehicles > 0 && vehicles > maxVehicles) overflows.add(vehicles + " véhicules pour " + maxVehicles + " autorisés");
        if (maxDrivers > 0 && drivers > maxDrivers) overflows.add(drivers + " chauffeurs pour " + maxDrivers + " autorisés");
        return overflows;
    }

    /** Montant dû pour un cycle ; null si le plan n'est pas proposé sur ce cycle. */
    public static BigDecimal priceFor(BigDecimal monthlyPrice, BigDecimal annualPrice, BillingCycle cycle) {
        BigDecimal monthly = monthlyPrice != null ? monthlyPrice : BigDecimal.ZERO;
        if (monthly.signum() == 0) return BigDecimal.ZERO;
        return cycle == BillingCycle.MONTHLY ? monthly : annualPrice;
    }
}
