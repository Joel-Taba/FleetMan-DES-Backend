package com.yowyob.fleet.domain.model;

import java.util.Arrays;
import java.util.Optional;

/** Fonctionnalités qu'un plan tarifaire peut inclure (clés de fleet.plan_features). */
public enum PlanFeature {
    TRIPS("Trajets"),
    DOCUMENTS("Documents"),
    SCHEDULES("Plannings"),
    ASSIGNMENTS("Affectations"),
    OPERATIONS("Opérations terrain"),
    KPI_REPORTS("KPI & Rapports"),
    GEOFENCING("Géofencing"),
    ALERTS("Alertes"),
    SCORING("Scoring conducteur"),
    API_ACCESS("Accès API");

    private final String label;

    PlanFeature(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static Optional<PlanFeature> fromKey(String key) {
        return Arrays.stream(values()).filter(f -> f.name().equals(key)).findFirst();
    }

    /** Libellé lisible d'une clé (la clé elle-même si elle est inconnue). */
    public static String labelOf(String key) {
        return fromKey(key).map(PlanFeature::label).orElse(key);
    }
}
