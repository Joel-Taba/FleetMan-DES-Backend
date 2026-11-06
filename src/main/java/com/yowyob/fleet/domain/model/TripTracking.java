package com.yowyob.fleet.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Suivi GPS en direct d'un trajet : règles de validation des points envoyés par le téléphone du
 * chauffeur et vues exposées au gestionnaire (dernière position, tracé récent).
 */
public final class TripTracking {

    /** Écart minimal entre deux points enregistrés pour un même trajet (les points plus rapprochés sont ignorés). */
    public static final Duration MIN_INTERVAL = Duration.ofSeconds(15);
    /** Nombre de points conservés par trajet (les plus anciens sont purgés à l'écriture). */
    public static final int HISTORY_LIMIT = 200;
    /** Points du tracé renvoyés par défaut pour un trajet. */
    public static final int DEFAULT_TRACK = 200;
    /** Points du tracé renvoyés par trajet sur la carte de suivi de toute la flotte. */
    public static final int LIVE_TRACK = 40;
    /** Vitesse au-delà de laquelle la mesure est jugée aberrante (km/h) : on garde le point, pas la vitesse. */
    public static final double MAX_SPEED_KMH = 400;

    public static final String RECORDED = "RECORDED";
    public static final String TOO_SOON = "TOO_SOON";

    private TripTracking() {}

    /** Coordonnées WGS 84 valides (aucune restriction géographique). */
    public static boolean validCoordinates(Double lat, Double lng) {
        return lat != null && lng != null
            && Double.isFinite(lat) && Double.isFinite(lng)
            && lat >= -90 && lat <= 90
            && lng >= -180 && lng <= 180;
    }

    /** Vitesse en km/h exploitable, sinon null (absente, négative ou aberrante). */
    public static Double sanitizeSpeed(Double speedKmh) {
        if (speedKmh == null || !Double.isFinite(speedKmh) || speedKmh < 0 || speedKmh > MAX_SPEED_KMH) {
            return null;
        }
        return Math.round(speedKmh * 10.0) / 10.0;
    }

    /** Précision en mètres exploitable, sinon null. */
    public static Double sanitizeAccuracy(Double accuracyM) {
        if (accuracyM == null || !Double.isFinite(accuracyM) || accuracyM < 0) {
            return null;
        }
        return Math.round(accuracyM * 10.0) / 10.0;
    }

    /** Vrai si un point reçu à {@code now} arrive trop tôt après le dernier point enregistré. */
    public static boolean tooSoon(Instant last, Instant now) {
        return last != null && now != null && Duration.between(last, now).compareTo(MIN_INTERVAL) < 0;
    }

    /** Secondes à attendre avant qu'un nouveau point soit accepté. */
    public static long retryInSeconds(Instant last, Instant now) {
        if (last == null || now == null) return 0;
        long wait = MIN_INTERVAL.minus(Duration.between(last, now)).toSeconds();
        return Math.max(0, wait);
    }

    /** Un point du tracé (vitesse en km/h, peut être absente). */
    public record PositionPoint(double lat, double lng, Double speed, Instant at) {}

    /** Réponse à l'envoi d'un point : enregistré, ou ignoré car trop rapproché du précédent. */
    public record TelemetryAck(boolean accepted, String outcome, Instant positionAt, long retryInSeconds) {}

    /** Dernière position et tracé récent d'un trajet (du plus ancien au plus récent). */
    public record TripPositions(
        UUID tripId,
        String tripCode,
        String status,
        UUID vehicleId,
        String licensePlate,
        PositionPoint last,
        List<PositionPoint> track
    ) {}

    /** Trajet en cours sur la carte de suivi : dernière position (null si jamais partagée) et tracé récent. */
    public record LiveTripPosition(
        UUID tripId,
        String tripCode,
        String status,
        UUID fleetId,
        UUID vehicleId,
        String licensePlate,
        UUID driverId,
        PositionPoint last,
        List<PositionPoint> track
    ) {
        public LiveTripPosition withTrack(List<PositionPoint> points) {
            return new LiveTripPosition(tripId, tripCode, status, fleetId, vehicleId, licensePlate, driverId, last, points);
        }
    }
}
