package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.mapping.Column;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.UUID;

@Table(name = "operational_parameters", schema = "fleet")
@Data @NoArgsConstructor @AllArgsConstructor
public class OperationalParameterEntity {
    @Id
    private UUID id;
    
    @Column("vehicle_id")
    private UUID vehicleId;
    
    @Column("trip_id")
    private UUID tripId;
    
    private Boolean statut;
    
    @Column("current_location")
    private String currentLocation;
    
    @Column("current_speed")
    private BigDecimal currentSpeed;
    
    @Column("fuel_level")
    private String fuelLevel;
    
    private BigDecimal mileage;
    
    @Column("odometer_reading")
    private BigDecimal odometerReading;
    
    private BigDecimal bearing;
    
    private Instant timestamp;
}