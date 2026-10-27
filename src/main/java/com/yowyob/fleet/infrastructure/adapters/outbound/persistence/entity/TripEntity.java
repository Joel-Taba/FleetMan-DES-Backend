package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity;

import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table(name = "trips", schema = "fleet")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TripEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Override
    public UUID getId() {
        return this.id;
    }

    @Column("trip_code")
    private String tripCode;

    @Column("vehicle_id")
    private UUID vehicleId;

    @Column("driver_id")
    private UUID driverId;

    @Column("fleet_id")
    private UUID fleetId;

    @Column("vehicle_type_id")
    private UUID vehicleTypeId;

    @Column("created_by")
    private UUID createdBy;

    private String status;

    // ── Départ ──────────────────────────────────────────────────────────────
    @Column("start_date")
    private LocalDate startDate;

    @Column("start_time")
    private LocalTime startTime;

    @Column("departure_location")
    private String departureLocation;

    @Column("departure_km_index")
    private BigDecimal departureKmIndex;

    @Column("departure_fuel_index")
    private BigDecimal departureFuelIndex;

    // ── Retour ───────────────────────────────────────────────────────────────
    @Column("end_date")
    private LocalDate endDate;

    @Column("end_time")
    private LocalTime endTime;

    @Column("return_location")
    private String returnLocation;

    @Column("return_km_index")
    private BigDecimal returnKmIndex;

    @Column("return_fuel_index")
    private BigDecimal returnFuelIndex;

    @Column("return_registered_at")
    private Instant returnRegisteredAt;

    @Column("scheduled_return_datetime")
    private LocalDateTime scheduledReturnDatetime;

    // ── Mission ───────────────────────────────────────────────────────────────
    @Column("mission_object")
    private String missionObject;

    @Column("mission_cost")
    private BigDecimal missionCost;

    @Column("mission_cost_currency")
    private String missionCostCurrency;

    @Column("rate_type")
    private String rateType;

    @Column("departure_registered_at")
    private Instant departureRegisteredAt;

    @Column("departure_lat")
    private BigDecimal departureLat;

    @Column("departure_lng")
    private BigDecimal departureLng;

    @Column("return_lat")
    private BigDecimal returnLat;

    @Column("return_lng")
    private BigDecimal returnLng;

    // ── Calculés ──────────────────────────────────────────────────────────────
    @Column("distance_km")
    private Double distanceKm;

    @Column("duration_minutes")
    private Integer durationMinutes;

    @Column("computed_distance_km")
    private BigDecimal computedDistanceKm;

    @Column("computed_fuel_consumed")
    private BigDecimal computedFuelConsumed;

    // ── Annulation ────────────────────────────────────────────────────────────
    @Column("cancel_reason")
    private String cancelReason;

    @Column("cancelled_at")
    private Instant cancelledAt;

    // ── Liaison chauffeur ↔ véhicule ──────────────────────────────────────────
    // true si c'est CE trajet qui a posé la liaison à son départ : seule une
    // liaison posée par le trajet est défaite à sa clôture (une affectation
    // permanente préexistante n'est jamais effacée par un trajet).
    @Column("driver_vehicle_linked")
    private boolean driverVehicleLinked;

    // ── Interruption (incident grave immobilisant le véhicule en cours de trajet) ──
    // Le trajet reste ouvert ; il reprend quand le véhicule est remplacé ou libéré.
    @Column("interrupted_at")
    private LocalDateTime interruptedAt;

    @Column("resumed_at")
    private LocalDateTime resumedAt;

    @Column("interruption_incident_id")
    private UUID interruptionIncidentId;

    /** Interrompu et pas encore repris. */
    public boolean isInterrupted() {
        return interruptedAt != null && resumedAt == null;
    }

    // ── R2DBC Persistable ─────────────────────────────────────────────────────
    @Transient
    private boolean isNew = false;

    @Override
    public boolean isNew() {
        return isNew || id == null;
    }

    public void setNew(boolean v) {
        this.isNew = v;
    }
}
