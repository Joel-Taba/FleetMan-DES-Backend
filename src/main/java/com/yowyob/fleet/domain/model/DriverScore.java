package com.yowyob.fleet.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Entité du domaine : Score de conduite d'un chauffeur.
 * Domaine pur — aucune dépendance Spring, JPA ou Jackson.
 *
 * Un score est un instantané calculé pour un chauffeur sur une période donnée.
 * Il agrège 5 composantes pondérées en un score global sur 100 points.
 *
 * Immuable par conception : on crée un nouveau snapshot à chaque recalcul.
 */
public class DriverScore {

    // ── Enums du domaine ─────────────────────────────────────────────────────

    /** Période de calcul */
    public enum PeriodType {
        WEEKLY,
        MONTHLY
    }

    /** Badge attribué selon le score final */
    public enum ScoreBadge {
        EXCELLENCE,     // > 90 pts
        GOOD,           // 75 – 90 pts
        SATISFACTORY,   // 60 – 74 pts
        WARNING,        // 40 – 59 pts
        INSUFFICIENT    // < 40 pts
    }

    // ── Champs ───────────────────────────────────────────────────────────────

    private UUID id;

    /** Chauffeur évalué */
    private final UUID driverId;

    /** Flotte d'appartenance */
    private final UUID fleetId;

    /** Manager responsable */
    private final UUID managerId;

    /** Période de calcul */
    private final PeriodType periodType;

    /** Début de la période */
    private final LocalDate periodStart;

    /** Fin de la période */
    private final LocalDate periodEnd;

    // ── Composantes brutes ────────────────────────────────────────────────────

    /** Nombre d'incidents sur la période */
    private final int incidentCount;

    /** Nombre total de trajets (affectations COMPLETED) */
    private final int totalTrips;

    /** Consommation carburant en L/100km (null si pas de données) */
    private final BigDecimal fuelPer100Km;

    /** Consommation moyenne de la flotte en L/100km (pour comparaison) */
    private final BigDecimal fleetAvgFuelPer100Km;

    /** Taux de conformité documentaire en % (permis, visite médicale) */
    private final BigDecimal docComplianceRate;

    /** Nombre de maintenances anormales imputées au chauffeur */
    private final int abnormalMaintenanceCount;

    /** Nombre d'affectations COMPLETED (sur total des affectations COMPLETED + NO_SHOW) */
    private final int completedAssignments;

    /** Nombre d'affectations NO_SHOW */
    private final int noShowAssignments;

    // ── Scores composantes (normalisés 0-100) ─────────────────────────────────

    /** Score incidents (30%) : moins d'incidents = meilleur score */
    private final BigDecimal incidentScore;

    /** Score carburant (25%) : consommation < moyenne flotte = meilleur */
    private final BigDecimal fuelScore;

    /** Score conformité documentaire (20%) */
    private final BigDecimal complianceScore;

    /** Score ponctualité / présence (15%) : taux COMPLETED vs NO_SHOW */
    private final BigDecimal punctualityScore;

    /** Score maintenance (10%) : pas de maintenance anormale = meilleur */
    private final BigDecimal maintenanceScore;

    // ── Score final ───────────────────────────────────────────────────────────

    /** Score global pondéré (0-100) */
    private final BigDecimal finalScore;

    /** Badge attribué */
    private final ScoreBadge badge;

    /** Date de calcul */
    private final LocalDateTime calculatedAt;

    // ─────────────────────────────────────────────────────────────────────────

    public DriverScore(UUID id,
                       UUID driverId,
                       UUID fleetId,
                       UUID managerId,
                       PeriodType periodType,
                       LocalDate periodStart,
                       LocalDate periodEnd,
                       int incidentCount,
                       int totalTrips,
                       BigDecimal fuelPer100Km,
                       BigDecimal fleetAvgFuelPer100Km,
                       BigDecimal docComplianceRate,
                       int abnormalMaintenanceCount,
                       int completedAssignments,
                       int noShowAssignments,
                       BigDecimal incidentScore,
                       BigDecimal fuelScore,
                       BigDecimal complianceScore,
                       BigDecimal punctualityScore,
                       BigDecimal maintenanceScore,
                       BigDecimal finalScore,
                       ScoreBadge badge,
                       LocalDateTime calculatedAt) {

        if (driverId == null)  throw new IllegalArgumentException("Le chauffeur est obligatoire.");
        if (fleetId == null)   throw new IllegalArgumentException("La flotte est obligatoire.");
        if (managerId == null) throw new IllegalArgumentException("Le manager est obligatoire.");
        if (periodType == null) throw new IllegalArgumentException("La période est obligatoire.");

        this.id = id;
        this.driverId = driverId;
        this.fleetId = fleetId;
        this.managerId = managerId;
        this.periodType = periodType;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.incidentCount = incidentCount;
        this.totalTrips = totalTrips;
        this.fuelPer100Km = fuelPer100Km;
        this.fleetAvgFuelPer100Km = fleetAvgFuelPer100Km;
        this.docComplianceRate = docComplianceRate;
        this.abnormalMaintenanceCount = abnormalMaintenanceCount;
        this.completedAssignments = completedAssignments;
        this.noShowAssignments = noShowAssignments;
        this.incidentScore = incidentScore != null ? incidentScore : BigDecimal.ZERO;
        this.fuelScore = fuelScore != null ? fuelScore : BigDecimal.ZERO;
        this.complianceScore = complianceScore != null ? complianceScore : BigDecimal.ZERO;
        this.punctualityScore = punctualityScore != null ? punctualityScore : BigDecimal.ZERO;
        this.maintenanceScore = maintenanceScore != null ? maintenanceScore : BigDecimal.ZERO;
        this.finalScore = finalScore != null ? finalScore : BigDecimal.ZERO;
        this.badge = badge != null ? badge : resolveBadge(this.finalScore);
        this.calculatedAt = calculatedAt != null ? calculatedAt : LocalDateTime.now();
    }

    // ── Méthodes métier statiques (calcul des composantes) ────────────────────

    /**
     * Calcule le score incidents (pondération 30%).
     * Formule : max(0, 100 - incidentCount × 20)
     * → 0 incident = 100, 1 incident = 80, 5+ incidents = 0
     */
    public static BigDecimal computeIncidentScore(int incidentCount) {
        int raw = Math.max(0, 100 - incidentCount * 20);
        return BigDecimal.valueOf(raw);
    }

    /**
     * Calcule le score carburant (pondération 25%).
     * Formule : si fleetAvg == null → 75 (neutre)
     * si fuelPer100Km ≤ fleetAvg → 100
     * si fuelPer100Km > fleetAvg × 1.3 → 0
     * sinon → interpolation linéaire
     */
    public static BigDecimal computeFuelScore(BigDecimal driverFuel, BigDecimal fleetAvg) {
        if (driverFuel == null || fleetAvg == null || fleetAvg.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.valueOf(75); // Score neutre si pas de données
        }
        if (driverFuel.compareTo(fleetAvg) <= 0) return BigDecimal.valueOf(100);

        BigDecimal ceiling = fleetAvg.multiply(BigDecimal.valueOf(1.3));
        if (driverFuel.compareTo(ceiling) >= 0) return BigDecimal.ZERO;

        // Interpolation linéaire entre 100 (=fleetAvg) et 0 (=fleetAvg × 1.3)
        BigDecimal excess = driverFuel.subtract(fleetAvg);
        BigDecimal range  = ceiling.subtract(fleetAvg);
        BigDecimal penalty = excess.divide(range, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        BigDecimal score = BigDecimal.valueOf(100).subtract(penalty);
        return score.max(BigDecimal.ZERO).setScale(1, RoundingMode.HALF_UP);
    }

    /**
     * Calcule le score conformité documentaire (pondération 20%).
     * Formule directe : docComplianceRate (déjà en %)
     */
    public static BigDecimal computeComplianceScore(BigDecimal complianceRate) {
        if (complianceRate == null) return BigDecimal.valueOf(100);
        return complianceRate.max(BigDecimal.ZERO).min(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
    }

    /**
     * Calcule le score ponctualité (pondération 15%).
     * Formule : (completedAssignments / (completedAssignments + noShowAssignments)) × 100
     */
    public static BigDecimal computePunctualityScore(int completed, int noShow) {
        int total = completed + noShow;
        if (total == 0) return BigDecimal.valueOf(100); // Neutre si pas d'affectations
        return BigDecimal.valueOf(completed)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
    }

    /**
     * Calcule le score maintenance (pondération 10%).
     * Formule : max(0, 100 - abnormalMaintenanceCount × 25)
     * → 0 maintenance anormale = 100, 4+ = 0
     */
    public static BigDecimal computeMaintenanceScore(int abnormalCount) {
        int raw = Math.max(0, 100 - abnormalCount * 25);
        return BigDecimal.valueOf(raw);
    }

    /**
     * Calcule le score final pondéré à partir des 5 composantes.
     * Pondérations : incidents 30%, carburant 25%, conformité 20%, ponctualité 15%, maintenance 10%
     */
    public static BigDecimal computeFinalScore(BigDecimal incident,
                                                BigDecimal fuel,
                                                BigDecimal compliance,
                                                BigDecimal punctuality,
                                                BigDecimal maintenance) {
        BigDecimal s1 = incident    != null ? incident    : BigDecimal.ZERO;
        BigDecimal s2 = fuel        != null ? fuel        : BigDecimal.ZERO;
        BigDecimal s3 = compliance  != null ? compliance  : BigDecimal.ZERO;
        BigDecimal s4 = punctuality != null ? punctuality : BigDecimal.ZERO;
        BigDecimal s5 = maintenance != null ? maintenance : BigDecimal.ZERO;

        BigDecimal score = s1.multiply(BigDecimal.valueOf(0.30))
                .add(s2.multiply(BigDecimal.valueOf(0.25)))
                .add(s3.multiply(BigDecimal.valueOf(0.20)))
                .add(s4.multiply(BigDecimal.valueOf(0.15)))
                .add(s5.multiply(BigDecimal.valueOf(0.10)));

        return score.setScale(1, RoundingMode.HALF_UP);
    }

    /** Détermine le badge selon le score final. */
    public static ScoreBadge resolveBadge(BigDecimal score) {
        if (score == null) return ScoreBadge.INSUFFICIENT;
        double s = score.doubleValue();
        if (s > 90) return ScoreBadge.EXCELLENCE;
        if (s >= 75) return ScoreBadge.GOOD;
        if (s >= 60) return ScoreBadge.SATISFACTORY;
        if (s >= 40) return ScoreBadge.WARNING;
        return ScoreBadge.INSUFFICIENT;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public UUID getId()                            { return id; }
    public UUID getDriverId()                      { return driverId; }
    public UUID getFleetId()                       { return fleetId; }
    public UUID getManagerId()                     { return managerId; }
    public PeriodType getPeriodType()              { return periodType; }
    public LocalDate getPeriodStart()              { return periodStart; }
    public LocalDate getPeriodEnd()                { return periodEnd; }
    public int getIncidentCount()                  { return incidentCount; }
    public int getTotalTrips()                     { return totalTrips; }
    public BigDecimal getFuelPer100Km()            { return fuelPer100Km; }
    public BigDecimal getFleetAvgFuelPer100Km()    { return fleetAvgFuelPer100Km; }
    public BigDecimal getDocComplianceRate()       { return docComplianceRate; }
    public int getAbnormalMaintenanceCount()       { return abnormalMaintenanceCount; }
    public int getCompletedAssignments()           { return completedAssignments; }
    public int getNoShowAssignments()              { return noShowAssignments; }
    public BigDecimal getIncidentScore()           { return incidentScore; }
    public BigDecimal getFuelScore()               { return fuelScore; }
    public BigDecimal getComplianceScore()         { return complianceScore; }
    public BigDecimal getPunctualityScore()        { return punctualityScore; }
    public BigDecimal getMaintenanceScore()        { return maintenanceScore; }
    public BigDecimal getFinalScore()              { return finalScore; }
    public ScoreBadge getBadge()                   { return badge; }
    public LocalDateTime getCalculatedAt()         { return calculatedAt; }

    public void setId(UUID id) { this.id = id; }

    /** Retourne la liste des composantes pour affichage détaillé. */
    public List<ScoreComponentView> getComponents() {
        return List.of(
                new ScoreComponentView("Taux d'incidents",   30, incidentCount    + " incident(s)", incidentScore),
                new ScoreComponentView("Consommation carburant", 25,
                        fuelPer100Km != null ? fuelPer100Km + " L/100km" : "N/A", fuelScore),
                new ScoreComponentView("Conformité documentaire", 20,
                        docComplianceRate != null ? docComplianceRate + "%" : "N/A", complianceScore),
                new ScoreComponentView("Ponctualité",        15,
                        completedAssignments + "/" + (completedAssignments + noShowAssignments) + " missions", punctualityScore),
                new ScoreComponentView("Maintenance",        10, abnormalMaintenanceCount + " anomalie(s)", maintenanceScore)
        );
    }

    /** Vue d'une composante du score (utilisée dans la réponse REST). */
    public record ScoreComponentView(
            String label,
            int weight,
            String rawValue,
            BigDecimal score
    ) {}
}
