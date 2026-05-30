package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder; // Ajout
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import java.util.UUID;

@Table(name = "vehicle_illustration_images", schema = "fleet")
@Data 
@Builder // Ajout
@NoArgsConstructor 
@AllArgsConstructor
public class VehicleIllustrationImageEntity {
    @Id
    private UUID id;
    
    @Column("vehicle_id")
    private UUID vehicleId;
    
    @Column("image_path")
    private String imagePath;
}