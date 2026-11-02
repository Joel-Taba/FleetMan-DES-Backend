package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.mapping.Column;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Table(name = "vehicles", schema = "fleet")
@Data 
@NoArgsConstructor 
@AllArgsConstructor
public class VehicleLocalEntity implements Persistable<UUID> {
    @Id
    private UUID id;
    
    @Column("fleet_id")
    private UUID fleetId;
    
    @Column("manager_id")
    private UUID managerId;
    
    @Column("current_driver_id")
    private UUID currentDriverId;
    
    @Column("vehicle_type_id")
    private UUID vehicleTypeId;
    
    @Column("license_plate")
    private String licensePlate;
    
    private String brand;
    private String model;
    
    @Column("manufacturing_year")
    private Integer manufacturingYear;
    
    private String color;
    private String status; 
    
    @Column("photo_url")
    private String photoUrl;
    
    @Column("serial_number_photo_url")
    private String serialNumberPhotoUrl;
    
    @Column("registration_photo_url")
    private String registrationPhotoUrl;

    @Column("geofence_remote_id")
    private String geofenceRemoteId;

    @Column("kernel_resource_id")
    private UUID kernelResourceId;

    // Caractéristiques techniques (non stockées par la ressource Kernel)
    @Column("vehicle_serial_number")
    private String vehicleSerialNumber;

    @Column("fuel_type")
    private String fuelType;

    @Column("transmission_type")
    private String transmissionType;

    @Column("tank_capacity")
    private BigDecimal tankCapacity;

    @Column("total_seat_number")
    private Integer totalSeatNumber;

    @Column("average_fuel_consumption")
    private BigDecimal averageFuelConsumption;

    // Cycle de vie (écrit uniquement par VehiclePersistencePort.updateLifecycle)
    @Column("status_reason")
    private String statusReason;

    @Column("status_changed_at")
    private Instant statusChangedAt;

    @Column("retired_at")
    private LocalDate retiredAt;

    @Column("retirement_type")
    private String retirementType;

    @Column("retirement_reason")
    private String retirementReason;

    @Column("sale_price")
    private BigDecimal salePrice;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    @Column("deleted_at")
    private Instant deletedAt;

    @Transient
    private boolean isNew = false;

    @Override
    public boolean isNew() {
        return isNew || id == null;
    }

    // Setter explicite pour MapStruct et l'Adapter
    public void setNew(boolean isNew) {
        this.isNew = isNew;
    }
}