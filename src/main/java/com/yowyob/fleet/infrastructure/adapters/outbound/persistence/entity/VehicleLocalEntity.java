package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.mapping.Column;

import java.time.Instant;
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