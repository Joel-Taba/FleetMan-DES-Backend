package com.yowyob.fleet.domain.model;

import java.math.BigDecimal;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entité de domaine : Trajet (aller-retour complet).
 * Piloté exclusivement par le Fleet Manager.
 * Cycle de vie : SCHEDULED → DEPARTED → RETURNING → COMPLETED | CANCELLED
 */
public class Trip {

    public enum Status {
        SCHEDULED,
        DEPARTED,
        RETURNING,
        COMPLETED,
        CANCELLED,
    }

    public enum RateType {
        FIXED,
        PER_KM,
        HOURLY,
    }

    // ── Identité ──────────────────────────────────────────────────────────────
    private UUID id;
    private String tripCode; // TRJ-2026-0001
    private UUID vehicleId;
    private UUID driverId;
    private UUID fleetId;
    private UUID createdBy; // ID du manager qui a créé
    private Status status;

    // ── Départ ────────────────────────────────────────────────────────────────
    private LocalDate startDate;
    private LocalTime startTime;
    private String departureLocation;
    private BigDecimal departureKmIndex;
    private BigDecimal departureFuelIndex;

    // ── Retour ────────────────────────────────────────────────────────────────
    private LocalDate endDate;
    private LocalTime endTime;
    private String returnLocation;
    private BigDecimal returnKmIndex;
    private BigDecimal returnFuelIndex;
    private Instant returnRegisteredAt;
    private LocalDateTime scheduledReturnDatetime;

    // ── Mission ───────────────────────────────────────────────────────────────
    private String missionObject;
    private BigDecimal missionCost;
    private RateType rateType;

    // ── Valeurs calculées ─────────────────────────────────────────────────────
    private Double distanceKm; // ancien champ (GPS)
    private Integer durationMinutes;
    private BigDecimal computedDistanceKm; // calculé : returnKm - departureKm
    private BigDecimal computedFuelConsumed; // calculé : departureFuel - returnFuel

    // ── Annulation ────────────────────────────────────────────────────────────
    private String cancelReason;
    private Instant cancelledAt;

    // ── Détails de mission ────────────────────────────────────────────────────
    private List<TripDetail> details;

    // ── Constructeur complet ──────────────────────────────────────────────────
    public Trip(
        UUID id,
        String tripCode,
        UUID vehicleId,
        UUID driverId,
        UUID fleetId,
        UUID createdBy,
        Status status,
        LocalDate startDate,
        LocalTime startTime,
        String departureLocation,
        BigDecimal departureKmIndex,
        BigDecimal departureFuelIndex,
        LocalDate endDate,
        LocalTime endTime,
        String returnLocation,
        BigDecimal returnKmIndex,
        BigDecimal returnFuelIndex,
        Instant returnRegisteredAt,
        LocalDateTime scheduledReturnDatetime,
        String missionObject,
        BigDecimal missionCost,
        RateType rateType,
        Double distanceKm,
        Integer durationMinutes,
        BigDecimal computedDistanceKm,
        BigDecimal computedFuelConsumed,
        String cancelReason,
        Instant cancelledAt,
        List<TripDetail> details
    ) {
        this.id = id;
        this.tripCode = tripCode;
        this.vehicleId = vehicleId;
        this.driverId = driverId;
        this.fleetId = fleetId;
        this.createdBy = createdBy;
        this.status = status;
        this.startDate = startDate;
        this.startTime = startTime;
        this.departureLocation = departureLocation;
        this.departureKmIndex = departureKmIndex;
        this.departureFuelIndex = departureFuelIndex;
        this.endDate = endDate;
        this.endTime = endTime;
        this.returnLocation = returnLocation;
        this.returnKmIndex = returnKmIndex;
        this.returnFuelIndex = returnFuelIndex;
        this.returnRegisteredAt = returnRegisteredAt;
        this.scheduledReturnDatetime = scheduledReturnDatetime;
        this.missionObject = missionObject;
        this.missionCost = missionCost;
        this.rateType = rateType;
        this.distanceKm = distanceKm;
        this.durationMinutes = durationMinutes;
        this.computedDistanceKm = computedDistanceKm;
        this.computedFuelConsumed = computedFuelConsumed;
        this.cancelReason = cancelReason;
        this.cancelledAt = cancelledAt;
        this.details = details != null ? details : new ArrayList<>();
    }

    // ── Getters ───────────────────────────────────────────────────────────────
    public UUID getId() {
        return id;
    }

    public String getTripCode() {
        return tripCode;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public UUID getDriverId() {
        return driverId;
    }

    public UUID getFleetId() {
        return fleetId;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Status getStatus() {
        return status;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public String getDepartureLocation() {
        return departureLocation;
    }

    public BigDecimal getDepartureKmIndex() {
        return departureKmIndex;
    }

    public BigDecimal getDepartureFuelIndex() {
        return departureFuelIndex;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public String getReturnLocation() {
        return returnLocation;
    }

    public BigDecimal getReturnKmIndex() {
        return returnKmIndex;
    }

    public BigDecimal getReturnFuelIndex() {
        return returnFuelIndex;
    }

    public Instant getReturnRegisteredAt() {
        return returnRegisteredAt;
    }

    public LocalDateTime getScheduledReturnDatetime() {
        return scheduledReturnDatetime;
    }

    public String getMissionObject() {
        return missionObject;
    }

    public BigDecimal getMissionCost() {
        return missionCost;
    }

    public RateType getRateType() {
        return rateType;
    }

    public Double getDistanceKm() {
        return distanceKm;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public BigDecimal getComputedDistanceKm() {
        return computedDistanceKm;
    }

    public BigDecimal getComputedFuelConsumed() {
        return computedFuelConsumed;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public List<TripDetail> getDetails() {
        return details;
    }

    // ── Setters (mutations métier) ────────────────────────────────────────────
    public void setId(UUID id) {
        this.id = id;
    }

    public void setTripCode(String tripCode) {
        this.tripCode = tripCode;
    }

    public void setDriverId(UUID driverId) {
        this.driverId = driverId;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public void setReturnLocation(String returnLocation) {
        this.returnLocation = returnLocation;
    }

    public void setReturnKmIndex(BigDecimal returnKmIndex) {
        this.returnKmIndex = returnKmIndex;
    }

    public void setReturnFuelIndex(BigDecimal returnFuelIndex) {
        this.returnFuelIndex = returnFuelIndex;
    }

    public void setReturnRegisteredAt(Instant returnRegisteredAt) {
        this.returnRegisteredAt = returnRegisteredAt;
    }

    public void setComputedDistanceKm(BigDecimal computedDistanceKm) {
        this.computedDistanceKm = computedDistanceKm;
    }

    public void setComputedFuelConsumed(BigDecimal computedFuelConsumed) {
        this.computedFuelConsumed = computedFuelConsumed;
    }

    public void setDistanceKm(Double distanceKm) {
        this.distanceKm = distanceKm;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public void setDetails(List<TripDetail> details) {
        this.details = details;
    }
}
