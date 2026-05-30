package com.yowyob.fleet.domain.model;

/**
 * Value Object représentant des coordonnées GPS.
 * Domaine pur — aucune dépendance Spring, JPA ou Jackson.
 *
 * Valide les bornes géographiques à la construction et fournit
 * un format WKT (Well-Known Text) pour la persistance PostGIS.
 */
public record Coordinates(double longitude, double latitude) {

    public Coordinates {
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException(
                "Longitude invalide : " + longitude + ". Doit être comprise entre -180 et 180."
            );
        }
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException(
                "Latitude invalide : " + latitude + ". Doit être comprise entre -90 et 90."
            );
        }
    }

    /**
     * Représentation WKT compatible PostGIS.
     * Exemple : POINT(3.8480 11.5021)
     */
    @Override
    public String toString() {
        return "POINT(" + longitude + " " + latitude + ")";
    }
}
